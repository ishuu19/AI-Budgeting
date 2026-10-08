package com.ledgerai.app.data.insight

import com.ledgerai.app.domain.insight.BehaviourInsight
import com.ledgerai.app.domain.insight.InsightLens
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object InsightEngine {

    fun rankForHome(insights: List<BehaviourInsight>, limit: Int = 3): List<BehaviourInsight> {
        if (insights.isEmpty()) return emptyList()
        val byLens = insights
            .groupBy { it.lens }
            .mapValues { (_, list) -> list.maxBy { it.score } }
            .values
            .sortedByDescending { it.score }
        val win = byLens.firstOrNull { it.lens == InsightLens.WIN }
        val rest = byLens.filter { it.lens != InsightLens.WIN }
        val picked = mutableListOf<BehaviourInsight>()
        if (win != null && limit > 1) picked += win
        for (item in rest) {
            if (picked.size >= limit) break
            picked += item
        }
        if (picked.size < limit && win != null && win !in picked) picked += win
        return picked.take(limit)
    }

    fun detect(transactions: List<Transaction>, today: LocalDate = LocalDate.now()): List<BehaviourInsight> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE && it.deletedAt == null }
        if (expenses.size < 5) return emptyList()
        return listOfNotNull(
            weekend(expenses),
            lateNight(expenses, today),
            smallTickets(expenses),
            merchantRepeat(expenses),
            pace(expenses, today),
            noSpendWin(expenses, today)
        )
    }

    fun weekend(expenses: List<Transaction>): BehaviourInsight? {
        val byDay = expenses.groupBy { it.date }
        if (byDay.size < 14) return null
        val weekend = mutableListOf<Double>()
        val weekday = mutableListOf<Double>()
        byDay.forEach { (day, txs) ->
            val sum = txs.sumOf { it.amount }
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) weekend += sum
            else weekday += sum
        }
        if (weekend.size < 4 || weekday.size < 8) return null
        val wEnd = weekend.average()
        val wDay = weekday.average().coerceAtLeast(1.0)
        val ratio = wEnd / wDay
        if (ratio < 1.5) return null
        return BehaviourInsight(
            id = "weekend-effect",
            lens = InsightLens.RHYTHM,
            headline = "Weekends average ${"%.1f".format(ratio)}× a weekday.",
            action = "Set a weekend limit",
            impact = (ratio - 1.0).coerceIn(0.2, 3.0),
            confidence = (byDay.size / 42.0).coerceIn(0.4, 1.0),
            supportingCount = weekend.size
        )
    }

    fun lateNight(expenses: List<Transaction>, today: LocalDate): BehaviourInsight? {
        val windowStart = today.minusDays(14)
        val night = expenses.filter {
            !it.date.isBefore(windowStart) &&
                (it.createdAt.hour >= 22 || it.createdAt.hour < 4)
        }
        if (night.size < 3) return null
        val share = night.sumOf { it.amount } / expenses.sumOf { it.amount }.coerceAtLeast(1.0)
        if (night.size < 3 && share < 0.15) return null
        return BehaviourInsight(
            id = "late-night",
            lens = InsightLens.TRIGGERS,
            headline = "${night.size} purchases landed after 10 pm in two weeks.",
            action = "Add a cool-down reminder",
            impact = share.coerceIn(0.15, 1.5),
            confidence = (night.size / 6.0).coerceIn(0.4, 1.0),
            urgency = 1.1,
            supportingCount = night.size
        )
    }

    fun smallTickets(expenses: List<Transaction>): BehaviourInsight? {
        val small = expenses.filter { it.amount in 0.01..15.0 }
        if (small.size < 5) return null
        val total = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
        val share = small.sumOf { it.amount } / total
        if (share < 0.15) return null
        return BehaviourInsight(
            id = "small-ticket",
            lens = InsightLens.LEAKS,
            headline = "Purchases under 15 add up to ${"%.0f".format(share * 100)}% of spending.",
            action = "Review small purchases",
            impact = share.coerceIn(0.15, 1.0),
            confidence = (small.size / 12.0).coerceIn(0.4, 1.0),
            supportingCount = small.size
        )
    }

    fun merchantRepeat(expenses: List<Transaction>): BehaviourInsight? {
        val monthStart = expenses.maxOfOrNull { it.date }?.withDayOfMonth(1) ?: return null
        val month = expenses.filter { !it.date.isBefore(monthStart) && it.merchant.isNotBlank() }
        val top = month.groupBy { it.merchant.trim().lowercase() }
            .mapValues { (_, txs) -> txs }
            .maxByOrNull { it.value.size }
            ?: return null
        if (top.value.size < 8) return null
        val annual = top.value.sumOf { it.amount } * 12
        return BehaviourInsight(
            id = "merchant-${top.key}",
            lens = InsightLens.LEAKS,
            headline = "${top.value.first().merchant} shows up ${top.value.size} times. About ${"%.0f".format(annual)} a year.",
            action = "Set a merchant cap",
            impact = (annual / 1000.0).coerceIn(0.2, 2.0),
            confidence = 0.8,
            supportingCount = top.value.size
        )
    }

    fun pace(expenses: List<Transaction>, today: LocalDate): BehaviourInsight? {
        val recentStart = today.minusDays(6)
        val priorStart = today.minusDays(13)
        val recent = expenses.filter { !it.date.isBefore(recentStart) && !it.date.isAfter(today) }
        val prior = expenses.filter { !it.date.isBefore(priorStart) && it.date.isBefore(recentStart) }
        if (recent.size < 3 || prior.isEmpty()) return null
        val recentSum = recent.sumOf { it.amount }
        val priorSum = prior.sumOf { it.amount }.coerceAtLeast(1.0)
        val ratio = recentSum / priorSum
        if (ratio < 1.25) return null
        return BehaviourInsight(
            id = "pace",
            lens = InsightLens.DISCIPLINE,
            headline = "The last 7 days ran ${"%.1f".format(ratio)}× the week before.",
            action = "Check today's allowance",
            impact = (ratio - 1.0).coerceIn(0.15, 1.5),
            confidence = (recent.size / 8.0).coerceIn(0.4, 1.0),
            urgency = 1.3,
            supportingCount = recent.size
        )
    }

    fun noSpendWin(expenses: List<Transaction>, today: LocalDate): BehaviourInsight? {
        var streak = 0
        var cursor = today
        val days = expenses.map { it.date }.toSet()
        while (streak < 14 && !days.contains(cursor) && ChronoUnit.DAYS.between(cursor, today) < 30) {
            if (expenses.none { it.date == cursor }) streak++
            cursor = cursor.minusDays(1)
            if (abs(ChronoUnit.DAYS.between(cursor, today)) > 40) break
        }
        if (streak < 2) return null
        return BehaviourInsight(
            id = "no-spend-streak",
            lens = InsightLens.WIN,
            headline = "$streak days without a recorded purchase.",
            action = "Keep the streak",
            impact = 0.4,
            confidence = 0.9,
            supportingCount = streak
        )
    }

    fun fixedLoad(monthlyBills: Double, monthlyIncome: Double): BehaviourInsight? {
        if (monthlyIncome <= 0) return null
        val ratio = monthlyBills / monthlyIncome
        if (ratio < 0.5) return null
        val alert = ratio >= 0.65
        return BehaviourInsight(
            id = "fixed-load",
            lens = InsightLens.RESILIENCE,
            headline = "Bills take ${"%.0f".format(ratio * 100)}% of income.",
            action = "Review bills",
            impact = ratio.coerceIn(0.3, 1.5),
            confidence = 0.85,
            urgency = if (alert) 1.4 else 1.0,
            supportingCount = 5
        )
    }

    @Suppress("unused")
    private val discretionary = setOf(
        TransactionCategory.FOOD,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.SHOPPING
    )
}
