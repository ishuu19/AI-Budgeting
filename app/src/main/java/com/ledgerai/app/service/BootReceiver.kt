package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ledgerai.app.data.repository.AlarmRepository
import com.ledgerai.app.worker.BillReminderWorker
import com.ledgerai.app.worker.BudgetCheckWorker
import com.ledgerai.app.worker.TaskReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * After reboot / app update: re-enqueue budget checks, re-arm alarms, and reschedule task reminders.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var taskReminderScheduler: TaskReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        BudgetCheckWorker.schedule(context)
        BillReminderWorker.schedule(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmCount = alarmRepository.rescheduleAllEnabled()
                Log.i(TAG, "Re-armed $alarmCount enabled alarm(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-arm alarms", e)
            }
            try {
                val reminderCount = taskReminderScheduler.rescheduleAllEnabled()
                Log.i(TAG, "Re-scheduled $reminderCount task reminder(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-schedule task reminders", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
