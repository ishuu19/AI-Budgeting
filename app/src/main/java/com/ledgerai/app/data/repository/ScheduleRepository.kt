package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.RoutineSlotReminderDao
import com.ledgerai.app.data.local.room.ScheduleSlotDao
import com.ledgerai.app.data.local.room.ScheduleSlotExceptionEntity
import com.ledgerai.app.data.local.room.ScheduleSlotExceptionDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.schedule.ParsedScheduleRow
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.data.schedule.nextOccurrence
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_ROUTINE_SLOT
import com.ledgerai.app.domain.schedule.DEFAULT_BEFORE_EVENT_MINUTES
import com.ledgerai.app.domain.schedule.labelForMinutesBefore
import com.ledgerai.app.domain.model.RoutineSlotReminder
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.model.ScheduleSlot
import com.ledgerai.app.worker.RoutineSlotReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val slotDao: ScheduleSlotDao,
    private val exceptionDao: ScheduleSlotExceptionDao,
    private val reminderDao: RoutineSlotReminderDao,
    private val reminderScheduler: RoutineSlotReminderScheduler,
    private val calendarRepository: CalendarRepository
) {

    fun observeSlots(routineId: Long): Flow<List<ScheduleSlot>> =
        slotDao.observeWithReminders(routineId).map { list -> list.map { it.toDomain() } }

    fun observeAllSlots(): Flow<List<ScheduleSlot>> =
        combine(slotDao.observeAll(), exceptionDao.observeAll()) { slots, exceptions ->
            val skipped = exceptions.groupBy { it.slotId }
                .mapValues { e -> e.value.map { it.exceptionDate }.toSet() }
            slots.map { entity ->
                entity.toDomain(skippedDates = skipped[entity.id] ?: emptySet())
            }
        }

    suspend fun importRows(routineId: Long, rows: List<ParsedScheduleRow>, replace: Boolean) {
        val now = System.currentTimeMillis()
        if (replace) {
            clearRoutineSlots(routineId, now)
        }
        for (row in rows) {
            val slotId = slotDao.insert(
                ScheduleSlot(
                    routineId = routineId,
                    courseId = null,
                    title = row.title,
                    dayOfWeek = row.dayOfWeek,
                    startTime = row.startTime,
                    endTime = row.endTime,
                    location = row.location
                ).toEntity(updatedAt = now, deletedAt = null).copy(id = 0)
            )
            seedDefaultSlotReminders(slotId)
        }
    }

    suspend fun saveSlot(slot: ScheduleSlot): Long {
        val capped = slot.copy(reminders = slot.reminders.take(MAX_REMINDERS_PER_ROUTINE_SLOT))
        val now = System.currentTimeMillis()
        val slotId = if (capped.id == 0L) {
            slotDao.insert(capped.toEntity(updatedAt = now, deletedAt = null).copy(id = 0))
        } else {
            slotDao.insert(capped.toEntity(updatedAt = now, deletedAt = null))
            capped.id
        }
        if (capped.reminders.isEmpty()) {
            seedDefaultSlotReminders(slotId)
        } else {
            replaceReminders(slotId, capped.reminders)
        }
        return slotId
    }

    suspend fun getSlot(slotId: Long): ScheduleSlot? {
        val row = slotDao.getWithReminders(slotId) ?: return null
        val skipped = exceptionDao.listForSlot(slotId).map { it.exceptionDate }.toSet()
        return row.toDomain(skipped)
    }

    suspend fun deleteSlotOccurrence(
        slotId: Long,
        instanceDate: LocalDate,
        scope: RecurrenceDeleteScope
    ) {
        when (scope) {
            RecurrenceDeleteScope.THIS -> {
                exceptionDao.insert(ScheduleSlotExceptionEntity(slotId, instanceDate))
            }
            RecurrenceDeleteScope.THIS_AND_FUTURE -> {
                val entity = slotDao.getById(slotId) ?: return
                slotDao.update(
                    entity.copy(
                        recurrenceUntil = instanceDate.minusDays(1),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            RecurrenceDeleteScope.ALL -> {
                getSlot(slotId)?.let { deleteSlot(it) }
            }
        }
    }

    suspend fun updateSlotLocation(slotId: Long, location: String, title: String? = null) {
        val entity = slotDao.getById(slotId) ?: return
        slotDao.update(
            entity.copy(
                location = location.trim(),
                title = title?.trim()?.takeIf { it.isNotBlank() } ?: entity.title,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSlotDayOfWeek(slotId: Long, dayOfWeek: Int) {
        val entity = slotDao.getById(slotId) ?: return
        slotDao.update(
            entity.copy(
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Skip [fromDate] and add a one-off class block on [toDate]. */
    suspend fun moveClassOccurrence(slotId: Long, fromDate: LocalDate, toDate: LocalDate) {
        if (fromDate == toDate) return
        val slot = getSlot(slotId) ?: return
        exceptionDao.insert(ScheduleSlotExceptionEntity(slotId, fromDate))
        val start = LocalDateTime.of(toDate, slot.startTime)
        var end = LocalDateTime.of(toDate, slot.endTime)
        if (!end.isAfter(start)) end = end.plusDays(1)
        calendarRepository.upsert(
            CalendarEvent(
                title = slot.title,
                location = slot.location,
                startAt = start,
                endAt = end,
                kind = CalendarEventKind.CLASS,
                recurrence = EventRecurrence(frequency = RecurrenceFrequency.NONE)
            )
        )
    }

    suspend fun replaceSlotReminders(slotId: Long, reminders: List<RoutineSlotReminder>) {
        replaceReminders(slotId, reminders)
    }

    suspend fun deleteSlot(slot: ScheduleSlot) {
        if (slot.id == 0L) return
        val now = System.currentTimeMillis()
        cancelRemindersForSlot(slot.id, now)
        slotDao.softDelete(slot.id, deletedAt = now, updatedAt = now)
    }

    suspend fun addSlotReminder(
        slotId: Long,
        label: String,
        remindAt: LocalDateTime,
        offsetMinutes: Int? = null
    ): Boolean {
        val slot = slotDao.getById(slotId) ?: return false
        if (reminderDao.countActiveForSlot(slotId) >= MAX_REMINDERS_PER_ROUTINE_SLOT) return false
        val id = reminderDao.insert(
            RoutineSlotReminder(
                label = label,
                remindAt = remindAt,
                offsetMinutes = offsetMinutes
            ).toEntity(slotId = slotId, userId = slot.userId).copy(id = 0)
        )
        val entity = reminderDao.getById(id) ?: return false
        reminderScheduler.schedule(entity, slot.title)
        return true
    }

    suspend fun removeSlotReminder(slotId: Long, reminderId: Long) {
        slotDao.getById(slotId) ?: return
        reminderScheduler.cancel(reminderId)
        val now = System.currentTimeMillis()
        reminderDao.softDelete(reminderId, deletedAt = now, updatedAt = now)
    }

    suspend fun addBeforeClassReminder(slotId: Long, minutesBefore: Long) {
        val slot = slotDao.getById(slotId) ?: return
        val at = nextOccurrence(slot.dayOfWeek, slot.startTime).minusMinutes(minutesBefore)
        addSlotReminder(
            slotId = slotId,
            label = labelForMinutesBefore(minutesBefore),
            remindAt = at,
            offsetMinutes = minutesBefore.toInt().takeIf { minutesBefore > 0 }
        )
    }

    private suspend fun seedDefaultSlotReminders(slotId: Long) {
        DEFAULT_BEFORE_EVENT_MINUTES.forEach { minutes ->
            if (reminderDao.countActiveForSlot(slotId) >= MAX_REMINDERS_PER_ROUTINE_SLOT) return
            addBeforeClassReminder(slotId, minutes)
        }
    }

    private suspend fun clearRoutineSlots(routineId: Long, now: Long) {
        slotDao.listActiveIds(routineId).forEach { cancelRemindersForSlot(it, now) }
        slotDao.softDeleteForRoutine(routineId, deletedAt = now, updatedAt = now)
    }

    private suspend fun cancelRemindersForSlot(slotId: Long, now: Long) {
        reminderDao.listActiveForSlot(slotId).forEach { reminderScheduler.cancel(it.id) }
        reminderDao.softDeleteForSlot(slotId, deletedAt = now, updatedAt = now)
    }

    private suspend fun replaceReminders(slotId: Long, reminders: List<RoutineSlotReminder>) {
        val slot = slotDao.getById(slotId) ?: return
        val now = System.currentTimeMillis()
        cancelRemindersForSlot(slotId, now)
        reminders.take(MAX_REMINDERS_PER_ROUTINE_SLOT).forEach { reminder ->
            val id = reminderDao.insert(
                reminder.toEntity(slotId = slotId, userId = slot.userId).copy(id = 0)
            )
            val entity = reminderDao.getById(id) ?: return@forEach
            reminderScheduler.schedule(entity, slot.title)
        }
    }
}
