package com.ledgerai.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.ledgerai.app.worker.BudgetCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receiver that reschedules WorkManager tasks after device reboot.
 * WorkManager persists work across reboots automatically starting from
 * WorkManager 2.1+, but this receiver ensures any custom scheduling is
 * also restored.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Re-enqueue periodic budget checks
            BudgetCheckWorker.schedule(context)
        }
    }
}
