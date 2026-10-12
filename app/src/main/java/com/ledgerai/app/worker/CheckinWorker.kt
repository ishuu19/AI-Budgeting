package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class CheckinWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val due: DueChecks
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        due.checkin()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "checkin_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckinWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
