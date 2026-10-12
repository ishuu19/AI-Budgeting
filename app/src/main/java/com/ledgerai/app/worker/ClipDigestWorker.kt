package com.ledgerai.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.R
import com.ledgerai.app.data.clips.ClipDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** "You saved 9 clips today: 2 tasks, 1 event waiting." Counts only. No clip text. */
@HiltWorker
class ClipDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ClipDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rows = dao.capturedSince(start)
        if (rows.isEmpty()) return Result.success()
        val tasks = rows.count { it.aiKind == "task" && it.filedId == null && it.aiState == "done" }
        val events = rows.count { it.aiKind == "event" && it.filedId == null && it.aiState == "done" }
        val text = "You saved ${rows.size} clips today: $tasks tasks, $events event waiting."
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Add to Notes", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_capture)
            .setContentTitle("Add to Notes")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        manager?.notify(DIGEST_ID, notification)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "clip_digest"
        const val CHANNEL = "add_to_notes"
        private const val DIGEST_ID = 42013

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClipDigestWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
