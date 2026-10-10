package com.ledgerai.app.widget

import android.content.Context
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.domain.model.PlanBlockStatus
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object WidgetStateBuilder {

    private fun scheduleMark(title: String, kind: CalendarEventKind?): String {
        val t = title.lowercase()
        return when {
            kind == CalendarEventKind.ALARM || "alarm" in t -> "⏰"
            kind == CalendarEventKind.EXAM || "exam" in t -> "📝"
            kind == CalendarEventKind.CLASS || "lecture" in t || "class" in t -> "💻"
            "meet" in t -> "🤝"
            "train" in t || "football" in t || "gym" in t || "sport" in t -> "⚽"
            kind == CalendarEventKind.TASK -> "✓"
            else -> "•"
        }
    }

    fun load(context: Context): WidgetPayload = runBlocking(Dispatchers.IO) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        build(context, ep)
    }

    private suspend fun build(context: Context, ep: WidgetEntryPoint): WidgetPayload {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val privateMode = WidgetPrefs.privateMode(context)
        val symbol = runCatching { ep.userPreferences().currencySymbol.first() }
            .getOrElse { BuildConfig.DEFAULT_CURRENCY_SYMBOL }
        val headerFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
        val timeFmt = DateTimeFormatter.ofPattern("hh:mm a")

        var safeAmount = 0.0
        var spentToday = 0.0
        var spentMonth = 0.0
        var guideProgress = 0f
        var overGuide = false
        var nearGuide = false
        var monthPct = 0

        try {
            val guide = ep.spendGuideRepository().computeTodayGuide(today)
            safeAmount = guide.guideAmount.coerceAtLeast(0.0)
            val txs = ep.transactionRepository().getTransactionsForMonth(today.year, today.monthValue).first()
            val expenses = txs.filter { it.type == TransactionType.EXPENSE }
            spentToday = expenses.filter { it.date == today }.sumOf { it.amount }
            spentMonth = expenses.sumOf { it.amount }
            val denom = safeAmount.coerceAtLeast(1.0)
            guideProgress = (spentToday / denom).toFloat().coerceIn(0f, 1f)
            overGuide = guide.status == SpendGuideStatus.OVER || spentToday > safeAmount
            nearGuide = guide.status == SpendGuideStatus.NEAR || guideProgress >= 0.8f

            val budgets = ep.budgetRepository().getBudgetsForMonth(today.monthValue, today.year).first()
            val limit = budgets.sumOf { it.monthlyLimit }
            val spent = budgets.sumOf { it.spent }
            monthPct = if (limit > 0) ((spent / limit) * 100).roundToInt().coerceIn(0, 999) else 0
        } catch (_: Exception) {
        }

        val nextItems = mutableListOf<Triple<LocalDateTime, String, String>>()
        try {
            ep.calendarRepository().listRange(today, today)
                .filter { !(it.kind == CalendarEventKind.TASK && it.isCompleted) && it.isEnabled }
                .forEach { e -> nextItems += Triple(e.startAt, e.title, scheduleMark(e.title, e.kind)) }
            ep.planRepository().observeBlocks().first()
                .filter {
                    it.status == PlanBlockStatus.SCHEDULED &&
                        it.startAt.toLocalDate() == today &&
                        !it.startAt.isBefore(now.minusMinutes(30))
                }
                .forEach { b -> nextItems += Triple(b.startAt, b.title, scheduleMark(b.title, null)) }
        } catch (_: Exception) {
        }

        val sortedNext = nextItems
            .distinct()
            .sortedBy { it.first }
            .mapIndexed { index, item -> WidgetNextItem(timeFmt.format(item.first), item.second, "", index.toLong()) }

        val nextLine = sortedNext.firstOrNull()?.let { "${it.time}  ${it.title}" }
            ?: "Clear day"

        var tasksDue = 0
        val tasksToday = mutableListOf<WidgetTaskLine>()
        try {
            val tasks = ep.calendarRepository().observeTasks().first()
            val due = tasks.filter { !it.isCompleted && it.hasDate && !it.startAt.toLocalDate().isAfter(today) }
            tasksDue = due.size
            due.sortedWith(compareBy({ it.startAt }, { it.title }))
                .take(4)
                .forEach { t -> tasksToday += WidgetTaskLine(t.id, t.title, t.isCompleted) }
        } catch (_: Exception) {
        }

        var billsDue = 0
        try {
            val bills = ep.billRepository().getActiveBills().first()
            billsDue = bills.count { !it.nextDueDate.isAfter(today.plusDays(7)) }
        } catch (_: Exception) {
        }

        var logGaps = 0
        try {
            ep.lifeLogRepository().markExpiredGaps(now)
            val windows = ep.lifeLogRepository().observeDay(today).first()
            logGaps = windows.count {
                it.state == CheckinWindowState.GAP || it.state == CheckinWindowState.PENDING
            }
        } catch (_: Exception) {
        }

        var jobsWeek = 0
        try {
            val apps = ep.jobRepository().observeAll().first()
            jobsWeek = ep.jobRepository().stats(apps).appliedThisWeek
        } catch (_: Exception) {
        }

        val quoteLine = runCatching {
            val q = ep.quoteRepository().readWidgetQuote()
            q.text.take(72).let { if (q.author.isNotBlank()) "$it — ${q.author}" else it }
        }.getOrDefault("")

        val habits = mutableListOf<WidgetHabitDot>()
        try {
            ep.habitRepository().observeHabits().first().take(6).forEach { h ->
                habits += WidgetHabitDot(h.id, h.title, false)
            }
        } catch (_: Exception) {
        }

        var focusTitle = "Focus"
        var focusCountdown: String? = null
        try {
            val blocks = ep.planRepository().observeBlocks().first()
            val next = blocks
                .filter { it.status == PlanBlockStatus.SCHEDULED && it.startAt.isAfter(now) }
                .minByOrNull { it.startAt }
            if (next != null) {
                focusTitle = next.title
                val mins = java.time.Duration.between(now, next.startAt).toMinutes()
                if (mins in 0..240) focusCountdown = "${mins}m"
            }
        } catch (_: Exception) {
        }

        fun money(v: Double): String =
            if (privateMode) "•••" else "$symbol${v.roundToInt()}"

        return WidgetPayload(
            dateHeader = headerFmt.format(today),
            safeTodayLabel = "Safe today",
            safeTodayAmount = money(safeAmount),
            spentTodayLabel = if (privateMode) "••• spent" else "${money(spentToday)} spent",
            spentTodayAmount = if (privateMode) "•••" else "$symbol${"%.2f".format(spentToday)}",
            spentMonthAmount = if (privateMode) "•••" else "$symbol${"%.2f".format(spentMonth)}",
            guideProgress = if (overGuide) 1f else guideProgress,
            overGuide = overGuide,
            nearGuide = nearGuide && !overGuide,
            monthBudgetPercent = monthPct,
            nextUp = sortedNext.map {
                if (privateMode) it.copy(title = "•••") else it
            },
            nextOneLine = if (privateMode && sortedNext.isNotEmpty()) "Next •••" else nextLine,
            tasksDue = tasksDue,
            billsDue = billsDue,
            logGaps = logGaps,
            jobsThisWeek = jobsWeek,
            quoteLine = quoteLine,
            tasksToday = tasksToday.map {
                if (privateMode) it.copy(title = "•••") else it
            },
            habitsToday = habits.map {
                if (privateMode) it.copy(title = "•••") else it
            },
            habitStreak = 0,
            focusTitle = if (privateMode) "•••" else focusTitle,
            focusCountdown = focusCountdown,
            privateMode = privateMode,
            builtAtMillis = System.currentTimeMillis()
        )
    }
}
