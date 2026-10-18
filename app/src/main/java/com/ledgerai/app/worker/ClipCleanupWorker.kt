package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.chat.ChatDao
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
    private val chat: ChatDao,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val clipDays = when (prefs.clipRetentionNow()) {
            "30" -> 30L
            "365" -> 365L
            else -> null
        }
        if (clipDays != null) {
            val before = now - clipDays * 24L * 60L * 60L * 1000L
            dao.softDeleteInboxOlder(before, now)
        }
        val chatDays = when (prefs.notebookChatKeepNow()) {
            "30" -> 30L
            "365" -> 365L
            else -> null
        }
        if (chatDays != null) {
            val chatBefore = now - chatDays * 24L * 60L * 60L * 1000L
            chat.softDeleteUnpinnedThreads(chatBefore, now)
            chat.softDeleteMessagesOfDeletedThreads(now)
        }
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
