package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that checks budget usage and fires alerts
 * when a category exceeds its alert threshold.
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val due: DueChecks
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "periodic_budget_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BudgetCheckWorker>(6, TimeUnit.HOURS)
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
        due.budget()
        return Result.success()
    }
}
