package com.ledgerai.app.widget

import android.content.Context
import android.content.Intent
import com.ledgerai.app.MainActivity
import com.ledgerai.app.service.FocusForegroundService
import com.ledgerai.app.service.WidgetVoiceCaptureActivity

object WidgetActions {
    const val EXTRA_OPEN_VOICE = "open_voice_record"
    const val EXTRA_VOICE_TEXT = "voice_text"
    const val EXTRA_OPEN_CALENDAR = "open_calendar"
    const val EXTRA_OPEN_TODAY = "open_spend_today"
    const val EXTRA_ADD_TRANSACTION = "add_transaction"
    const val EXTRA_OPEN_TASKS = "open_tasks"
    const val EXTRA_OPEN_NOTES = "open_notes"
    const val EXTRA_OPEN_BILLS = "open_bills"
    const val EXTRA_OPEN_SEARCH = "open_search"
    const val EXTRA_OPEN_DAY = "open_today"
    const val EXTRA_OPEN_SPEND_LIST = "open_spend_list"
    const val EXTRA_OPEN_MONEY = "open_money"
    const val EXTRA_EVENT_ID = "open_event_id"
    const val EXTRA_PLAN_BLOCK_ID = "open_plan_block_id"
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

    /** Opens the voice page and shows the suggestion for these words. */
    fun openVoiceReview(context: Context, text: String) =
        main(context) {
            putExtra(EXTRA_OPEN_VOICE, true)
            putExtra(EXTRA_VOICE_TEXT, text)
        }

    fun openCalendar(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_CALENDAR, true) }

    /** The full-day screen. */
    fun openToday(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_DAY, true) }

    fun openSearch(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_SEARCH, true) }

    /** Money → Spend, the list behind "spent today". */
    fun openSpendList(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_SPEND_LIST, true) }

    /** Money overview, the month total. */
    fun openMoney(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_MONEY, true) }

    /** Opens that calendar item. */
    fun openEvent(context: Context, masterId: Long) =
        main(context) { putExtra(EXTRA_EVENT_ID, masterId) }

    /** Opens that plan block on the calendar. */
    fun openPlanBlock(context: Context, blockId: Long) =
        main(context) { putExtra(EXTRA_PLAN_BLOCK_ID, blockId) }

    fun openSpendToday(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_TODAY, true) }

    /** Opens Money → Spend with the add-transaction sheet. */
    fun openAddTransaction(context: Context) =
        main(context) { putExtra(EXTRA_ADD_TRANSACTION, true) }

    fun openTasks(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_TASKS, true) }

    fun openNotes(context: Context) =
        main(context) { putExtra(EXTRA_OPEN_NOTES, true) }

    /** A small note field over the home screen. It does not open the app. */
    fun openNoteComposer(context: Context): Intent =
        Intent(context, WidgetNoteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

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
