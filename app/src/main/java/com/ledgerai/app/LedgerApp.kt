package com.ledgerai.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.di.DatabaseSeeder
import com.ledgerai.app.worker.BillReminderWorker
import com.ledgerai.app.worker.BudgetCheckWorker
import com.ledgerai.app.worker.InsightDailyWorker
import com.ledgerai.app.worker.QuoteDailyWorker
import com.ledgerai.app.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        databaseSeeder.seedIfEmpty()
        BudgetCheckWorker.schedule(this)
        BillReminderWorker.schedule(this)
        QuoteDailyWorker.schedule(this)
        InsightDailyWorker.schedule(this)
        appScope.launch {
            runCatching { quoteRepository.persistForWidgetRemote() }
            userSession.userInfo
                .map { it.hasRemoteUser }
                .distinctUntilChanged()
                .collect { remote ->
                    if (remote) SyncWorker.schedule(this@LedgerApp)
                    else SyncWorker.cancel(this@LedgerApp)
                }
        }
    }
}
