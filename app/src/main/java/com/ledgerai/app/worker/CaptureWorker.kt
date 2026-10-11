package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.capture.CaptureEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Looks at new photos with the AI and uploads them to private storage. Runs only with a network,
 * survives the app being closed, and retries with backoff, so captures made offline are filed later.
 */
@HiltWorker
class CaptureWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: CaptureEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (engine.processPending().retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "capture_pipeline"

        /**
         * Queue a pass. REPLACE starts now instead of waiting behind an earlier run that is sitting in a
         * retry delay. A pass re-reads the database, so nothing captured mid-run is lost.
         */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<CaptureWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
