package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Soft-deletes inbox clips older than the retention setting. Clips in notes or already filed stay. */
@HiltWorker
class ClipCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ClipDao,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val keep = prefs.clipRetentionNow()
        val days = when (keep) {
            "30" -> 30L
            "365" -> 365L
            else -> return Result.success()
        }
        val now = System.currentTimeMillis()
        val before = now - days * 24L * 60L * 60L * 1000L
        dao.softDeleteInboxOlder(before, now)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "clip_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClipCleanupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
