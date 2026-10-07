package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.feature.TaskLocalStore
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_TASK
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val store: TaskLocalStore
) {

    fun observeTasks(): Flow<List<TaskItem>> =
        store.tasks.map { it.thenByDue() }

    suspend fun insert(task: TaskItem): Long {
        val capped = task.copy(reminders = task.reminders.take(MAX_REMINDERS_PER_TASK))
        val id = if (capped.id == 0L) store.nextId() else capped.id
        store.upsert(capped.copy(id = id))
        return id
    }

    suspend fun update(task: TaskItem) {
        if (task.id == 0L) return
        store.upsert(task.copy(reminders = task.reminders.take(MAX_REMINDERS_PER_TASK)))
    }

    suspend fun delete(task: TaskItem) {
        if (task.id == 0L) return
        store.remove(task.id)
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val existing = store.getById(id) ?: return
        store.upsert(existing.copy(isCompleted = completed))
    }

    /**
     * Adds a reminder when under the cap. Returns false if the task already has
     * [MAX_REMINDERS_PER_TASK] reminders (caller should surface this in UI).
     */
    suspend fun addReminder(taskId: Long, label: String, remindAt: LocalDateTime): Boolean {
        val existing = store.getById(taskId) ?: return false
        if (existing.reminders.size >= MAX_REMINDERS_PER_TASK) return false
        val reminder = TaskReminder(
            id = store.nextReminderId(),
            label = label,
            remindAt = remindAt
        )
        store.upsert(existing.copy(reminders = existing.reminders + reminder))
        return true
    }

    suspend fun removeReminder(taskId: Long, reminderId: Long) {
        val existing = store.getById(taskId) ?: return
        store.upsert(existing.copy(reminders = existing.reminders.filterNot { it.id == reminderId }))
    }

    private fun List<TaskItem>.thenByDue(): List<TaskItem> =
        sortedWith(
            compareBy<TaskItem> { it.isCompleted }
                .thenBy { it.dueAt ?: LocalDateTime.MAX }
                .thenByDescending { it.createdAt }
        )
}
