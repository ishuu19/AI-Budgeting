package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ledgerai.app.MainActivity
import com.ledgerai.app.data.local.room.HabitDao
import com.ledgerai.app.data.repository.HabitRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HabitNudgeActionReceiver : BroadcastReceiver() {

    @Inject lateinit var habitRepo: HabitRepository
    @Inject lateinit var habitDao: HabitDao
    @Inject lateinit var habitNudgeScheduler: HabitNudgeScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(HabitNudgeScheduler.EXTRA_HABIT_ID, 0L)
        val title = intent.getStringExtra(HabitNudgeScheduler.EXTRA_TITLE).orEmpty()
        when (intent.action) {
            ACTION_START -> {
                habitNudgeScheduler.cancelForHabit(habitId)
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(MainActivity.EXTRA_OPEN_FOCUS_BLOCK_ID, -habitId)
                    putExtra(MainActivity.EXTRA_FOCUS_TOPIC, title)
                }
                context.startActivity(launch)
            }
            ACTION_SNOOZE -> habitNudgeScheduler.snooze(habitId, title, 5)
            ACTION_SKIP -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    habitRepo.logOutcome(habitId, com.ledgerai.app.domain.model.HabitOutcome.SKIPPED)
                    habitNudgeScheduler.cancelForHabit(habitId)
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "com.ledgerai.app.action.HABIT_NUDGE_START"
        const val ACTION_SNOOZE = "com.ledgerai.app.action.HABIT_NUDGE_SNOOZE"
        const val ACTION_SKIP = "com.ledgerai.app.action.HABIT_NUDGE_SKIP"
    }
}
