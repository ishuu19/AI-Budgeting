package com.ledgerai.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ledgerai.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_BUDGET = "budget_alerts"
        const val CHANNEL_DEBT = "debt_reminders"
        const val CHANNEL_BILLS = "bill_reminders"
        const val CHANNEL_TASK = "task_reminders"
        const val CHANNEL_ROUTINE = "routine_reminders"
        const val CHANNEL_LEAVE = "leave_by"
        const val CHANNEL_PLAN = "plan_blocks"
        const val CHANNEL_NUDGES = "habit_nudges"
        const val CHANNEL_CHECKIN = "check_in"
        const val CHANNEL_SPEND = "spend_guide"
        const val CHANNEL_JOBS = "jobs"
        const val CHANNEL_AI = "ai_insights"

        enum class NudgeImportance { LOW, DEFAULT, HIGH }

        /** Single ~1s vibration pulse for Life reminders. */
        val REMINDER_VIBRATION_PATTERN = longArrayOf(0, 1000)
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    fun showBudgetAlert(categoryName: String, usagePercent: Int, notificationId: Int = usagePercent) {
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget Alert: $categoryName")
            .setContentText("You've used $usagePercent% of your $categoryName budget.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showDebtReminder(friendName: String, amount: Double, daysLeft: Int, notificationId: Int) {
        val message = when {
            daysLeft <= 0 -> "$friendName's debt of \$$amount is due today!"
            daysLeft == 1 -> "Reminder: $friendName owes you \$$amount — due tomorrow"
            else -> "Reminder: $friendName owes you \$$amount — due in $daysLeft days"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DEBT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Debt Reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showBillReminder(billName: String, amount: Double, daysLeft: Int, notificationId: Int) {
        val message = when {
            daysLeft <= 0 -> "$billName (\$$amount) is due today!"
            else -> "$billName (\$$amount) is due in $daysLeft days"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bill Due Soon")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showTaskReminder(taskTitle: String, reminderLabel: String, notificationId: Int) {
        val message = if (reminderLabel.isBlank() || reminderLabel == taskTitle) {
            taskTitle
        } else {
            "$reminderLabel — $taskTitle"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Task reminder")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showLeaveByReminder(
        eventTitle: String,
        placeLabel: String,
        notificationId: Int
    ) {
        val placePart = placeLabel.trim().takeIf { it.isNotEmpty() }?.let { " for $it" } ?: ""
        val message = "Time to leave$placePart — $eventTitle"

        val notification = NotificationCompat.Builder(context, CHANNEL_LEAVE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Leave by")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showRoutineSlotReminder(slotTitle: String, reminderLabel: String, notificationId: Int) {
        val message = if (reminderLabel.isBlank() || reminderLabel == slotTitle) {
            slotTitle
        } else {
            "$reminderLabel — $slotTitle"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ROUTINE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daily routine")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showPlanBlockStart(blockId: Long, title: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOCUS_BLOCK_ID, blockId)
            putExtra(MainActivity.EXTRA_FOCUS_TOPIC, title)
        }
        val pending = PendingIntent.getActivity(
            context, blockId.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Study block")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
            .setContentIntent(pending)
            .build()
        notificationManager.notify((blockId + 10_000).toInt(), notification)
    }

    fun showHabitNudge(
        habitId: Long,
        title: String,
        minutesBefore: Int,
        importance: NudgeImportance
    ) {
        val message = when {
            minutesBefore <= 0 -> "Start $title now"
            else -> "$title in $minutesBefore min"
        }
        val startPi = habitActionPending(HabitNudgeActionReceiver.ACTION_START, habitId, title, 1)
        val snoozePi = habitActionPending(HabitNudgeActionReceiver.ACTION_SNOOZE, habitId, title, 2)
        val skipPi = habitActionPending(HabitNudgeActionReceiver.ACTION_SKIP, habitId, title, 3)
        val priority = when (importance) {
            NudgeImportance.HIGH -> NotificationCompat.PRIORITY_HIGH
            NudgeImportance.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            NudgeImportance.LOW -> NotificationCompat.PRIORITY_LOW
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_NUDGES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Habit")
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .addAction(0, "Start", startPi)
            .addAction(0, "Snooze", snoozePi)
            .addAction(0, "Skip", skipPi)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()
        notificationManager.notify((habitId + 20_000).toInt(), notification)
    }

    private fun habitActionPending(action: String, habitId: Long, title: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, HabitNudgeActionReceiver::class.java).apply {
            this.action = action
            putExtra(HabitNudgeScheduler.EXTRA_HABIT_ID, habitId)
            putExtra(HabitNudgeScheduler.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context, (habitId * 10 + requestCode).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showCheckinPrompt(windowId: Long, rangeLabel: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_CHECKIN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Check-in")
            .setContentText("What did you do $rangeLabel?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()
        notificationManager.notify((windowId + 30_000).toInt(), notification)
    }

    fun showSpendGuideMorning(safeAmount: Double) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SPEND)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Safe today")
            .setContentText("You can spend about ${"%.0f".format(safeAmount)} today")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()
        notificationManager.notify(9001, notification)
    }

    fun showAiInsight(title: String, message: String, notificationId: Int = 9000) {
        val notification = NotificationCompat.Builder(context, CHANNEL_AI)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityPendingIntent())
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun buildMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when you approach or exceed a budget category limit"
                },
                NotificationChannel(CHANNEL_DEBT, "Debt Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders for upcoming debt repayment deadlines"
                },
                NotificationChannel(CHANNEL_BILLS, "Bill Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders for upcoming bill due dates"
                },
                NotificationChannel(CHANNEL_TASK, "Task Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for your tasks"
                    enableVibration(true)
                    vibrationPattern = REMINDER_VIBRATION_PATTERN
                },
                NotificationChannel(CHANNEL_ROUTINE, "Daily Routine", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for class and routine blocks"
                    enableVibration(true)
                    vibrationPattern = REMINDER_VIBRATION_PATTERN
                },
                NotificationChannel(CHANNEL_LEAVE, "Leave by", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders to leave for events and tasks"
                    enableVibration(true)
                    vibrationPattern = REMINDER_VIBRATION_PATTERN
                },
                NotificationChannel(CHANNEL_PLAN, "Plan", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = REMINDER_VIBRATION_PATTERN
                },
                NotificationChannel(CHANNEL_NUDGES, "Habits", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_CHECKIN, "Check-in", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SPEND, "Spend", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_JOBS, "Jobs", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_AI, "AI Insights", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Weekly AI financial insights and tips"
                }
            )
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }
}
