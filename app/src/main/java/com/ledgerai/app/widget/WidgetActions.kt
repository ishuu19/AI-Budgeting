package com.ledgerai.app.widget

import android.content.Context
import android.content.Intent
import com.ledgerai.app.MainActivity
import com.ledgerai.app.service.FocusForegroundService
import com.ledgerai.app.service.WidgetVoiceCaptureActivity

object WidgetActions {
    const val EXTRA_OPEN_VOICE = "open_voice_record"
    const val EXTRA_OPEN_CALENDAR = "open_calendar"
    const val EXTRA_OPEN_TODAY = "open_spend_today"
    const val EXTRA_OPEN_TASKS = "open_tasks"
    const val EXTRA_OPEN_BILLS = "open_bills"
    const val EXTRA_LIFE_TAB = "open_life_tab"
    const val LIFE_TAB_LOG = "log"
    const val LIFE_TAB_JOBS = "jobs"
    const val LIFE_TAB_PLAN = "plan"

    fun main(context: Context, configure: Intent.() -> Unit = {}): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }

    fun openVoice(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_VOICE, true) }

    fun openCalendar(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_CALENDAR, true) }

    fun openSpendToday(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_TODAY, true) }

    fun openTasks(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_TASKS, true) }

    fun openBills(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_BILLS, true) }

    fun openLifeLog(context: Context) =
        main(context) {
            putExtra(EXTRA_LIFE_TAB, LIFE_TAB_LOG)
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }

    fun openJobs(context: Context) =
        main(context) {
            putExtra(EXTRA_LIFE_TAB, LIFE_TAB_JOBS)
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }

    fun voiceCapture(context: Context): Intent =
        Intent(context, WidgetVoiceCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun startFocus(context: Context, blockId: Long, topic: String): Intent =
        Intent(context, FocusForegroundService::class.java).apply {
            action = FocusForegroundService.ACTION_START
            putExtra(FocusForegroundService.EXTRA_BLOCK_ID, blockId)
            putExtra(FocusForegroundService.EXTRA_TOPIC, topic)
        }

    fun openFocusScreen(context: Context, blockId: Long, topic: String): Intent =
        main(context) {
            putExtra(MainActivity.EXTRA_OPEN_FOCUS_BLOCK_ID, blockId)
            putExtra(MainActivity.EXTRA_FOCUS_TOPIC, topic)
        }
}
