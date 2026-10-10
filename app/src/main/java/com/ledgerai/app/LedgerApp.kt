package com.ledgerai.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerai.app.data.ai.LocalParseModel
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.widget.WidgetRefresh
import com.ledgerai.app.di.DatabaseSeeder
import com.ledgerai.app.worker.BillReminderWorker
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
import kotlinx.coroutines.flow.collectLatest
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
    @Inject lateinit var localParseModel: LocalParseModel

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
        WidgetRefreshWorker.schedule(this)
        watchWidgetData()
        appScope.launch { localParseModel.prepare() }
        appScope.launch {
            runCatching { quoteRepository.persistForWidgetRemote() }
            userSession.userInfo
                .map { it.hasRemoteUser }
                .distinctUntilChanged()
                .collect { remote ->
                    if (remote) {
                        SyncWorker.schedule(this@LedgerApp)
                        registerSyncOnAvailable()
                    } else {
                        unregisterSyncOnAvailable()
                        SyncWorker.cancel(this@LedgerApp)
                    }
                }
        }
    }

    /** Reloads the home widget whenever today's events or spending change. */
    private fun watchWidgetData() {
        appScope.launch(Dispatchers.IO) {
            launch {
                calendarRepository.observeAll()
                    .map { rows -> rows.joinToString { "${it.id}|${it.title}|${it.startAt}|${it.isCompleted}|${it.isEnabled}" } }
                    .distinctUntilChanged()
                    .debounce(400)
                    .collectLatest { WidgetRefresh.refreshAll(this@LedgerApp) }
            }
            launch {
                transactionRepository.getAllTransactions()
                    .map { rows -> rows.joinToString { "${it.id}|${it.amount}|${it.date}|${it.type}" } }
                    .distinctUntilChanged()
                    .debounce(400)
                    .collectLatest { WidgetRefresh.refreshAll(this@LedgerApp) }
            }
        }
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
