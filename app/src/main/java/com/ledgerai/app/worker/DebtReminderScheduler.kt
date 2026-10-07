package com.ledgerai.app.worker

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules 5-tier debt reminder notifications using WorkManager:
 *   - 7 days before
 *   - 5 days before
 *   - 1 day before
 *   - 10 hours before
 *   - 30 minutes before
 */
@Singleton
class DebtReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleReminders(debtId: Long, friendName: String, amount: Double, dueDate: LocalDate) {
        val nowMillis = System.currentTimeMillis()
        val dueDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val reminderOffsets = listOf(
            7 * 24 * 60 * 60 * 1000L to 7,    // 7 days
            5 * 24 * 60 * 60 * 1000L to 5,    // 5 days
            1 * 24 * 60 * 60 * 1000L to 1,    // 1 day
            10 * 60 * 60 * 1000L to 0,         // 10 hours
            30 * 60 * 1000L to 0               // 30 minutes
        )

        reminderOffsets.forEachIndexed { index, (offsetMillis, daysLeft) ->
            val triggerMillis = dueDateMillis - offsetMillis
            val delayMillis = triggerMillis - nowMillis

            if (delayMillis > 0) {
                val inputData = workDataOf(
                    DebtReminderWorker.KEY_DEBT_ID to debtId,
                    DebtReminderWorker.KEY_FRIEND_NAME to friendName,
                    DebtReminderWorker.KEY_AMOUNT to amount,
                    DebtReminderWorker.KEY_DAYS_LEFT to daysLeft
                )

                val workRequest = OneTimeWorkRequestBuilder<DebtReminderWorker>()
                    .setInputData(inputData)
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .addTag("debt_reminder_${debtId}_$index")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "debt_reminder_${debtId}_$index",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }
    }

    fun cancelReminders(debtId: Long) {
        val workManager = WorkManager.getInstance(context)
        for (i in 0..4) {
            workManager.cancelUniqueWork("debt_reminder_${debtId}_$i")
        }
    }
}
