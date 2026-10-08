package com.ledgerai.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ledgerai.app.domain.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** AlarmManager wrapper for exact / alarm-clock triggers and snooze. */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: CalendarEvent) {
        val triggerAt = nextTriggerMillis(alarm)
        scheduleAt(alarm.id, triggerAt)
    }

    fun scheduleSnooze(alarmId: Long, minutes: Int) {
        val triggerAt = System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000L
        scheduleAt(alarmId, triggerAt)
        Log.d(TAG, "Snoozed alarm id=$alarmId for ${minutes}m")
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId))
        Log.d(TAG, "Cancelled alarm id=$alarmId")
    }

    private fun scheduleAt(alarmId: Long, triggerAt: Long) {
        val pending = pendingIntent(alarmId)
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
            Log.d(TAG, "Scheduled alarm id=$alarmId at $triggerAt")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing; schedule skipped for id=$alarmId", e)
        }
    }

    /** @see AlarmTriggerCalc.nextTriggerMillis */
    internal fun nextTriggerMillis(alarm: CalendarEvent): Long =
        AlarmTriggerCalc.nextTriggerMillis(alarm.startAt.toLocalTime(), alarm.alarmRepeatDays)

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
