package com.ledgerai.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Hard limit: at most this many reminders may be attached to one calendar event. */
const val MAX_REMINDERS_PER_EVENT = 10

/** PERSONAL is the pre-merge name for a plain event and is treated exactly like [EVENT]. */
enum class CalendarEventKind {
    EVENT, TASK, EXAM, CLASS, ROUTINE, ALARM, PERSONAL;

    val isPlainEvent: Boolean get() = this == EVENT || this == PERSONAL

    /** Kinds that occupy time on the calendar (used for free-time search). */
    val blocksTime: Boolean get() = this != TASK && this != ALARM

    companion object {
        fun parse(raw: String?): CalendarEventKind =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EVENT
    }
}

/**
 * Reminder on an event. [offsetMinutes] is relative to every occurrence start; when null,
 * [remindAt] is a single absolute time.
 */
data class EventReminder(
    val id: Long = 0,
    val remoteId: String? = null,
    val label: String,
    val offsetMinutes: Int? = null,
    val remindAt: LocalDateTime? = null,
    val isEnabled: Boolean = true
)

/**
 * The single model for events, tasks, exams, classes, routines and alarms.
 * Point-in-time items (tasks, alarms) use `endAt == startAt`.
 */
data class CalendarEvent(
    val id: Long = 0,
    val remoteId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    val links: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime = startAt,
    val allDay: Boolean = false,
    /** False only for undated tasks. */
    val hasDate: Boolean = true,
    val kind: CalendarEventKind = CalendarEventKind.EVENT,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    /** Alarm on/off, routine or class active flag. */
    val isEnabled: Boolean = true,
    val alarmToneUri: String? = null,
    /** Bitmask Sun=1 ... Sat=64; 0 = one-shot. Only for ALARM events. */
    val alarmRepeatDays: Int = 0,
    val recurrence: EventRecurrence? = null,
    val reminders: List<EventReminder> = emptyList(),
    /** Occurrence day when expanded from a series. */
    val instanceDate: LocalDate? = null,
    /** Master [CalendarEvent.id] for expanded instances. */
    val seriesEventId: Long? = null
) {
    val reminderCount: Int get() = reminders.size
    val canAddReminder: Boolean get() = reminders.size < MAX_REMINDERS_PER_EVENT
    val isRecurring: Boolean get() = recurrence?.repeats == true
    val masterId: Long get() = seriesEventId ?: id
}
