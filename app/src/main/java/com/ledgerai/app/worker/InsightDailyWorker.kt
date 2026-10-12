package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Generates and caches a daily AI insight (type=insight) for the dashboard card.
 */
@HiltWorker
class InsightDailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val due: DueChecks,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        due.insight()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_ai_insight"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InsightDailyWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
