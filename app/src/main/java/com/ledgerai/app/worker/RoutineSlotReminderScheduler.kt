package com.ledgerai.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ledgerai.app.data.local.room.RoutineSlotReminderDao
import com.ledgerai.app.data.local.room.RoutineSlotReminderEntity
import com.ledgerai.app.data.local.room.ScheduleSlotDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineSlotReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: RoutineSlotReminderDao,
    private val slotDao: ScheduleSlotDao
) {

    fun schedule(
        reminderId: Long,
        slotTitle: String,
        label: String,
        remindAt: LocalDateTime
    ) {
        val delayMillis = millisUntil(remindAt)
        if (delayMillis <= 0L) return

        val inputData = workDataOf(
            RoutineSlotReminderWorker.KEY_REMINDER_ID to reminderId,
            RoutineSlotReminderWorker.KEY_SLOT_TITLE to slotTitle,
            RoutineSlotReminderWorker.KEY_LABEL to label
        )

        val request = OneTimeWorkRequestBuilder<RoutineSlotReminderWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(uniqueName(reminderId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(reminderId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedule(entity: RoutineSlotReminderEntity, slotTitle: String) {
        if (!entity.isEnabled) {
            cancel(entity.id)
            return
        }
        schedule(entity.id, slotTitle, entity.label, entity.remindAt)
    }

    fun cancel(reminderId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(reminderId))
    }

    suspend fun rescheduleAllEnabled(): Int {
        val now = LocalDateTime.now()
        var count = 0
        reminderDao.listEnabled().forEach { reminder ->
            if (reminder.remindAt.isBefore(now) || reminder.remindAt.isEqual(now)) return@forEach
            val slot = slotDao.getById(reminder.slotId) ?: return@forEach
            schedule(reminder, slot.title)
            count++
        }
        return count
    }

    private fun millisUntil(remindAt: LocalDateTime): Long {
        val trigger = remindAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return trigger - System.currentTimeMillis()
    }

    private fun uniqueName(reminderId: Long) = "routine_slot_reminder_$reminderId"
}
