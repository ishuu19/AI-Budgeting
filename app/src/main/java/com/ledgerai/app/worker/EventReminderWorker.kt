package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.EventReminderDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: EventReminderDao,
    private val eventDao: CalendarEventDao,
    private val notificationService: NotificationService,
    private val scheduler: EventReminderScheduler
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (reminderId < 0L) return Result.failure()

        val reminder = reminderDao.getById(reminderId) ?: return Result.success()
        if (!reminder.isEnabled) return Result.success()
        val row = eventDao.getWithReminders(reminder.eventId) ?: return Result.success()
        val event = row.toDomain()
        if (!event.isEnabled) return Result.success()
        val recurring = event.isRecurring
        if (event.isCompleted && !recurring) return Result.success()

        notificationService.showEventReminder(
            title = event.title,
            reminderLabel = reminder.label,
            kind = event.kind,
            notificationId = (EVENT_REMINDER_BASE_ID + reminderId).toInt()
        )

        if (recurring) {
            event.reminders.firstOrNull { it.id == reminderId }?.let { scheduler.scheduleReminder(event, it) }
        }
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        private const val EVENT_REMINDER_BASE_ID = 40_000L
    }
}
