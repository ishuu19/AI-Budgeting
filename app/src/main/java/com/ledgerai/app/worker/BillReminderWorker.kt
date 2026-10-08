package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that notifies for active bills due within [REMIND_WITHIN_DAYS] days
 * (including overdue / due today).
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val billRepo: BillRepository,
    private val notificationService: NotificationService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "periodic_bill_reminder"
        private const val REMIND_WITHIN_DAYS = 7

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BillReminderWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresCharging(false).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val bills = billRepo.getActiveBills().first()

        bills.forEach { bill ->
            val daysLeft = ChronoUnit.DAYS.between(today, bill.nextDueDate).toInt()
            if (daysLeft <= REMIND_WITHIN_DAYS) {
                notificationService.showBillReminder(
                    billName = bill.name,
                    amount = bill.amount,
                    daysLeft = daysLeft,
                    notificationId = (2_000 + bill.id).toInt()
                )
            }
        }

        return Result.success()
    }
}
