package com.ledgerai.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ledgerai.app.domain.model.Habit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitNudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val offsetsMinutes = listOf(30, 25, 20, 15, 10, 5, 0)

    fun scheduleForHabit(habit: Habit) {
        cancelForHabit(habit.id)
        if (!habit.nudgeEnabled) return
        val sessionStart = nextSessionStart(habit) ?: return
        offsetsMinutes.forEachIndexed { index, offset ->
            val fireAt = sessionStart.minusMinutes(offset.toLong())
            if (fireAt.isBefore(LocalDateTime.now())) return@forEachIndexed
            scheduleOne(habit.id, habit.title, sessionStart, fireAt, index, offset)
        }
    }

    fun cancelForHabit(habitId: Long) {
        offsetsMinutes.indices.forEach { index ->
            alarmManager.cancel(pendingIntent(habitId, index, "", LocalDateTime.now(), 0))
        }
    }

    fun snooze(habitId: Long, title: String, minutes: Int = 5) {
        val fireAt = LocalDateTime.now().plusMinutes(minutes.toLong())
        scheduleOne(habitId, title, fireAt, fireAt, 99, 0)
    }

    private fun scheduleOne(
        habitId: Long,
        title: String,
        sessionStart: LocalDateTime,
        fireAt: LocalDateTime,
        index: Int,
        minutesBefore: Int
    ) {
        val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        val pending = pendingIntent(habitId, index, title, sessionStart, minutesBefore)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, pending),
                    pending
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Habit nudge skipped habitId=$habitId", e)
        }
    }

    private fun pendingIntent(
        habitId: Long,
        index: Int,
        title: String,
        sessionStart: LocalDateTime,
        minutesBefore: Int
    ): PendingIntent {
        val intent = Intent(context, HabitNudgeFireReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_INDEX, index)
            putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
            putExtra(EXTRA_SESSION_START, sessionStart.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(habitId, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(habitId: Long, index: Int): Int =
        (REQUEST_CODE_BASE + habitId * 10 + index).toInt()

    private fun nextSessionStart(habit: Habit): LocalDateTime? {
        val today = LocalDate.now()
        for (i in 0..7) {
            val day = today.plusDays(i.toLong())
            val dow = day.dayOfWeek.value
            val bit = 1 shl (dow % 7)
            if (habit.daysMask == 0 || (habit.daysMask and bit) != 0) {
                val start = LocalDateTime.of(day, habit.startTime)
                if (start.isAfter(LocalDateTime.now())) return start
            }
        }
        return LocalDateTime.of(today.plusDays(1), habit.startTime)
    }

    companion object {
        private const val TAG = "HabitNudgeScheduler"
        private const val REQUEST_CODE_BASE = 700_000L
        const val ACTION_FIRE = "com.ledgerai.app.action.HABIT_NUDGE_FIRE"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_TITLE = "habit_title"
        const val EXTRA_INDEX = "nudge_index"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"
        const val EXTRA_SESSION_START = "session_start"
    }
}
