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
    private val aiRepository: AiRepository,
    private val notifications: NotificationService,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val insight = aiRepository.generateDailyInsight(forceRefresh = true).getOrThrow()
            val title = insight.title?.takeIf { it.isNotBlank() } ?: "LedgerAI insight"
            val body = insight.body.orEmpty()
            if (body.isNotBlank()) {
                notifications.showAiInsight(title, body)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
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
