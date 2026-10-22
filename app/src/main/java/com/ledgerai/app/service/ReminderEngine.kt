package com.ledgerai.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ledgerai.app.MainActivity
import com.ledgerai.app.R
import com.ledgerai.app.data.reminders.ReminderDao
import com.ledgerai.app.data.reminders.ReminderRepository
import com.ledgerai.app.data.reminders.ReminderState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One rolling alarm for every birthday and trip reminder (docs/life-os/app-design.md section 15).
 * The `reminders` table holds the instants. This arms the earliest pending one, delivers everything
 * due when it fires, then arms the next. So the app never holds more than one of these alarms.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderDao,
    private val repo: ReminderRepository,
) {

    /** Rebuild from birthdays and trips, then arm. Safe to call often. */
    suspend fun rebuildAndArm() {
        repo.rebuildAll()
        arm()
    }

    suspend fun arm() {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent()
        val next = dao.next()
        if (next == null) {
            manager.cancel(pending)
            return
        }
        val at = maxOf(next.fireAt, System.currentTimeMillis() + 1_000)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** Posts everything due now (reminders in the same minute become one notification), then re-arms. */
    suspend fun deliverDue() {
        val due = dao.due(System.currentTimeMillis() + WINDOW_MS)
        if (due.isNotEmpty()) {
            ensureChannel()
            val allowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (allowed) {
                due.groupBy { it.fireAt / 60_000 to it.sourceId }.values.forEach { group ->
                    val last = group.last()
                    NotificationManagerCompat.from(context).notify(last.id.hashCode(), build(last.title, group.map { it.body }.distinct().joinToString(" · ")))
                }
            }
            dao.mark(due.map { it.id }, ReminderState.DELIVERED)
        }
        // A delivered "at the time" birthday needs next year's rows.
        repo.rebuildAll()
        arm()
    }

    /** Settings → Reminders → test. Posts one notification now. */
    fun sendTest() {
        ensureChannel()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(TEST_ID, build("Test reminder", "Birthday and trip reminders look like this."))
    }

    private fun build(title: String, body: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Birthdays and trips", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, ReminderFireReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION = "com.ledgerai.app.action.REMINDER_FIRE"
        private const val CHANNEL = "dates"
        private const val REQUEST = 7301
        private const val TEST_ID = 7302
        private const val WINDOW_MS = 30_000L
    }
}

@AndroidEntryPoint
class ReminderFireReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: ReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                engine.deliverDue()
            } finally {
                pending.finish()
            }
        }
    }
}
