package com.ledgerai.app.data.insight

import com.ledgerai.app.data.ai.InsightDto
import com.ledgerai.app.data.ai.SOURCE_RULES
import com.ledgerai.app.domain.insight.BehaviourInsight
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The numbers the daily insight is built from. */
data class InsightInputs(
    val today: LocalDate,
    val transactions: List<Transaction>,
    val budgets: List<Budget> = emptyList(),
    val bills: List<Bill> = emptyList(),
)

/**
 * Deterministic daily insight. Picks the most important finding and phrases it from a template.
 * No network. The cloud may reword the result, but never changes a number.
 */
object InsightRules {

    private fun defaultFmt(v: Double) = String.format(Locale.US, "%,.0f", v)

    /** Advice sentence for each detector id in [InsightEngine]. */
    internal fun adviceFor(id: String): String = when {
        id == "weekend-effect" -> "Set a weekend limit before Friday so Saturday does not decide your month."
        id == "late-night" -> "Late purchases are usually impulse buys. Wait until morning before paying."
        id == "small-ticket" -> "Small buys add up fast. Pick one to skip this week."
        id.startsWith("merchant-") -> "Put a monthly cap on this merchant or move it to a planned bill."
        id == "pace" -> "Check today's allowance and skip one non-essential purchase."
        id == "no-spend-streak" -> "Nice run. Keep the streak going one more day."
        id == "fixed-load" -> "Bills are a big share of income. Review which ones you can cut or renegotiate."
        else -> "Review your top category before the next purchase."
    }

    private fun severityOf(i: BehaviourInsight): String = when {
        i.id == "pace" || i.id == "late-night" || i.id == "fixed-load" -> "watch"
        i.id == "no-spend-streak" -> "info"
        else -> "info"
    }

    private fun titleOf(i: BehaviourInsight): String = when {
        i.id == "weekend-effect" -> "Weekend spending"
        i.id == "late-night" -> "Late-night buys"
        i.id == "small-ticket" -> "Small purchases"
        i.id.startsWith("merchant-") -> "Repeat merchant"
        i.id == "pace" -> "Spending pace"
        i.id == "no-spend-streak" -> "Nice streak"
        i.id == "fixed-load" -> "Fixed costs"
        else -> "Insight"
    }

    fun dailyInsight(input: InsightInputs, fmt: (Double) -> String = ::defaultFmt): InsightDto {
        val today = input.today
        val first = today.withDayOfMonth(1)
        val monthSpend = input.transactions.filter {
            it.type == TransactionType.EXPENSE && it.deletedAt == null && !it.date.isBefore(first) && !it.date.isAfter(today)
        }
        val spentBy = monthSpend.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }

        // 1. A budget that is already over.
        val over = input.budgets.filter { it.deletedAt == null && it.monthlyLimit > 0 }
            .map { it to (spentBy[it.category] ?: 0.0) }
            .filter { (b, s) -> s > b.monthlyLimit }
            .maxByOrNull { (b, s) -> s - b.monthlyLimit }
        if (over != null) {
            val (b, s) = over
            return dto(
                "Over budget", "${b.category.displayName} is over its limit by ${fmt(s - b.monthlyLimit)} (${fmt(s)} of ${fmt(b.monthlyLimit)}). Pause spending there for the rest of the month.",
                "alert", listOf("Open budget")
            )
        }

        // 2. Bills overdue or due very soon.
        val bills = input.bills.filter { it.isActive && it.deletedAt == null }
        val overdue = bills.filter { it.nextDueDate.isBefore(today) }
        if (overdue.isNotEmpty()) {
            val b = overdue.minBy { it.nextDueDate }
            return dto(
                "Overdue bill", "${b.name} (${fmt(b.amount)}) was due ${ChronoUnit.DAYS.between(b.nextDueDate, today)} days ago${if (overdue.size > 1) ", and ${overdue.size - 1} more are late" else ""}. Pay it today to avoid fees.",
                "alert", listOf("Open bills")
            )
        }
        val soon = bills.filter { !it.nextDueDate.isAfter(today.plusDays(3)) }.minByOrNull { it.nextDueDate }
        if (soon != null) {
            val days = ChronoUnit.DAYS.between(today, soon.nextDueDate)
            val whenText = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
            return dto("Bill coming up", "${soon.name} (${fmt(soon.amount)}) is due $whenText. Keep that amount free.", "watch", listOf("Open bills"))
        }

        // 3. A budget close to its limit.
        val near = input.budgets.filter { it.deletedAt == null && it.monthlyLimit > 0 }
            .map { it to (spentBy[it.category] ?: 0.0) / it.monthlyLimit * 100 }
            .filter { (b, pct) -> pct >= b.alertThreshold }
            .maxByOrNull { it.second }
        if (near != null) {
            val (b, pct) = near
            val left = b.monthlyLimit - (spentBy[b.category] ?: 0.0)
            return dto(
                "Budget nearly used", "${b.category.displayName} is at ${Math.round(pct)}% with ${fmt(left)} left and ${today.lengthOfMonth() - today.dayOfMonth} days to go. Slow down here.",
                "watch", listOf("Open budget")
            )
        }

        // 4. The strongest behaviour pattern.
        val behaviour = InsightEngine.rankForHome(InsightEngine.detect(input.transactions, today), 1).firstOrNull()
        if (behaviour != null) {
            return dto(titleOf(behaviour), "${behaviour.headline} ${adviceFor(behaviour.id)}", severityOf(behaviour), listOf(behaviour.action))
        }

        // 5. Month pace against income.
        val income = input.transactions.filter { it.type == TransactionType.INCOME && it.deletedAt == null && !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
        val spent = monthSpend.sumOf { it.amount }
        if (income > 0 && spent > income) {
            return dto("Spending over income", "You have spent ${fmt(spent)} against ${fmt(income)} income this month. Hold off on extras until the next payday.", "watch", listOf("Review spending"))
        }
        if (spent > 0) {
            val top = spentBy.maxByOrNull { it.value }!!
            val share = Math.round(top.value / spent * 100)
            return dto("Top category", "${top.key.displayName} takes $share% of this month's spending (${fmt(top.value)}). Check it before adding more.", "info", listOf("Add by voice"))
        }
        return dto("Track today", "Log a few expenses so LedgerAI can spot patterns tomorrow.", "info", listOf("Add by voice"))
    }

    private fun dto(title: String, body: String, severity: String, actions: List<String>) =
        InsightDto(title = title, body = body, severity = severity, actions = actions, source = SOURCE_RULES)
}

/** Deterministic forecast: recurring bills plus average weekday spend, against the income pattern. */
object ForecastRules {

    private fun defaultFmt(v: Double) = String.format(Locale.US, "%,.0f", v)

    /** Monthly cost of a bill. */
    fun monthlyEquivalent(bill: Bill): Double = when (bill.frequency) {
        BillFrequency.WEEKLY -> bill.amount * 52.0 / 12.0
        BillFrequency.MONTHLY -> bill.amount
        BillFrequency.QUARTERLY -> bill.amount / 3.0
        BillFrequency.YEARLY -> bill.amount / 12.0
    }

    /** Average spend for each weekday over up to [windowDays] of history, ignoring recurring-flagged rows. */
    fun weekdayAverages(transactions: List<Transaction>, today: LocalDate, windowDays: Int = 90): Map<DayOfWeek, Double> {
        val spend = transactions.filter { it.type == TransactionType.EXPENSE && it.deletedAt == null && !it.isRecurring && !it.date.isAfter(today) }
        if (spend.isEmpty()) return emptyMap()
        val earliest = spend.minOf { it.date }
        val start = maxOf(earliest, today.minusDays(windowDays - 1L))
        val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
        val totals = HashMap<DayOfWeek, Double>()
        val counts = HashMap<DayOfWeek, Int>()
        for (i in 0 until days) counts.merge(start.plusDays(i.toLong()).dayOfWeek, 1, Int::plus)
        spend.filter { !it.date.isBefore(start) }.forEach { totals.merge(it.date.dayOfWeek, it.amount, Double::plus) }
        val overall = totals.values.sum() / days
        return DayOfWeek.entries.associateWith { d -> (counts[d] ?: 0).let { c -> if (c == 0) overall else (totals[d] ?: 0.0) / c } }
    }

    /** Average monthly income from the income rows in the last [windowDays]. */
    fun incomePattern(transactions: List<Transaction>, today: LocalDate, windowDays: Int = 90): Double {
        val rows = transactions.filter { it.type == TransactionType.INCOME && it.deletedAt == null && !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(windowDays - 1L)) }
        if (rows.isEmpty()) return 0.0
        val span = maxOf(1.0, ChronoUnit.DAYS.between(rows.minOf { it.date }, today) + 1.0).coerceAtLeast(30.0)
        return rows.sumOf { it.amount } / (span / 30.0)
    }

    fun project(
        transactions: List<Transaction>,
        budgets: Map<com.ledgerai.app.domain.model.TransactionCategory, Double>,
        bills: List<Bill>,
        today: LocalDate,
        months: Int = 3,
        fmt: (Double) -> String = ::defaultFmt,
    ): List<FinancialForecast> {
        val weekday = weekdayAverages(transactions, today)
        val fixed = bills.filter { it.isActive && it.deletedAt == null }.sumOf(::monthlyEquivalent)
        val income = incomePattern(transactions, today)
        val budgetTotal = budgets.values.sum()
        return (1..months).map { offset ->
            val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
            val variable = (0 until first.lengthOfMonth()).sumOf { weekday[first.plusDays(it.toLong()).dayOfWeek] ?: 0.0 }
            val predicted = variable + fixed
            val ceiling = when {
                budgetTotal > 0 -> budgetTotal
                income > 0 -> income * 0.9
                else -> predicted
            }
            val risk = when {
                income > 0 && predicted > income * 0.95 -> RiskLevel.HIGH
                budgetTotal > 0 && predicted > budgetTotal * 1.1 -> RiskLevel.HIGH
                income > 0 && predicted > income * 0.8 -> RiskLevel.MEDIUM
                budgetTotal > 0 && predicted > budgetTotal * 0.9 -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }
            val incomeText = if (income > 0) " Income pattern ${fmt(income)}." else ""
            FinancialForecast(
                month = "${first.month.getDisplayName(TextStyle.FULL, Locale.US)} ${first.year}",
                predictedSpend = predicted,
                recommendedBudget = if (budgetTotal > 0) budgetTotal else minOf(predicted, ceiling),
                riskLevel = risk,
                insight = "About ${fmt(variable)} of day to day spending plus ${fmt(fixed)} of bills.$incomeText"
            )
        }
    }
}

/** One budget row with this month's spend. */
data class BudgetLine(
    val category: com.ledgerai.app.domain.model.TransactionCategory,
    val spent: Double,
    val limit: Double,
    val lastMonthSpent: Double? = null,
)

/** Rule-based budget suggestions: pace caps, reallocation from under-used budgets, trend caps. */
object BudgetAdviceRules {

    private fun defaultFmt(v: Double) = String.format(Locale.US, "%,.0f", v)

    fun advise(
        line: BudgetLine,
        others: List<BudgetLine>,
        today: LocalDate,
        fmt: (Double) -> String = ::defaultFmt,
    ): String {
        val name = line.category.displayName
        val pct = if (line.limit > 0) line.spent / line.limit * 100 else 0.0
        val dayOfMonth = today.dayOfMonth
        val daysLeft = (today.lengthOfMonth() - dayOfMonth).coerceAtLeast(0)
        val parts = mutableListOf<String>()
        // Status
        parts += when {
            line.limit <= 0 -> "$name has no limit set. Add one to track it."
            pct >= 100 -> "You are over the $name limit by ${fmt(line.spent - line.limit)} (${fmt(line.spent)} of ${fmt(line.limit)}). Pause non-essential spend here."
            pct >= 80 -> "You are at ${Math.round(pct)}% of the $name budget with ${fmt(line.limit - line.spent)} left."
            else -> "You are at ${Math.round(pct)}% of $name and on track."
        }
        // Pace cap
        if (line.limit > 0 && pct < 100 && dayOfMonth >= 5) {
            val projected = line.spent / dayOfMonth * today.lengthOfMonth()
            if (projected > line.limit) {
                val cap = (line.limit - line.spent) / daysLeft.coerceAtLeast(1)
                parts += "At this pace you reach ${fmt(projected)} by month end. Keep it under ${fmt(cap)} a day."
            }
        }
        // Trend cap
        line.lastMonthSpent?.let { last ->
            if (last > 0 && line.spent > last * 1.2 && dayOfMonth >= 10) {
                parts += "That is already ${Math.round((line.spent / last - 1) * 100)}% above last month's ${fmt(last)}."
            }
        }
        // Reallocation
        if ((pct >= 80 || line.limit <= 0) && dayOfMonth >= 10) {
            others.filter { it.category != line.category && it.limit > 0 && it.spent / it.limit < 0.5 }
                .maxByOrNull { it.limit - it.spent }?.let { donor ->
                    val movable = (donor.limit - donor.spent) * 0.5
                    if (movable >= 1.0) {
                        parts += "Consider moving up to ${fmt(movable)} from ${donor.category.displayName} (only ${Math.round(donor.spent / donor.limit * 100)}% used)."
                    }
                }
        }
        return parts.joinToString(" ")
    }
}
