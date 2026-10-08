package com.ledgerai.app.data.local.room

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.util.RecurrenceJson

fun CalendarEventEntity.toDomainWithRecurrence() = CalendarEvent(
    id = id,
    remoteId = remoteId,
    title = title,
    courseId = courseId,
    taskId = taskId,
    location = location,
    startAt = startAt,
    endAt = endAt,
    kind = kind,
    recurrence = EventRecurrence(
        frequency = recurrenceFrequency,
        interval = recurrenceInterval.coerceAtLeast(1),
        weekDays = RecurrenceJson.decodeWeekDays(recurrenceWeekdays),
        specificDates = RecurrenceJson.decodeSpecificDates(specificDatesJson),
        until = recurrenceUntil,
        excludedDates = RecurrenceJson.decodeDates(excludedDatesJson)
    )
)

fun CalendarEvent.toEntity(
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
): CalendarEventEntity {
    val r = recurrence ?: EventRecurrence()
    return CalendarEventEntity(
        id = id,
        remoteId = remoteId,
        userId = userId,
        title = title,
        courseId = courseId,
        taskId = taskId,
        startAt = startAt,
        endAt = endAt,
        kind = kind,
        location = location,
        recurrenceFrequency = r.frequency,
        recurrenceInterval = r.interval.coerceAtLeast(1),
        recurrenceWeekdays = RecurrenceJson.encodeWeekDays(r.weekDays),
        specificDatesJson = when (r.frequency) {
            RecurrenceFrequency.SPECIFIC_DATES -> RecurrenceJson.encodeSpecificDates(r.specificDates)
            else -> ""
        },
        recurrenceUntil = r.until,
        excludedDatesJson = RecurrenceJson.encodeDates(r.excludedDates),
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )
}
