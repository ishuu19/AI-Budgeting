package com.ledgerai.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerai.app.di.DatabaseSeeder
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.worker.BudgetCheckWorker
import com.ledgerai.app.worker.QuoteDailyWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LedgerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var quoteRepository: QuoteRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        databaseSeeder.seedIfEmpty()
        quoteRepository.persistForWidget()
        BudgetCheckWorker.schedule(this)
        QuoteDailyWorker.schedule(this)
    }
}
