package com.ledgerai.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerai.app.data.ai.LocalParseModel
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.service.AppPulse
import com.ledgerai.app.widget.WidgetRefresh
import com.ledgerai.app.di.DatabaseSeeder
import com.ledgerai.app.worker.BillReminderWorker
import com.ledgerai.app.worker.ClipCleanupWorker
import com.ledgerai.app.worker.ClipDigestWorker
import com.ledgerai.app.worker.BudgetCheckWorker
import com.ledgerai.app.worker.CheckinWorker
import com.ledgerai.app.worker.InsightDailyWorker
import com.ledgerai.app.worker.NoteScanWorker
import com.ledgerai.app.worker.QuoteDailyWorker
import com.ledgerai.app.worker.SpendGuideMorningWorker
import com.ledgerai.app.worker.SyncWorker
import com.ledgerai.app.worker.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LedgerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var quoteRepository: QuoteRepository
    @Inject lateinit var userSession: UserSession
    @Inject lateinit var calendarRepository: CalendarRepository
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var leaveByRepository: LeaveByRepository
    @Inject lateinit var planRepository: PlanRepository
    @Inject lateinit var habitRepository: HabitRepository
    @Inject lateinit var localParseModel: LocalParseModel
    @Inject lateinit var database: com.ledgerai.app.data.local.room.LedgerDatabase
    @Inject lateinit var reminderEngine: com.ledgerai.app.service.ReminderEngine

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncNetworkCallback: ConnectivityManager.NetworkCallback? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        databaseSeeder.onApplicationStart()
        BudgetCheckWorker.schedule(this)
        BillReminderWorker.schedule(this)
        QuoteDailyWorker.schedule(this)
        InsightDailyWorker.schedule(this)
        CheckinWorker.schedule(this)
        SpendGuideMorningWorker.schedule(this)
        NoteScanWorker.schedule(this)
        ClipCleanupWorker.schedule(this)
        ClipDigestWorker.schedule(this)
        WidgetRefreshWorker.schedule(this)
        AppPulse.schedule(this)
        com.ledgerai.app.worker.AppUsageWorker.schedule(this)
        armAlarms()
        watchWidgetData()
        appScope.launch { localParseModel.prepare() }
        appScope.launch(Dispatchers.IO) { runCatching { reminderEngine.rebuildAndArm() } }
        appScope.launch {
            runCatching { quoteRepository.persistForWidgetRemote() }
            userSession.userInfo
                .map { it.hasRemoteUser }
                .distinctUntilChanged()
                .collect { remote ->
                    if (remote) {
                        SyncWorker.schedule(this@LedgerApp)
                        SyncWorker.syncNow(this@LedgerApp)
                        watchLocalChanges()
                        registerSyncOnAvailable()
                    } else {
                        unregisterSyncOnAvailable()
                        SyncWorker.cancel(this@LedgerApp)
                    }
                }
        }
    }

    /** Exact alarms survive the app being closed. Re-arm them whenever the process starts. */
    private fun armAlarms() {
        appScope.launch(Dispatchers.IO) {
            runCatching { calendarRepository.rescheduleAllAlarms() }
            runCatching { calendarRepository.rescheduleAllReminders() }
            runCatching { leaveByRepository.rescheduleAllEnabled() }
            runCatching { planRepository.rescheduleAllBlockAlarms() }
            runCatching { habitRepository.rescheduleAllNudges() }
            runCatching { WidgetRefresh.refreshAll(this@LedgerApp) }
        }
    }

    /** Reloads the home widget whenever today's events, plan or spending change. */
    private fun watchWidgetData() {
        appScope.launch(Dispatchers.IO) {
            launch {
                calendarRepository.observeAll()
                    .map { rows -> rows.joinToString { "${it.id}|${it.title}|${it.startAt}|${it.isCompleted}|${it.isEnabled}" } }
                    .distinctUntilChanged()
                    .debounce(400)
                    .collect { runCatching { WidgetRefresh.refreshAll(this@LedgerApp) } }
            }
            launch {
                planRepository.observeBlocks()
                    .map { rows -> rows.joinToString { "${it.id}|${it.title}|${it.startAt}|${it.status}" } }
                    .distinctUntilChanged()
                    .debounce(400)
                    .collect { runCatching { WidgetRefresh.refreshAll(this@LedgerApp) } }
            }
            launch {
                transactionRepository.getAllTransactions()
                    .map { rows -> rows.joinToString { "${it.id}|${it.amount}|${it.date}|${it.type}" } }
                    .distinctUntilChanged()
                    .debounce(400)
                    .collect { runCatching { WidgetRefresh.refreshAll(this@LedgerApp) } }
            }
        }
    }

    private var changeObserver: androidx.room.InvalidationTracker.Observer? = null
    private val syncHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Any change to a synced table schedules an upload within seconds. Many quick edits become one sync. */
    private fun watchLocalChanges() {
        if (changeObserver != null) return
        val tables = arrayOf(
            "transactions", "budgets", "debts", "goals", "bills", "notes", "calendar_events", "event_reminders",
            "habits", "habit_logs", "study_plans", "plan_blocks", "focus_sessions", "job_applications", "voice_history",
            "folders", "birthdays", "trips", "achievements", "wishes", "media_assets", "clips", "chat_threads",
            "chat_messages", "people", "subscriptions",
        )
        val observer = object : androidx.room.InvalidationTracker.Observer(tables) {
            override fun onInvalidated(tables: Set<String>) {
                syncHandler.removeCallbacksAndMessages(null)
                syncHandler.postDelayed({ SyncWorker.syncNow(this@LedgerApp) }, 4_000)
            }
        }
        database.invalidationTracker.addObserver(observer)
        changeObserver = observer
    }

    private fun registerSyncOnAvailable() {
        if (syncNetworkCallback != null) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                SyncWorker.syncNow(this@LedgerApp)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        syncNetworkCallback = callback
    }

    private fun unregisterSyncOnAvailable() {
        val callback = syncNetworkCallback ?: return
        val connectivity = getSystemService(ConnectivityManager::class.java)
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        syncNetworkCallback = null
    }
}
