package com.ledgerai.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ledgerai.app.domain.model.AlarmItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin AlarmManager wrapper. Full ringing UI arrives in a later phase;
 * this schedules a broadcast stub so the shell is wired end-to-end.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmItem) {
        val triggerAt = nextTriggerMillis(alarm)
        val pending = pendingIntent(alarm.id)
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
            Log.d(TAG, "Scheduled alarm id=${alarm.id} at $triggerAt")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing; schedule skipped for id=${alarm.id}", e)
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId))
        Log.d(TAG, "Cancelled alarm id=$alarmId")
    }

    private fun nextTriggerMillis(alarm: AlarmItem): Long {
        val zone = ZoneId.systemDefault()
        var dateTime = LocalDateTime.of(LocalDate.now(), alarm.time)
        if (dateTime.isBefore(LocalDateTime.now())) {
            dateTime = dateTime.plusDays(1)
        }
        return dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    private fun pendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmFireReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE = "com.ledgerai.app.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
