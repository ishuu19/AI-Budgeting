package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.data.local.room.TaskReminderDao
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: TaskReminderDao,
    private val taskDao: TaskDao,
    private val notificationService: NotificationService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (reminderId < 0L) return Result.failure()

        val reminder = reminderDao.getById(reminderId) ?: return Result.success()
        if (!reminder.isEnabled) return Result.success()

        val task = taskDao.getById(reminder.taskId)
        if (task == null || task.isCompleted) return Result.success()

        val taskTitle = inputData.getString(KEY_TASK_TITLE)
            ?: task.title
        val label = inputData.getString(KEY_LABEL) ?: reminder.label

        notificationService.showTaskReminder(
            taskTitle = taskTitle,
            reminderLabel = label,
            notificationId = (TASK_REMINDER_BASE_ID + reminderId).toInt()
        )
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_LABEL = "label"
        private const val TASK_REMINDER_BASE_ID = 40_000L
    }
}
