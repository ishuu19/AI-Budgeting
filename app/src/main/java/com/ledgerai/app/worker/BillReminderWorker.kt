package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that notifies for active bills due within [REMIND_WITHIN_DAYS] days
 * (including overdue / due today).
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val due: DueChecks
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "periodic_bill_reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresCharging(false).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        due.bills()
        return Result.success()
    }
}
