package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the ringing foreground service when a scheduled alarm fires. */
class AlarmFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id < 0L) {
            Log.w(TAG, "Alarm fire missing id")
            return
        }
        Log.i(TAG, "Alarm fired id=$id — starting ring service")
        AlarmRingingService.start(context, id)
    }

    companion object {
        private const val TAG = "AlarmFireReceiver"
    }
}
