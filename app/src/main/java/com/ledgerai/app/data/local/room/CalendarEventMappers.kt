package com.ledgerai.app.data.local.room

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.util.RecurrenceJson

fun CalendarEventEntity.toDomain(reminders: List<EventReminder> = emptyList()) = CalendarEvent(
    id = id,
    remoteId = remoteId,
    title = title,
    notes = notes,
    location = location,
    links = links,
    startAt = startAt,
    endAt = endAt,
    allDay = allDay,
    hasDate = hasDate,
    kind = kind,
    isCompleted = isCompleted,
    completedAt = completedAt,
    isEnabled = isEnabled,
    alarmToneUri = alarmToneUri,
    alarmRepeatDays = alarmRepeatDays,
    recurrence = EventRecurrence(
        frequency = recurrenceFrequency,
        interval = recurrenceInterval.coerceAtLeast(1),
        weekDays = RecurrenceJson.decodeWeekDays(recurrenceWeekdays),
        specificDates = RecurrenceJson.decodeSpecificDates(specificDatesJson),
        until = recurrenceUntil,
        excludedDates = RecurrenceJson.decodeDates(excludedDatesJson)
    ),
    reminders = reminders
)

fun EventWithReminders.toDomain() = event.toDomain(
    reminders = reminders
        .asSequence()
        .filter { it.deletedAt == null }
        .sortedBy { it.id }
        .take(MAX_REMINDERS_PER_EVENT)
        .map { it.toDomain() }
        .toList()
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
        notes = notes,
        location = location,
        links = links,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        hasDate = hasDate,
        kind = kind,
        isCompleted = isCompleted,
        completedAt = completedAt,
        isEnabled = isEnabled,
        alarmToneUri = alarmToneUri,
        alarmRepeatDays = alarmRepeatDays,
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

fun EventReminderEntity.toDomain() = EventReminder(
    id = id,
    remoteId = remoteId,
    label = label,
    offsetMinutes = offsetMinutes,
    remindAt = remindAt,
    isEnabled = isEnabled
)

fun EventReminder.toEntity(
    eventId: Long,
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
) = EventReminderEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    eventId = eventId,
    label = label,
    offsetMinutes = offsetMinutes,
    remindAt = remindAt,
    isEnabled = isEnabled,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
