package com.ledgerai.app.domain.model

import java.time.LocalDateTime

/** Hard limit: at most this many custom reminders may be attached to one task. */
const val MAX_REMINDERS_PER_TASK = 10

data class TaskItem(
    val id: Long = 0,
    val remoteId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    val links: String = "",
    val dueAt: LocalDateTime? = null,
    val isCompleted: Boolean = false,
    val reminders: List<TaskReminder> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    val reminderCount: Int get() = reminders.size
    val canAddReminder: Boolean get() = reminders.size < MAX_REMINDERS_PER_TASK
}

data class TaskReminder(
    val id: Long = 0,
    val label: String,
    /** Absolute fire time for the reminder. */
    val remindAt: LocalDateTime,
    /** Minutes before the task due time. When set, editing the due time moves this reminder. */
    val offsetMinutes: Int? = null,
    val isEnabled: Boolean = true
)

data class RoutineItem(
    val id: Long = 0,
    val remoteId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    /** Opaque repeat rule (e.g. DAILY, WEEKLY, WEEKDAYS, or custom). */
    val repeatRule: String = "",
    val isActive: Boolean = true
)
