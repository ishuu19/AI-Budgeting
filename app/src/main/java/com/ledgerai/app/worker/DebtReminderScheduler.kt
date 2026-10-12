package com.ledgerai.app.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import com.ledgerai.app.service.DebtReminderFireReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms for debt reminders (7d, 5d, 1d, 10h, 30m before).
 * WorkManager delays were held until the app was opened.
 */
@Singleton
class DebtReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminders(debtId: Long, friendName: String, amount: Double, dueDate: LocalDate) {
        val nowMillis = System.currentTimeMillis()
        val dueDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reminderOffsets = listOf(
            7 * 24 * 60 * 60 * 1000L to 7,
            5 * 24 * 60 * 60 * 1000L to 5,
            1 * 24 * 60 * 60 * 1000L to 1,
            10 * 60 * 60 * 1000L to 0,
            30 * 60 * 1000L to 0
        )
        reminderOffsets.forEachIndexed { index, (offsetMillis, daysLeft) ->
            WorkManager.getInstance(context).cancelUniqueWork("debt_reminder_${debtId}_$index")
            val triggerMillis = dueDateMillis - offsetMillis
            if (triggerMillis <= nowMillis) {
                alarmManager.cancel(pending(debtId, index, friendName, amount, daysLeft))
                return@forEachIndexed
            }
            val pending = pending(debtId, index, friendName, amount, daysLeft)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, pending), pending)
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Debt reminder skipped debtId=$debtId", e)
            }
        }
    }

    fun cancelReminders(debtId: Long) {
        val workManager = WorkManager.getInstance(context)
        for (i in 0..4) {
            workManager.cancelUniqueWork("debt_reminder_${debtId}_$i")
            alarmManager.cancel(pending(debtId, i, "", 0.0, 0))
        }
    }

    private fun pending(debtId: Long, index: Int, friendName: String, amount: Double, daysLeft: Int): PendingIntent {
        val intent = Intent(context, DebtReminderFireReceiver::class.java).apply {
            action = DebtReminderFireReceiver.ACTION_FIRE
            putExtra(DebtReminderFireReceiver.EXTRA_DEBT_ID, debtId)
            putExtra(DebtReminderFireReceiver.EXTRA_FRIEND, friendName)
            putExtra(DebtReminderFireReceiver.EXTRA_AMOUNT, amount)
            putExtra(DebtReminderFireReceiver.EXTRA_DAYS_LEFT, daysLeft)
            putExtra(DebtReminderFireReceiver.EXTRA_INDEX, index)
        }
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_BASE + debtId * 10 + index).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "DebtReminderScheduler"
        private const val REQUEST_BASE = 800_000L
    }
}
