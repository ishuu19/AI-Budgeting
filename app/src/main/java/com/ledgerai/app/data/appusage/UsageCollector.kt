package com.ledgerai.app.data.appusage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads foreground time per app from Android's usage events and stores one row per app per local day.
 * Needs the Usage Access special permission; with it off, [hasAccess] is false and nothing is read or faked.
 * A session that crosses midnight is split at midnight. Device only.
 */
@Singleton
class UsageCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppUsageDao,
) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Re-reads [daysBack] days up to now. Today is rewritten each run, finished days are final. */
    suspend fun collect(daysBack: Int = 2, zone: ZoneId = ZoneId.systemDefault()) {
        if (!hasAccess()) return
        val today = LocalDate.now(zone)
        for (offset in 0 until daysBack) {
            val day = today.minusDays(offset.toLong())
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
            val rows = totals(from, to).map { (pkg, t) ->
                AppUsageDayEntity(day, pkg, labelOf(pkg), t.first, t.second)
            }
            if (rows.isNotEmpty()) dao.upsertAll(rows)
        }
        dao.pruneBefore(today.minusDays(RETENTION_DAYS))
    }

    /** package -> (foreground ms, launches) inside [from, to). */
    private fun totals(from: Long, to: Long): Map<String, Pair<Long, Int>> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(from, to)
        val started = HashMap<String, Long>()
        val ms = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        val event = UsageEvents.Event()
        @Suppress("DEPRECATION")
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    started[pkg] = event.timeStamp
                    launches[pkg] = (launches[pkg] ?: 0) + 1
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // A pause with no resume in this window means the app was already open at the start.
                    val begin = started.remove(pkg) ?: from
                    ms[pkg] = (ms[pkg] ?: 0L) + (event.timeStamp - begin).coerceAtLeast(0)
                }
            }
        }
        started.forEach { (pkg, begin) -> ms[pkg] = (ms[pkg] ?: 0L) + (to - begin).coerceAtLeast(0) }
        return ms.filter { (pkg, t) -> t >= MIN_MS && isUserApp(pkg) }
            .mapValues { (pkg, t) -> t to (launches[pkg] ?: 0) }
    }

    /** Apps with a launcher entry. This leaves out the keyboard, system UI and background services. */
    private fun isUserApp(pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null && pkg != LAUNCHER_HINT

    private fun labelOf(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private companion object {
        const val RETENTION_DAYS = 400L
        const val MIN_MS = 1_000L
        const val LAUNCHER_HINT = "com.android.launcher"
    }
}
