package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HabitNudgeFireReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationService: NotificationService
    @Inject lateinit var habitNudgeScheduler: HabitNudgeScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HabitNudgeScheduler.ACTION_FIRE) return
        val habitId = intent.getLongExtra(HabitNudgeScheduler.EXTRA_HABIT_ID, 0L)
        val title = intent.getStringExtra(HabitNudgeScheduler.EXTRA_TITLE).orEmpty()
        val minutesBefore = intent.getIntExtra(HabitNudgeScheduler.EXTRA_MINUTES_BEFORE, 0)
        if (habitId == 0L) return
        val importance = when {
            minutesBefore <= 5 -> NotificationService.Companion.NudgeImportance.HIGH
            minutesBefore <= 15 -> NotificationService.Companion.NudgeImportance.DEFAULT
            else -> NotificationService.Companion.NudgeImportance.LOW
        }
        notificationService.showHabitNudge(habitId, title, minutesBefore, importance)
    }
}
