package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.auth.SessionGuard
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.sync.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic Room ↔ Supabase sync. No-ops unless the session is a remote user with a JWT.
 * [LedgerApp] schedules when [com.ledgerai.app.data.preferences.UserInfo.hasRemoteUser] is true.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val userSession: UserSession,
    private val sessionGuard: SessionGuard,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val user = userSession.userInfo.first()
        if (!user.hasRemoteUser) return Result.success()

        if (!sessionGuard.ensureFreshSession()) return Result.retry()

        return try {
            syncRepository.syncAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "periodic_supabase_sync"
        private const val NOW_WORK_NAME = "supabase_sync_now"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** One-shot sync. [ExistingWorkPolicy.KEEP] drops repeats while one is queued or running. */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
