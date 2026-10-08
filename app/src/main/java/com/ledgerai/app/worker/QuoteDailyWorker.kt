package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.widget.VoiceTransactionWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

/**
 * Picks today's quote, persists it for the widget, and requests a Glance refresh.
 */
@HiltWorker
class QuoteDailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val quoteRepository: QuoteRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            quoteRepository.persistForWidgetRemote()
            VoiceTransactionWidget().updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_quote_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<QuoteDailyWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
