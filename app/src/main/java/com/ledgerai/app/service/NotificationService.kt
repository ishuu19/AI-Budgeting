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
        const val CHANNEL_AI = "ai_insights"
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
                NotificationChannel(CHANNEL_AI, "AI Insights", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Weekly AI financial insights and tips"
                }
            )
            channels.forEach { notificationManager.createNotificationChannel(it) }
        }
    }
}
