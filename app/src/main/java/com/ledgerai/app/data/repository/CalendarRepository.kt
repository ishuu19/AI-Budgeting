package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.toDomainWithRecurrence
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.data.schedule.RecurrenceExpander
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.TaskEventKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val dao: CalendarEventDao
) {

    fun observeMonth(month: LocalDate): Flow<List<CalendarEvent>> {
        val start = month.withDayOfMonth(1).atStartOfDay()
        val end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay()
        val fromDay = month.withDayOfMonth(1)
        return dao.observeForMonth(start.toString(), end.toString(), fromDay.toString())
            .map { list ->
                val rangeStart = fromDay
                val rangeEnd = month.withDayOfMonth(month.lengthOfMonth())
                list.flatMap { entity ->
                    val master = entity.toDomainWithRecurrence()
                    RecurrenceExpander.expand(master, rangeStart, rangeEnd)
                }.sortedBy { it.startAt }
            }
    }

    fun observeRange(from: LocalDateTime, to: LocalDateTime): Flow<List<CalendarEvent>> =
        observeMonth(from.toLocalDate())

    suspend fun listNextDays(days: Int): List<CalendarEvent> {
        val from = LocalDate.now()
        val to = from.plusDays(days.toLong())
        val masters = dao.listBetween(
            from.atStartOfDay().toString(),
            to.plusDays(1).atStartOfDay().toString()
        )
        return masters.flatMap { entity ->
            val master = entity.toDomainWithRecurrence()
            RecurrenceExpander.expand(master, from, to)
        }.sortedBy { it.startAt }
    }

    suspend fun getById(id: Long): CalendarEvent? =
        dao.getById(id)?.toDomainWithRecurrence()

    suspend fun upsert(event: CalendarEvent): Long {
        val now = System.currentTimeMillis()
        return if (event.id == 0L) {
            dao.insert(event.toEntity(updatedAt = now, deletedAt = null).copy(id = 0))
        } else {
            dao.insert(event.toEntity(updatedAt = now, deletedAt = null))
            event.id
        }
    }

    suspend fun deleteRecurring(
        masterId: Long,
        instanceDate: LocalDate?,
        scope: RecurrenceDeleteScope
    ) {
        val entity = dao.getById(masterId) ?: return
        val master = entity.toDomainWithRecurrence()
        val recurrence = master.recurrence ?: EventRecurrence()
        val now = System.currentTimeMillis()

        when (scope) {
            RecurrenceDeleteScope.ALL -> {
                dao.softDelete(masterId, deletedAt = now, updatedAt = now)
            }
            RecurrenceDeleteScope.THIS -> {
                val date = instanceDate ?: master.startAt.toLocalDate()
                if (!recurrence.repeats) {
                    dao.softDelete(masterId, deletedAt = now, updatedAt = now)
                } else {
                    val excluded = recurrence.excludedDates + date
                    val updated = master.copy(
                        recurrence = recurrence.copy(excludedDates = excluded)
                    )
                    dao.insert(updated.toEntity(updatedAt = now, deletedAt = null).copy(id = masterId))
                }
            }
            RecurrenceDeleteScope.THIS_AND_FUTURE -> {
                val date = instanceDate ?: master.startAt.toLocalDate()
                if (!recurrence.repeats) {
                    dao.softDelete(masterId, deletedAt = now, updatedAt = now)
                } else {
                    val until = date.minusDays(1)
                    val updated = master.copy(
                        recurrence = recurrence.copy(until = until)
                    )
                    dao.insert(updated.toEntity(updatedAt = now, deletedAt = null).copy(id = masterId))
                }
            }
        }
    }

    suspend fun syncFromTask(
        taskId: Long,
        title: String,
        courseId: Long?,
        startAt: LocalDateTime?,
        eventKind: TaskEventKind,
        location: String = ""
    ) {
        if (eventKind == TaskEventKind.TASK || startAt == null) {
            removeForTask(taskId)
            return
        }
        val endAt = startAt.plusHours(1)
        val existing = dao.getByTaskId(taskId)
        val calKind = when (eventKind) {
            TaskEventKind.EXAM -> CalendarEventKind.EXAM
            TaskEventKind.EVENT -> CalendarEventKind.PERSONAL
            TaskEventKind.TASK -> CalendarEventKind.PERSONAL
        }
        val event = CalendarEvent(
            id = existing?.id ?: 0L,
            title = title,
            courseId = courseId,
            taskId = taskId,
            location = location,
            startAt = startAt,
            endAt = endAt,
            kind = calKind,
            recurrence = EventRecurrence(frequency = RecurrenceFrequency.NONE)
        )
        upsert(event)
    }

    suspend fun removeForTask(taskId: Long) {
        val now = System.currentTimeMillis()
        dao.softDeleteForTask(taskId, deletedAt = now, updatedAt = now)
    }

    suspend fun deleteById(eventId: Long) {
        if (eventId <= 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(eventId, deletedAt = now, updatedAt = now)
    }

    /** Moves one occurrence or a non-repeating event to [newDate] (keeps times). */
    suspend fun rescheduleInstance(event: CalendarEvent, newDate: LocalDate) {
        val oldDate = event.instanceDate ?: event.startAt.toLocalDate()
        if (newDate == oldDate) return
        val masterId = event.seriesEventId ?: event.id.takeIf { it > 0 } ?: return
        val master = getById(masterId) ?: return
        val r = master.recurrence ?: EventRecurrence()
        val startTime = master.startAt.toLocalTime()
        val endTime = master.endAt.toLocalTime()
        val newStart = LocalDateTime.of(newDate, startTime)
        var newEnd = LocalDateTime.of(newDate, endTime)
        if (!newEnd.isAfter(newStart)) newEnd = newEnd.plusDays(1)

        if (!r.repeats) {
            upsert(master.copy(startAt = newStart, endAt = newEnd))
            return
        }
        when (r.frequency) {
            RecurrenceFrequency.SPECIFIC_DATES -> {
                val dates = r.specificDates - oldDate + newDate
                upsert(master.copy(recurrence = r.copy(specificDates = dates)))
            }
            else -> {
                val excluded = r.excludedDates + oldDate
                upsert(master.copy(recurrence = r.copy(excludedDates = excluded)))
                upsert(
                    CalendarEvent(
                        title = master.title,
                        location = master.location,
                        startAt = newStart,
                        endAt = newEnd,
                        kind = master.kind,
                        recurrence = EventRecurrence(frequency = RecurrenceFrequency.NONE)
                    )
                )
            }
        }
    }
}
