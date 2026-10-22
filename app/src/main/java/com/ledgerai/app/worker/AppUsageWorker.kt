package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.appusage.UsageCollector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Hourly. Does nothing until the user has granted Usage Access. */
@HiltWorker
class AppUsageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val collector: UsageCollector,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        collector.collect()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "app_usage"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUsageWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
