package com.ledgerai.app.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.schedule.EventReminderRules
import com.ledgerai.app.service.EventReminderFireReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One exact alarm per [EventReminder]. WorkManager delays only run when the app is opened,
 * so a reminder set for 6pm never arrived until the next launch. The alarm fires with the
 * app closed. Repeating events re-arm themselves from the receiver.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDao: CalendarEventDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arms or cancels every reminder of [event] according to its current state. */
    fun scheduleEvent(event: CalendarEvent, now: LocalDateTime = LocalDateTime.now()) {
        event.reminders.forEach { scheduleReminder(event, it, now) }
    }

    fun scheduleReminder(
        event: CalendarEvent,
        reminder: EventReminder,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        if (reminder.id <= 0L) return
        val fireAt = EventReminderRules.nextFire(event, reminder, now)
        if (fireAt == null) {
            cancel(reminder.id)
            return
        }
        val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            cancel(reminder.id)
            return
        }
        // Drop any deferred WorkManager job so the same reminder cannot fire twice.
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(reminder.id))
        val pending = pendingIntent(reminder.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pending), pending)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing; reminder ${reminder.id} skipped", e)
        }
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(pendingIntent(reminderId))
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(reminderId))
    }

    private fun pendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, EventReminderFireReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelAll(reminderIds: Collection<Long>) {
        reminderIds.forEach { cancel(it) }
    }

    /** Re-enqueue every future reminder (boot, package replace, time zone change). */
    suspend fun rescheduleAll(): Int {
        var count = 0
        eventDao.listWithActiveReminders().forEach { row ->
            val event = row.toDomain()
            scheduleEvent(event)
            count += event.reminders.size
        }
        return count
    }

    private fun uniqueName(reminderId: Long) = "event_reminder_$reminderId"

    companion object {
        private const val TAG = "EventReminderScheduler"
        const val ACTION_FIRE = "com.ledgerai.app.action.EVENT_REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
