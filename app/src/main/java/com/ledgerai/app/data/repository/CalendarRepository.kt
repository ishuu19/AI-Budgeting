package com.ledgerai.app.data.repository

import androidx.room.withTransaction
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.EventReminderDao
import com.ledgerai.app.data.local.room.LedgerDatabase
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.data.schedule.ParsedScheduleRow
import com.ledgerai.app.data.schedule.RecurrenceExpander
import com.ledgerai.app.data.schedule.nextOccurrence
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.schedule.AlarmDays
import com.ledgerai.app.domain.schedule.EventReminderRules
import com.ledgerai.app.domain.schedule.defaultEventReminders
import com.ledgerai.app.service.AlarmScheduler
import com.ledgerai.app.service.AlarmTriggerCalc
import com.ledgerai.app.worker.EventReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single store for events, tasks, exams, classes, routines and alarms.
 * Reminders (max [MAX_REMINDERS_PER_EVENT]) and alarm/reminder scheduling are handled here.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val db: LedgerDatabase,
    private val dao: CalendarEventDao,
    private val reminderDao: EventReminderDao,
    private val reminderScheduler: EventReminderScheduler,
    private val alarmScheduler: AlarmScheduler,
    private val leaveByRepository: LeaveByRepository
) {

    // --- observe -----------------------------------------------------------------------------

    /** Occurrences (recurring events expanded) between [from] and [to], both inclusive. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        dao.observeForRange(
            from.atStartOfDay().toString(),
            to.plusDays(1).atStartOfDay().toString(),
            from.toString()
        ).map { rows -> expandRows(rows.map { it.toDomain() }, from, to) }

    fun observeMonth(month: LocalDate): Flow<List<CalendarEvent>> =
        observeRange(month.withDayOfMonth(1), month.withDayOfMonth(month.lengthOfMonth()))

    /** Task masters: incomplete first, dated before undated, then by due time. */
    fun observeTasks(): Flow<List<CalendarEvent>> =
        dao.observeByKind(CalendarEventKind.TASK.name).map { rows ->
            rows.map { it.toDomain() }.sortedWith(
                compareBy<CalendarEvent> { it.isCompleted }
                    .thenBy { !it.hasDate }
                    .thenBy { it.startAt }
            )
        }

    /** Alarm masters ordered by time of day. */
    fun observeAlarms(): Flow<List<CalendarEvent>> =
        dao.observeByKind(CalendarEventKind.ALARM.name).map { rows ->
            rows.map { it.toDomain() }.sortedBy { it.startAt.toLocalTime() }
        }

    /** Every master event, not expanded (search, AI context). */
    fun observeAll(): Flow<List<CalendarEvent>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun listRange(from: LocalDate, to: LocalDate): List<CalendarEvent> =
        expandRows(
            dao.listForRange(
                from.atStartOfDay().toString(),
                to.plusDays(1).atStartOfDay().toString(),
                from.toString()
            ).map { it.toDomain() },
            from,
            to
        )

    suspend fun listNextDays(days: Int): List<CalendarEvent> {
        val from = LocalDate.now()
        return listRange(from, from.plusDays(days.toLong()))
    }

    suspend fun getById(id: Long): CalendarEvent? =
        if (id <= 0L) null else dao.getWithReminders(id)?.toDomain()

    // --- write -------------------------------------------------------------------------------

    /**
     * Inserts or replaces [event] with its reminders and (re)arms reminders and alarms.
     * When [withDefaultReminders] is set and a new event has none, the standard offsets are added.
     */
    suspend fun upsert(event: CalendarEvent, withDefaultReminders: Boolean = false): Long {
        val prepared = normalize(event)
        val now = System.currentTimeMillis()
        val id = db.withTransaction {
            val existing = if (prepared.id > 0L) dao.getByIdAny(prepared.id) else null
            val entity = prepared.toEntity(
                userId = existing?.userId,
                updatedAt = now,
                deletedAt = null
            )
            val savedId = if (prepared.id <= 0L) {
                dao.insert(entity.copy(id = 0))
            } else {
                dao.insert(entity)
                prepared.id
            }
            val desired = when {
                !EventReminderRules.supportsReminders(prepared.kind) -> emptyList()
                prepared.id <= 0L && withDefaultReminders && prepared.reminders.isEmpty() ->
                    defaultEventReminders()
                else -> prepared.reminders
            }
            replaceReminders(savedId, desired, existing?.userId, now)
            savedId
        }
        rearm(id)
        return id
    }

    suspend fun deleteById(eventId: Long) {
        if (eventId <= 0L) return
        val now = System.currentTimeMillis()
        val reminderIds = reminderDao.listForEvent(eventId).map { it.id }
        db.withTransaction {
            reminderDao.softDeleteForEvent(eventId, deletedAt = now, updatedAt = now)
            dao.softDelete(eventId, deletedAt = now, updatedAt = now)
        }
        reminderScheduler.cancelAll(reminderIds)
        alarmScheduler.cancel(eventId)
        leaveByRepository.removeForRef(LeaveRefType.CALENDAR_EVENT, eventId)
    }

    suspend fun deleteRecurring(
        masterId: Long,
        instanceDate: LocalDate?,
        scope: RecurrenceDeleteScope
    ) {
        val master = getById(masterId) ?: return
        val recurrence = master.recurrence ?: EventRecurrence()
        val startDate = master.startAt.toLocalDate()
        when (scope) {
            RecurrenceDeleteScope.ALL -> deleteById(masterId)
            RecurrenceDeleteScope.THIS -> {
                val date = instanceDate ?: startDate
                if (!recurrence.repeats) {
                    deleteById(masterId)
                } else {
                    upsert(master.copy(recurrence = recurrence.copy(excludedDates = recurrence.excludedDates + date)))
                }
            }
            RecurrenceDeleteScope.THIS_AND_FUTURE -> {
                val date = instanceDate ?: startDate
                if (!recurrence.repeats || !date.isAfter(startDate)) {
                    deleteById(masterId)
                } else {
                    upsert(master.copy(recurrence = recurrence.copy(until = date.minusDays(1))))
                }
            }
        }
    }

    /**
     * Completes or reopens a task. Completing one occurrence of a repeating task skips just that
     * day ([instanceDate]); non-repeating tasks are flagged completed.
     */
    suspend fun setCompleted(id: Long, completed: Boolean, instanceDate: LocalDate? = null) {
        val event = getById(id) ?: return
        val recurrence = event.recurrence
        if (completed && recurrence != null && recurrence.repeats && instanceDate != null) {
            upsert(event.copy(recurrence = recurrence.copy(excludedDates = recurrence.excludedDates + instanceDate)))
            return
        }
        upsert(
            event.copy(
                isCompleted = completed,
                completedAt = if (completed) LocalDateTime.now() else null
            )
        )
        if (completed) leaveByRepository.removeForRef(LeaveRefType.CALENDAR_EVENT, id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val event = getById(id) ?: return
        upsert(event.copy(isEnabled = enabled))
    }

    /** Moves one occurrence or a non-repeating event to [newDate] (keeps times and reminders). */
    suspend fun rescheduleInstance(event: CalendarEvent, newDate: LocalDate) {
        val oldDate = event.instanceDate ?: event.startAt.toLocalDate()
        if (newDate == oldDate) return
        val masterId = event.masterId.takeIf { it > 0 } ?: return
        val master = getById(masterId) ?: return
        val r = master.recurrence ?: EventRecurrence()
        val newStart = LocalDateTime.of(newDate, master.startAt.toLocalTime())
        var newEnd = LocalDateTime.of(newDate, master.endAt.toLocalTime())
        if (newEnd.isBefore(newStart)) newEnd = newEnd.plusDays(1)

        if (!r.repeats) {
            upsert(master.copy(startAt = newStart, endAt = newEnd, hasDate = true))
            return
        }
        when (r.frequency) {
            RecurrenceFrequency.SPECIFIC_DATES ->
                upsert(master.copy(recurrence = r.copy(specificDates = r.specificDates - oldDate + newDate)))
            else -> {
                upsert(master.copy(recurrence = r.copy(excludedDates = r.excludedDates + oldDate)))
                upsert(
                    master.copy(
                        id = 0,
                        remoteId = null,
                        startAt = newStart,
                        endAt = newEnd,
                        recurrence = EventRecurrence(),
                        reminders = master.reminders.map { it.copy(id = 0, remoteId = null) }
                    )
                )
            }
        }
    }

    // --- reminders ---------------------------------------------------------------------------

    /** Adds a reminder; false when the event already has [MAX_REMINDERS_PER_EVENT]. */
    suspend fun addReminder(eventId: Long, label: String, offsetMinutes: Int): Boolean {
        val event = getById(eventId) ?: return false
        if (!event.canAddReminder || !EventReminderRules.supportsReminders(event.kind)) return false
        if (event.reminders.any { it.offsetMinutes == offsetMinutes }) return true
        upsert(event.copy(reminders = event.reminders + EventReminder(label = label, offsetMinutes = offsetMinutes)))
        return true
    }

    suspend fun removeReminder(eventId: Long, reminderId: Long) {
        val event = getById(eventId) ?: return
        upsert(event.copy(reminders = event.reminders.filterNot { it.id == reminderId }))
    }

    // --- scheduling --------------------------------------------------------------------------

    /** Arms reminders and the alarm for one event (also used after a sync pull). */
    suspend fun rearm(eventId: Long) {
        val event = getById(eventId) ?: return
        reminderScheduler.scheduleEvent(event)
        if (event.kind == CalendarEventKind.ALARM) {
            if (event.isEnabled) alarmScheduler.schedule(event) else alarmScheduler.cancel(event.id)
        }
    }

    /**
     * After a sync pull: arms live events, and cancels anything scheduled for events that were
     * deleted remotely. The reminder cap is enforced while pulling (SyncRepository).
     */
    suspend fun rearmAfterPull(eventId: Long) {
        val event = getById(eventId)
        if (event == null) {
            reminderScheduler.cancelAll(reminderDao.allIdsForEvent(eventId))
            alarmScheduler.cancel(eventId)
            return
        }
        rearm(eventId)
    }

    suspend fun rescheduleAllAlarms(): Int {
        val alarms = dao.listEnabledAlarms().map { it.toDomain() }
        alarms.forEach { alarmScheduler.schedule(it) }
        return alarms.size
    }

    suspend fun rescheduleAllReminders(): Int = reminderScheduler.rescheduleAll()

    // --- timetable import --------------------------------------------------------------------

    /** Writes each timetable row as a weekly recurring CLASS event. Returns rows written. */
    suspend fun importClasses(rows: List<ParsedScheduleRow>, replace: Boolean): Int {
        if (replace) {
            dao.listByKind(CalendarEventKind.CLASS.name)
                .filter { it.recurrenceFrequency == RecurrenceFrequency.WEEKLY }
                .forEach { deleteById(it.id) }
        }
        var written = 0
        for (row in rows) {
            val anchor = nextOccurrence(row.dayOfWeek, row.startTime).toLocalDate().minusWeeks(4)
            val start = LocalDateTime.of(anchor, row.startTime)
            var end = LocalDateTime.of(anchor, row.endTime)
            if (!end.isAfter(start)) end = start.plusHours(1)
            upsert(
                CalendarEvent(
                    title = row.title,
                    notes = row.courseCode,
                    location = row.location,
                    startAt = start,
                    endAt = end,
                    kind = CalendarEventKind.CLASS,
                    recurrence = EventRecurrence(
                        frequency = RecurrenceFrequency.WEEKLY,
                        weekDays = setOf(row.dayOfWeek.coerceIn(1, 7))
                    )
                ),
                withDefaultReminders = true
            )
            written++
        }
        return written
    }

    // --- internals ---------------------------------------------------------------------------

    private suspend fun replaceReminders(
        eventId: Long,
        desired: List<EventReminder>,
        userId: String?,
        now: Long
    ) {
        val capped = EventReminderRules.cap(desired)
        val existing = reminderDao.listForEvent(eventId).associateBy { it.id }
        val keep = capped.mapNotNull { r -> r.id.takeIf { it > 0L && it in existing } }.toSet()
        existing.keys.filterNot { it in keep }.forEach { reminderScheduler.cancel(it) }
        existing.keys.filterNot { it in keep }.forEach { reminderDao.softDelete(it, now, now) }
        capped.forEach { r ->
            val old = existing[r.id]
            if (old != null) {
                reminderDao.update(
                    r.copy(remoteId = r.remoteId ?: old.remoteId)
                        .toEntity(eventId = eventId, userId = old.userId ?: userId, updatedAt = now)
                )
            } else {
                reminderDao.insert(r.toEntity(eventId = eventId, userId = userId, updatedAt = now).copy(id = 0))
            }
        }
    }

    /** Alarm events derive their recurrence and next date from time and repeat mask. */
    private fun normalize(event: CalendarEvent): CalendarEvent {
        if (event.kind != CalendarEventKind.ALARM) {
            return event.copy(
                endAt = if (event.endAt.isBefore(event.startAt)) event.startAt else event.endAt
            )
        }
        val trigger = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(
                AlarmTriggerCalc.nextTriggerMillis(event.startAt.toLocalTime(), event.alarmRepeatDays)
            ),
            ZoneId.systemDefault()
        )
        val repeat = event.alarmRepeatDays
        return event.copy(
            startAt = trigger,
            endAt = trigger,
            hasDate = true,
            allDay = false,
            recurrence = if (repeat == 0) {
                EventRecurrence()
            } else {
                EventRecurrence(
                    frequency = RecurrenceFrequency.WEEKLY,
                    weekDays = AlarmDays.toIsoDays(repeat)
                )
            },
            reminders = emptyList()
        )
    }

    private fun expandRows(masters: List<CalendarEvent>, from: LocalDate, to: LocalDate): List<CalendarEvent> =
        masters.flatMap { RecurrenceExpander.expand(it, from, to) }.sortedBy { it.startAt }
}
