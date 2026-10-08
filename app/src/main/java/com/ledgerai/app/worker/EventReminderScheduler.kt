package com.ledgerai.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.schedule.EventReminderRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot WorkManager jobs, one per [EventReminder]. Unique work name `event_reminder_{id}` keeps
 * boot, edits and sync pulls idempotent. Repeating events re-arm themselves from the worker.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDao: CalendarEventDao
) {

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
        val delay = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        if (delay <= 0L) {
            cancel(reminder.id)
            return
        }
        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInputData(workDataOf(EventReminderWorker.KEY_REMINDER_ID to reminder.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(uniqueName(reminder.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(reminderId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(reminderId))
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
}
