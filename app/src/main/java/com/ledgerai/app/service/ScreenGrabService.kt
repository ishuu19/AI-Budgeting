package com.ledgerai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** Hand-off of one screenshot from [ScreenGrabService] to the panel that asked for it. */
object ScreenGrab {
    private var waiting: CompletableDeferred<File?>? = null

    fun expect(): CompletableDeferred<File?> = CompletableDeferred<File?>().also { waiting = it }

    internal fun deliver(file: File?) {
        waiting?.complete(file)
        waiting = null
    }
}

/**
 * Takes one screenshot with the permission the user just granted, then stops. Android requires a
 * foreground service of type mediaProjection for this. Nothing keeps running afterwards.
 */
class ScreenGrabService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            finish(null)
            return START_NOT_STICKY
        }
        startInForeground()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = runCatching { manager.getMediaProjection(code, data) }.getOrNull()
        if (granted == null) {
            finish(null)
            return START_NOT_STICKY
        }
        projection = granted
        // Android 14+ wants a callback registered before the projection is used.
        granted.registerCallback(object : MediaProjection.Callback() {}, main)
        // The panel hides itself first; a short wait lets the screen settle before the frame is read.
        main.postDelayed({ grab(granted) }, SETTLE_MS)
        return START_NOT_STICKY
    }

    private fun grab(granted: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = granted.createVirtualDisplay(
            "ledgerai-screen", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, main,
        )
        var done = false
        fun stop(file: File?) {
            if (done) return
            done = true
            display?.release()
            reader.close()
            finish(file)
        }
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val file = runCatching {
                val plane = image.planes[0]
                val rowPad = plane.rowStride - plane.pixelStride * width
                val padded = Bitmap.createBitmap(width + rowPad / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                val out = File(cacheDir, "screen_${System.currentTimeMillis()}.png")
                out.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                padded.recycle()
                if (cropped !== padded) cropped.recycle()
                out
            }.getOrNull()
            image.close()
            stop(file)
        }, main)
        main.postDelayed({ stop(null) }, TIMEOUT_MS)
    }

    private fun finish(file: File?) {
        ScreenGrab.deliver(file)
        projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Capturing the screen")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onDestroy() {
        projection?.stop()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL = "screen_capture"
        private const val NOTIFICATION_ID = 4107
        private const val SETTLE_MS = 450L
        private const val TIMEOUT_MS = 4_000L

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenGrabService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }
    }
}
