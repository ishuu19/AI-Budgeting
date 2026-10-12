package com.ledgerai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ledgerai.app.R
import com.ledgerai.app.worker.ClipDigestWorker

/** Pinned "tap to save what you copied" notice. It never includes the clip text. */
object ClipPins {
    private const val ID = 42012

    fun show(context: Context, collectingTitle: String?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(ClipDigestWorker.CHANNEL, "Add to Notes", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val text = if (collectingTitle.isNullOrBlank()) {
            "Add to Notes · Tap to save what you copied"
        } else {
            "Collecting into $collectingTitle"
        }
        val intent = ClipCaptureActivity.intent(context, ClipCaptureActivity.MODE_CLIPBOARD, "clipboard")
        val pending = PendingIntent.getActivity(
            context, 7, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ClipDigestWorker.CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_capture)
            .setContentTitle("Add to Notes")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
        manager.notify(ID, notification)
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
