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
    private val lifeLogRepo: LifeLogRepository,
    private val notificationService: NotificationService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        if (now.hour < 7 || now.hour >= 23) return Result.success()
        lifeLogRepo.markExpiredGaps(now)
        lifeLogRepo.ensureWindowsForDay(LocalDate.now())
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val start = now.minusHours(3)
        val label = "${fmt.format(start)}–${fmt.format(now)}"
        notificationService.showCheckinPrompt(now.toEpochSecond(java.time.ZoneOffset.UTC), label)
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
