package com.ledgerai.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaveByScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(
        ruleId: Long,
        title: String,
        placeLabel: String,
        leaveAt: LocalDateTime
    ) {
        val triggerAt = leaveAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            Log.d(TAG, "Skip past leave-by ruleId=$ruleId at $leaveAt")
            return
        }
        val pending = pendingIntent(ruleId, title, placeLabel)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, pending),
                    pending
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "Scheduled leave-by ruleId=$ruleId at $leaveAt")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing; leave-by skipped ruleId=$ruleId", e)
        }
    }

    fun cancel(ruleId: Long) {
        alarmManager.cancel(pendingIntent(ruleId, "", ""))
        Log.d(TAG, "Cancelled leave-by ruleId=$ruleId")
    }

    private fun pendingIntent(ruleId: Long, title: String, placeLabel: String): PendingIntent {
        val intent = Intent(context, LeaveByFireReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_RULE_ID, ruleId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PLACE, placeLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(ruleId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(ruleId: Long): Int =
        (REQUEST_CODE_BASE + ruleId).toInt()

    companion object {
        private const val TAG = "LeaveByScheduler"
        private const val REQUEST_CODE_BASE = 500_000L
        const val ACTION_FIRE = "com.ledgerai.app.action.LEAVE_BY_FIRE"
        const val EXTRA_RULE_ID = "leave_rule_id"
        const val EXTRA_TITLE = "leave_title"
        const val EXTRA_PLACE = "leave_place"
    }
}
