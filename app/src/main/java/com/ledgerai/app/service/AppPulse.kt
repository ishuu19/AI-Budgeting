package com.ledgerai.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ledgerai.app.widget.WidgetRefresh
import com.ledgerai.app.worker.DueChecks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wakes every 15 minutes with the app closed. Refreshes the home widget and runs
 * due notifications. WorkManager's periodic jobs were waiting until the next launch.
 */
object AppPulse {
    const val ACTION = "com.ledgerai.app.action.APP_PULSE"
    private const val INTERVAL_MS = 15 * 60 * 1000L
    private const val REQUEST = 910_001

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS
        val pending = pending(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not allowed; widget pulse will wait for the next app open", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, AppPulseReceiver::class.java).apply { action = ACTION }
        return PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val TAG = "AppPulse"
}

@AndroidEntryPoint
class AppPulseReceiver : BroadcastReceiver() {

    @Inject lateinit var due: DueChecks

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppPulse.ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRefresh.refreshAll(app)
                due.runAll()
            } catch (e: Exception) {
                Log.w("AppPulse", "Pulse failed", e)
            } finally {
                AppPulse.schedule(app)
                pending.finish()
            }
        }
    }
}
