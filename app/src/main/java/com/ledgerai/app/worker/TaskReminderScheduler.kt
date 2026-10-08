package com.ledgerai.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.data.local.room.TaskReminderDao
import com.ledgerai.app.data.local.room.TaskReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one-shot WorkManager jobs for each task reminder.
 * Unique work name: `task_reminder_{id}` so boot / replace stays idempotent.
 */
@Singleton
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: TaskReminderDao,
    private val taskDao: TaskDao
) {

    fun schedule(
        reminderId: Long,
        taskTitle: String,
        label: String,
        remindAt: LocalDateTime
    ) {
        val delayMillis = millisUntil(remindAt)
        if (delayMillis <= 0L) return

        val inputData = workDataOf(
            TaskReminderWorker.KEY_REMINDER_ID to reminderId,
            TaskReminderWorker.KEY_TASK_TITLE to taskTitle,
            TaskReminderWorker.KEY_LABEL to label
        )

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(tagFor(reminderId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(reminderId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedule(entity: TaskReminderEntity, taskTitle: String) {
        if (!entity.isEnabled) {
            cancel(entity.id)
            return
        }
        schedule(entity.id, taskTitle, entity.label, entity.remindAt)
    }

    fun cancel(reminderId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(reminderId))
    }

    fun cancelAll(reminderIds: Collection<Long>) {
        reminderIds.forEach { cancel(it) }
    }

    /** Re-enqueue all future enabled reminders (boot / package replace). */
    suspend fun rescheduleAllEnabled(): Int {
        val now = LocalDateTime.now()
        var count = 0
        reminderDao.listEnabled().forEach { reminder ->
            if (reminder.remindAt.isBefore(now) || reminder.remindAt.isEqual(now)) return@forEach
            val task = taskDao.getById(reminder.taskId) ?: return@forEach
            if (task.isCompleted) return@forEach
            schedule(reminder, task.title)
            count++
        }
        return count
    }

    private fun millisUntil(remindAt: LocalDateTime): Long {
        val trigger = remindAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return trigger - System.currentTimeMillis()
    }

    private fun uniqueName(reminderId: Long) = "task_reminder_$reminderId"

    private fun tagFor(reminderId: Long) = "task_reminder_$reminderId"
}
