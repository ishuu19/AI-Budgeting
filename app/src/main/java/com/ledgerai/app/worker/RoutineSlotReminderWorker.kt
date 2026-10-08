package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgerai.app.data.local.room.RoutineSlotReminderDao
import com.ledgerai.app.data.local.room.ScheduleSlotDao
import com.ledgerai.app.data.schedule.nextOccurrence
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

@HiltWorker
class RoutineSlotReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: RoutineSlotReminderDao,
    private val slotDao: ScheduleSlotDao,
    private val notificationService: NotificationService,
    private val scheduler: RoutineSlotReminderScheduler
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (reminderId < 0L) return Result.failure()

        val reminder = reminderDao.getById(reminderId) ?: return Result.success()
        if (!reminder.isEnabled) return Result.success()

        val slot = slotDao.getById(reminder.slotId) ?: return Result.success()
        val slotTitle = inputData.getString(KEY_SLOT_TITLE) ?: slot.title
        val label = inputData.getString(KEY_LABEL) ?: reminder.label

        notificationService.showRoutineSlotReminder(
            slotTitle = slotTitle,
            reminderLabel = label,
            notificationId = (ROUTINE_REMINDER_BASE_ID + reminderId).toInt()
        )

        val nextAt = when {
            reminder.offsetMinutes != null ->
                nextOccurrence(slot.dayOfWeek, slot.startTime)
                    .minusMinutes(reminder.offsetMinutes.toLong())
            else -> reminder.remindAt.plusWeeks(1)
        }
        if (nextAt.isAfter(LocalDateTime.now())) {
            val updated = reminder.copy(
                remindAt = nextAt,
                updatedAt = System.currentTimeMillis()
            )
            reminderDao.update(updated)
            scheduler.schedule(updated, slot.title)
        }
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "routine_reminder_id"
        const val KEY_SLOT_TITLE = "slot_title"
        const val KEY_LABEL = "label"
        private const val ROUTINE_REMINDER_BASE_ID = 50_000L
    }
}
