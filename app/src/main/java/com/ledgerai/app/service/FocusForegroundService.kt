package com.ledgerai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ledgerai.app.MainActivity
import com.ledgerai.app.R

class FocusForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val blockId = intent.getLongExtra(EXTRA_BLOCK_ID, 0L)
                val topic = intent.getStringExtra(EXTRA_TOPIC).orEmpty().ifBlank { "Focus" }
                acquireWakeLock()
                startForeground(NOTIFICATION_ID, buildNotification(blockId, topic))
            }
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ledgerai:focus").apply {
            acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(blockId: Long, topic: String): Notification {
        createChannel()
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_FOCUS_BLOCK_ID, blockId)
            putExtra(MainActivity.EXTRA_FOCUS_TOPIC, topic)
        }
        val pending = PendingIntent.getActivity(
            this, blockId.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Focus")
            .setContentText(topic)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus timer",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.ledgerai.app.action.FOCUS_START"
        const val ACTION_STOP = "com.ledgerai.app.action.FOCUS_STOP"
        const val EXTRA_BLOCK_ID = "focus_block_id"
        const val EXTRA_TOPIC = "focus_topic"
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1002
    }
}
