package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.worker.BillReminderWorker
import com.ledgerai.app.worker.BudgetCheckWorker
import com.ledgerai.app.worker.CheckinWorker
import com.ledgerai.app.worker.InsightDailyWorker
import com.ledgerai.app.worker.NoteScanWorker
import com.ledgerai.app.worker.QuoteDailyWorker
import com.ledgerai.app.worker.SpendGuideMorningWorker
import com.ledgerai.app.worker.WidgetRefreshWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * After reboot / app update: re-enqueue budget checks, re-arm alarms, and reschedule event reminders.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var calendarRepository: CalendarRepository
    @Inject lateinit var leaveByRepository: LeaveByRepository
    @Inject lateinit var planRepository: PlanRepository
    @Inject lateinit var habitRepository: HabitRepository

    override fun onReceive(context: Context, intent: Intent) {
        val timeChange = intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            !timeChange
        ) {
            return
        }

        if (!timeChange) {
            BudgetCheckWorker.schedule(context)
            BillReminderWorker.schedule(context)
            CheckinWorker.schedule(context)
            SpendGuideMorningWorker.schedule(context)
            NoteScanWorker.schedule(context)
            InsightDailyWorker.schedule(context)
            QuoteDailyWorker.schedule(context)
            WidgetRefreshWorker.schedule(context)
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarmCount = calendarRepository.rescheduleAllAlarms()
                Log.i(TAG, "Re-armed $alarmCount enabled alarm(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-arm alarms", e)
            }
            try {
                val reminderCount = calendarRepository.rescheduleAllReminders()
                Log.i(TAG, "Re-scheduled $reminderCount event reminder(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-schedule event reminders", e)
            }
            try {
                val leaveCount = leaveByRepository.rescheduleAllEnabled()
                Log.i(TAG, "Re-scheduled $leaveCount leave-by reminder(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-schedule leave-by reminders", e)
            }
            try {
                val planCount = planRepository.rescheduleAllBlockAlarms()
                Log.i(TAG, "Re-scheduled $planCount plan block alarm(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-schedule plan blocks", e)
            }
            try {
                val habitCount = habitRepository.rescheduleAllNudges()
                Log.i(TAG, "Re-scheduled $habitCount habit nudge chain(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-schedule habit nudges", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
