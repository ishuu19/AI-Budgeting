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
class PlanBlockScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(blockId: Long, title: String, startAt: LocalDateTime) {
        val triggerAt = startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        val pending = pendingIntent(blockId, title)
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
        } catch (e: SecurityException) {
            Log.w(TAG, "Plan block alarm skipped blockId=$blockId", e)
        }
    }

    fun cancel(blockId: Long) {
        alarmManager.cancel(pendingIntent(blockId, ""))
    }

    private fun pendingIntent(blockId: Long, title: String): PendingIntent {
        val intent = Intent(context, PlanBlockFireReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_BLOCK_ID, blockId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_CODE_BASE + blockId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "PlanBlockScheduler"
        private const val REQUEST_CODE_BASE = 600_000L
        const val ACTION_FIRE = "com.ledgerai.app.action.PLAN_BLOCK_FIRE"
        const val EXTRA_BLOCK_ID = "plan_block_id"
        const val EXTRA_TITLE = "plan_block_title"
    }
}
