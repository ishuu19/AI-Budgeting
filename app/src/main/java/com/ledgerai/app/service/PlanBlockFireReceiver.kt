package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlanBlockFireReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationService: NotificationService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlanBlockScheduler.ACTION_FIRE) return
        val blockId = intent.getLongExtra(PlanBlockScheduler.EXTRA_BLOCK_ID, 0L)
        val title = intent.getStringExtra(PlanBlockScheduler.EXTRA_TITLE).orEmpty()
        if (blockId == 0L) return
        notificationService.showPlanBlockStart(blockId, title)
        context.startForegroundService(
            Intent(context, FocusForegroundService::class.java).apply {
                action = FocusForegroundService.ACTION_START
                putExtra(FocusForegroundService.EXTRA_BLOCK_ID, blockId)
                putExtra(FocusForegroundService.EXTRA_TOPIC, title)
            }
        )
    }
}
