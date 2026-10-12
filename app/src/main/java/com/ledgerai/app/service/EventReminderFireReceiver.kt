package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.EventReminderDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.worker.EventReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires an event reminder while the app is closed. The alarm, not WorkManager, wakes the phone. */
@AndroidEntryPoint
class EventReminderFireReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderDao: EventReminderDao
    @Inject lateinit var eventDao: CalendarEventDao
    @Inject lateinit var notificationService: NotificationService
    @Inject lateinit var scheduler: EventReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EventReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getLongExtra(EventReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderDao.getById(reminderId) ?: return@launch
                if (!reminder.isEnabled) return@launch
                val row = eventDao.getWithReminders(reminder.eventId) ?: return@launch
                val event = row.toDomain()
                if (!event.isEnabled) return@launch
                val recurring = event.isRecurring
                if (event.isCompleted && !recurring) return@launch
                notificationService.showEventReminder(
                    title = event.title,
                    reminderLabel = reminder.label,
                    kind = event.kind,
                    notificationId = (EVENT_REMINDER_BASE_ID + reminderId).toInt()
                )
                if (recurring) {
                    event.reminders.firstOrNull { it.id == reminderId }?.let { scheduler.scheduleReminder(event, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EVENT_REMINDER_BASE_ID = 40_000L
    }
}
