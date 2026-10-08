package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.data.local.room.TaskReminderDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_TASK
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import com.ledgerai.app.domain.schedule.defaultBeforeEventOptions
import com.ledgerai.app.worker.TaskReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val reminderDao: TaskReminderDao,
    private val reminderScheduler: TaskReminderScheduler,
    private val calendarRepository: CalendarRepository,
    private val leaveByRepository: LeaveByRepository
) {

    fun observeTasks(): Flow<List<TaskItem>> =
        dao.observeWithReminders().map { list ->
            list.map { it.toDomain() }.thenByDue()
        }

    suspend fun findById(id: Long): TaskItem? = dao.getById(id)?.toDomain()

    suspend fun insert(task: TaskItem): Long {
        val capped = task.copy(reminders = task.reminders.take(MAX_REMINDERS_PER_TASK))
        val now = System.currentTimeMillis()
        val taskId = if (capped.id == 0L) {
            dao.insert(capped.toEntity(updatedAt = now, deletedAt = null).copy(id = 0))
        } else {
            val existing = dao.getById(capped.id)
            dao.insert(
                capped.toEntity(
                    userId = existing?.userId,
                    updatedAt = now,
                    deletedAt = null
                )
            )
            capped.id
        }
        replaceReminders(taskId, capped.reminders, userId = dao.getById(taskId)?.userId)
        syncCalendar(taskId)
        return taskId
    }

    suspend fun update(task: TaskItem) {
        if (task.id == 0L) return
        val existing = dao.getById(task.id)
        val capped = task.copy(reminders = task.reminders.take(MAX_REMINDERS_PER_TASK))
        dao.update(
            capped.toEntity(
                userId = existing?.userId,
                updatedAt = System.currentTimeMillis(),
                deletedAt = null
            )
        )
        replaceReminders(task.id, capped.reminders, userId = existing?.userId)
        syncCalendar(task.id)
    }

    suspend fun delete(task: TaskItem) {
        if (task.id == 0L) return
        cancelScheduledForTask(task.id)
        val now = System.currentTimeMillis()
        reminderDao.softDeleteForTask(task.id, deletedAt = now, updatedAt = now)
        dao.softDelete(task.id, deletedAt = now, updatedAt = now)
        calendarRepository.removeForTask(task.id)
        leaveByRepository.removeForRef(LeaveRefType.TASK, task.id)
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.update(
            entity.copy(
                isCompleted = completed,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (completed) {
            cancelScheduledForTask(id)
            leaveByRepository.removeForRef(LeaveRefType.TASK, id)
        } else {
            reminderDao.listEnabledForTask(id).forEach { reminder ->
                reminderScheduler.schedule(reminder, entity.title)
            }
        }
    }

    /**
     * Adds a reminder when under the cap. Returns false if the task already has
     * [MAX_REMINDERS_PER_TASK] reminders (caller should surface this in UI).
     */
    suspend fun addReminder(
        taskId: Long,
        label: String,
        remindAt: LocalDateTime,
        offsetMinutes: Int? = null
    ): Boolean {
        val task = dao.getById(taskId) ?: return false
        if (reminderDao.countActiveForTask(taskId) >= MAX_REMINDERS_PER_TASK) return false
        val id = reminderDao.insert(
            TaskReminder(label = label, remindAt = remindAt, offsetMinutes = offsetMinutes)
                .toEntity(taskId = taskId, userId = task.userId)
                .copy(id = 0)
        )
        reminderScheduler.schedule(id, task.title, label, remindAt)
        return true
    }

    /**
     * Schedules [offsets] (or the standard 10m / 1h / 3h set) before [eventAt].
     * Skips times that are not in the future or when the task is at the reminder cap.
     */
    suspend fun seedBeforeEventReminders(
        taskId: Long,
        eventAt: LocalDateTime,
        offsets: List<Pair<String, Long>> = defaultBeforeEventOptions()
    ): Int {
        val now = LocalDateTime.now()
        var added = 0
        for ((label, minutes) in offsets.distinctBy { it.second }) {
            if (minutes < 0) continue
            val at = eventAt.minusMinutes(minutes)
            if (!at.isAfter(now)) continue
            val offset = minutes.toInt().takeIf { minutes > 0 }
            if (addReminder(taskId, label, at, offset)) added++
        }
        return added
    }

    suspend fun removeReminder(taskId: Long, reminderId: Long) {
        dao.getById(taskId) ?: return
        reminderScheduler.cancel(reminderId)
        val now = System.currentTimeMillis()
        reminderDao.softDelete(reminderId, deletedAt = now, updatedAt = now)
    }

    /** Re-arms WorkManager jobs for future enabled reminders (e.g. after boot). */
    suspend fun rescheduleAllReminders(): Int = reminderScheduler.rescheduleAllEnabled()

    private suspend fun replaceReminders(
        taskId: Long,
        reminders: List<TaskReminder>,
        userId: String?
    ) {
        cancelScheduledForTask(taskId)
        val now = System.currentTimeMillis()
        reminderDao.softDeleteForTask(taskId, deletedAt = now, updatedAt = now)
        val taskTitle = dao.getById(taskId)?.title ?: "Task"
        reminders.take(MAX_REMINDERS_PER_TASK).forEach { reminder ->
            val id = reminderDao.insert(
                reminder.toEntity(taskId = taskId, userId = userId, updatedAt = now)
                    .copy(id = 0)
            )
            if (reminder.isEnabled) {
                reminderScheduler.schedule(id, taskTitle, reminder.label, reminder.remindAt)
            }
        }
    }

    private suspend fun cancelScheduledForTask(taskId: Long) {
        val ids = reminderDao.listForTask(taskId).map { it.id } +
            reminderDao.listEnabledForTask(taskId).map { it.id }
        reminderScheduler.cancelAll(ids.distinct())
    }

    private suspend fun syncCalendar(taskId: Long) {
        val entity = dao.getById(taskId) ?: return
        calendarRepository.syncFromTask(
            taskId = taskId,
            title = entity.title,
            courseId = entity.courseId,
            startAt = entity.dueAt,
            eventKind = entity.eventKind,
            location = entity.location
        )
    }

    private fun List<TaskItem>.thenByDue(): List<TaskItem> =
        sortedWith(
            compareBy<TaskItem> { it.isCompleted }
                .thenBy { it.dueAt ?: LocalDateTime.MAX }
                .thenByDescending { it.createdAt }
        )
}
