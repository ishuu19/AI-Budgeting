package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fires a debt reminder while the app is closed. */
@AndroidEntryPoint
class DebtReminderFireReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationService: NotificationService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val debtId = intent.getLongExtra(EXTRA_DEBT_ID, -1L)
        val friendName = intent.getStringExtra(EXTRA_FRIEND) ?: return
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 0)
        if (debtId < 0L) return
        notificationService.showDebtReminder(
            friendName = friendName,
            amount = amount,
            daysLeft = daysLeft,
            notificationId = (debtId * 10 + daysLeft).toInt()
        )
    }

    companion object {
        const val ACTION_FIRE = "com.ledgerai.app.action.DEBT_REMINDER_FIRE"
        const val EXTRA_DEBT_ID = "debt_id"
        const val EXTRA_FRIEND = "friend_name"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_DAYS_LEFT = "days_left"
        const val EXTRA_INDEX = "index"
    }
}
