package com.ledgerai.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * A small dot at the screen edge. Tapping it saves copied text the same way the tile does.
 * It never reads the clipboard and never changes colour, so it does not imply it can see copies.
 */
class ClipBubbleService : Service() {
    private var window: WindowManager? = null
    private var dot: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (dot != null) return START_STICKY
        val wm = getSystemService(WindowManager::class.java) ?: return START_NOT_STICKY
        window = wm
        val size = (48 * resources.displayMetrics.density).toInt()
        val view = FrameLayout(this)
        view.setBackgroundColor(0xFF17306F.toInt())
        view.contentDescription = "Add copied text"
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX - dx
                    params.y = startY + dy
                    wm.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        startActivity(ClipCaptureActivity.intent(this, ClipCaptureActivity.MODE_CLIPBOARD, "bubble"))
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        dot = view
        return START_STICKY
    }

    override fun onDestroy() {
        dot?.let { runCatching { window?.removeView(it) } }
        dot = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, ClipBubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipBubbleService::class.java))
        }
    }
}
