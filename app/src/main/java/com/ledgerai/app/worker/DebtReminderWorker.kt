package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgerai.app.service.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationService: NotificationService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_DEBT_ID = "debt_id"
        const val KEY_FRIEND_NAME = "friend_name"
        const val KEY_AMOUNT = "amount"
        const val KEY_DAYS_LEFT = "days_left"
    }

    override suspend fun doWork(): Result {
        val debtId = inputData.getLong(KEY_DEBT_ID, -1)
        val friendName = inputData.getString(KEY_FRIEND_NAME) ?: return Result.failure()
        val amount = inputData.getDouble(KEY_AMOUNT, 0.0)
        val daysLeft = inputData.getInt(KEY_DAYS_LEFT, 0)

        notificationService.showDebtReminder(
            friendName = friendName,
            amount = amount,
            daysLeft = daysLeft,
            notificationId = (debtId * 10 + daysLeft).toInt()
        )

        return Result.success()
    }
}
