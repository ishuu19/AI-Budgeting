package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LeaveByFireReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationService: NotificationService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LeaveByScheduler.ACTION_FIRE) return
        val title = intent.getStringExtra(LeaveByScheduler.EXTRA_TITLE).orEmpty()
        val place = intent.getStringExtra(LeaveByScheduler.EXTRA_PLACE).orEmpty()
        val ruleId = intent.getLongExtra(LeaveByScheduler.EXTRA_RULE_ID, 0L)
        if (ruleId == 0L) return
        notificationService.showLeaveByReminder(
            eventTitle = title.ifBlank { "Event" },
            placeLabel = place,
            notificationId = ruleId.toInt()
        )
    }
}
