package com.ledgerai.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Max reminders attached to one weekly schedule slot (3 defaults + room for extras). */
const val MAX_REMINDERS_PER_ROUTINE_SLOT = 10

enum class TaskEventKind { TASK, EXAM, EVENT }

enum class CalendarEventKind { TASK, EXAM, CLASS, PERSONAL }

data class Course(
    val id: Long = 0,
    val remoteId: String? = null,
    val code: String = "",
    val name: String,
    val defaultLocation: String = "",
    val colorToken: String = "emerald",
    val notes: String = ""
)

/** One block in a weekly timetable (university-style). */
data class ScheduleSlot(
    val id: Long = 0,
    val remoteId: String? = null,
    val routineId: Long,
    val courseId: Long? = null,
    val title: String,
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String = "",
    /** Last calendar day this weekly class may appear (inclusive). */
    val recurrenceUntil: LocalDate? = null,
    val skippedDates: Set<LocalDate> = emptySet(),
    val reminders: List<RoutineSlotReminder> = emptyList()
) {
    val reminderCount: Int get() = reminders.size
    val canAddReminder: Boolean get() = reminders.size < MAX_REMINDERS_PER_ROUTINE_SLOT
}

data class RoutineSlotReminder(
    val id: Long = 0,
    val label: String,
    val remindAt: LocalDateTime,
    val offsetMinutes: Int? = null,
    val isEnabled: Boolean = true
)

data class CalendarEvent(
    val id: Long = 0,
    val remoteId: String? = null,
    val title: String,
    val courseId: Long? = null,
    val taskId: Long? = null,
    /** Set for weekly class blocks expanded onto the calendar (not persisted). */
    val scheduleSlotId: Long? = null,
    val location: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val kind: CalendarEventKind = CalendarEventKind.PERSONAL,
    val recurrence: EventRecurrence? = null,
    /** Occurrence day when expanded from a series. */
    val instanceDate: LocalDate? = null,
    /** Master [CalendarEvent.id] for expanded instances. */
    val seriesEventId: Long? = null
)
