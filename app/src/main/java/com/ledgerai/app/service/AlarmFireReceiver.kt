package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Stub receiver for scheduled alarms; ringing service lands later. */
class AlarmFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        Log.i(TAG, "Alarm fired id=$id (stub — no ring UI yet)")
    }

    companion object {
        private const val TAG = "AlarmFireReceiver"
    }
}
