package com.ledgerai.app.data.insight

import com.ledgerai.app.data.ai.SOURCE_RULES
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InsightRulesTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val fmt: (Double) -> String = { "$" + String.format("%.0f", it) }

    private fun exp(day: LocalDate, amount: Double, cat: TransactionCategory = TransactionCategory.FOOD, merchant: String = "", recurring: Boolean = false) =
        Transaction(amount = amount, type = TransactionType.EXPENSE, category = cat, merchant = merchant, date = day, isRecurring = recurring)

    private fun inc(day: LocalDate, amount: Double) =
        Transaction(amount = amount, type = TransactionType.INCOME, category = TransactionCategory.SALARY, date = day)

    private fun budget(cat: TransactionCategory, limit: Double, threshold: Int = 80) =
        Budget(category = cat, monthlyLimit = limit, month = 10, year = 2026, alertThreshold = threshold)

    private fun daily(days: Int, amount: Double, from: LocalDate = today.minusDays(days - 1L)) =
        (0 until days).map { exp(from.plusDays(it.toLong()), amount) }

    // ─── daily insight ───────────────────────────────────────────────────────

    @Test
    fun emptyDataAsksToTrack() {
        val dto = InsightRules.dailyInsight(InsightInputs(today, emptyList()), fmt)
        assertEquals("Track today", dto.title)
        assertEquals(SOURCE_RULES, dto.source)
    }

    @Test
    fun overBudgetIsAnAlert() {
        val txs = listOf(exp(today, 160.0, TransactionCategory.SHOPPING))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs, listOf(budget(TransactionCategory.SHOPPING, 100.0))), fmt)
        assertEquals("alert", dto.severity)
        assertTrue(dto.body!!, dto.body!!.contains("Shopping"))
        assertTrue(dto.body!!, dto.body!!.contains("\$60"))
    }

    @Test
    fun overBudgetBeatsBills() {
        val txs = listOf(exp(today, 160.0, TransactionCategory.SHOPPING))
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs, listOf(budget(TransactionCategory.SHOPPING, 100.0)), bills), fmt)
        assertEquals("Over budget", dto.title)
    }

    @Test
    fun overdueBillIsAnAlert() {
        val bills = listOf(Bill(name = "Internet", amount = 30.0, nextDueDate = today.minusDays(3)))
        val dto = InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt)
        assertEquals("Overdue bill", dto.title)
        assertTrue(dto.body!!.contains("3 days ago"))
        assertEquals("alert", dto.severity)
    }

    @Test
    fun severalOverdueBillsAreCounted() {
        val bills = listOf(
            Bill(name = "A", amount = 1.0, nextDueDate = today.minusDays(3)),
            Bill(name = "B", amount = 1.0, nextDueDate = today.minusDays(1)),
        )
        val dto = InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt)
        assertTrue(dto.body!!.contains("1 more"))
    }

    @Test
    fun billDueTomorrowIsAWatch() {
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today.plusDays(1)))
        val dto = InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt)
        assertEquals("watch", dto.severity)
        assertTrue(dto.body!!.contains("tomorrow"))
    }

    @Test
    fun billDueTodayUsesToday() {
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today))
        assertTrue(InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt).body!!.contains("today"))
    }

    @Test
    fun billDueInThreeDaysUsesCount() {
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today.plusDays(3)))
        assertTrue(InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt).body!!.contains("in 3 days"))
    }

    @Test
    fun farBillsAreIgnored() {
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today.plusDays(10)))
        assertEquals("Track today", InsightRules.dailyInsight(InsightInputs(today, emptyList(), bills = bills), fmt).title)
    }

    @Test
    fun nearLimitBudgetIsAWatch() {
        val txs = listOf(exp(today, 85.0, TransactionCategory.FOOD))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs, listOf(budget(TransactionCategory.FOOD, 100.0))), fmt)
        assertEquals("Budget nearly used", dto.title)
        assertTrue(dto.body!!.contains("85%"))
    }

    @Test
    fun healthyBudgetFallsThrough() {
        val txs = listOf(exp(today, 20.0, TransactionCategory.FOOD))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs, listOf(budget(TransactionCategory.FOOD, 100.0))), fmt)
        assertEquals("Top category", dto.title)
    }

    @Test
    fun behaviourPatternIsUsedWhenNothingUrgent() {
        // Last 7 days far above the week before: the "pace" detector.
        val prior = daily(7, 10.0, today.minusDays(13))
        val recent = daily(7, 50.0, today.minusDays(6))
        val dto = InsightRules.dailyInsight(InsightInputs(today, prior + recent), fmt)
        assertEquals("Spending pace", dto.title)
        assertEquals("watch", dto.severity)
        assertTrue(dto.body!!.contains("5.0"))
        assertTrue(dto.actions!!.isNotEmpty())
    }

    @Test
    fun overspendingAgainstIncomeIsReported() {
        val txs = listOf(inc(today.minusDays(5), 100.0), exp(today.minusDays(4), 150.0, TransactionCategory.SHOPPING))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs), fmt)
        assertEquals("Spending over income", dto.title)
        assertTrue(dto.body!!.contains("\$150"))
    }

    @Test
    fun topCategoryShareIsPercent() {
        val txs = listOf(exp(today, 75.0, TransactionCategory.FOOD), exp(today, 25.0, TransactionCategory.TRANSPORT))
        val dto = InsightRules.dailyInsight(InsightInputs(today, txs), fmt)
        assertTrue(dto.body!!, dto.body!!.contains("75%"))
    }

    @Test
    fun deletedBudgetsAreIgnored() {
        val txs = listOf(exp(today, 500.0, TransactionCategory.FOOD))
        val deleted = budget(TransactionCategory.FOOD, 100.0).copy(deletedAt = 1L)
        assertFalse(InsightRules.dailyInsight(InsightInputs(today, txs, listOf(deleted)), fmt).title == "Over budget")
    }

    @Test
    fun everyInsightIsLabelledRules() {
        val dto = InsightRules.dailyInsight(InsightInputs(today, listOf(exp(today, 5.0))), fmt)
        assertEquals(SOURCE_RULES, dto.source)
        assertTrue(dto.body!!.isNotBlank())
    }

    @Test
    fun insightIsDeterministic() {
        val input = InsightInputs(today, daily(14, 12.0))
        assertEquals(InsightRules.dailyInsight(input, fmt), InsightRules.dailyInsight(input, fmt))
    }

    @Test
    fun adviceExistsForEveryDetector() {
        listOf("weekend-effect", "late-night", "small-ticket", "merchant-cafe", "pace", "no-spend-streak", "fixed-load", "unknown").forEach {
            assertTrue(it, InsightRules.adviceFor(it).isNotBlank())
        }
    }

    // ─── forecast ────────────────────────────────────────────────────────────

    @Test
    fun monthlyEquivalents() {
        val cases = listOf(
            BillFrequency.WEEKLY to 52.0 * 10 / 12, BillFrequency.MONTHLY to 10.0,
            BillFrequency.QUARTERLY to 10.0 / 3, BillFrequency.YEARLY to 10.0 / 12,
        )
        cases.forEach { (f, expected) ->
            assertEquals(f.name, expected, ForecastRules.monthlyEquivalent(Bill(name = "x", amount = 10.0, frequency = f, nextDueDate = today)), 0.001)
        }
    }

    @Test
    fun weekdayAveragesSeeHeavyWeekdays() {
        val txs = (0 until 56).map { exp(today.minusDays(it.toLong()), if (today.minusDays(it.toLong()).dayOfWeek == DayOfWeek.FRIDAY) 100.0 else 10.0) }
        val avg = ForecastRules.weekdayAverages(txs, today)
        assertEquals(100.0, avg[DayOfWeek.FRIDAY]!!, 0.001)
        assertEquals(10.0, avg[DayOfWeek.MONDAY]!!, 0.001)
    }

    @Test
    fun recurringFlaggedRowsAreLeftOutOfWeekdayAverages() {
        val txs = daily(14, 10.0) + exp(today, 900.0, recurring = true)
        assertEquals(10.0, ForecastRules.weekdayAverages(txs, today)[today.dayOfWeek]!!, 0.001)
    }

    @Test
    fun noHistoryGivesNoAverages() = assertTrue(ForecastRules.weekdayAverages(emptyList(), today).isEmpty())

    @Test
    fun incomePatternAveragesPerThirtyDays() {
        val txs = listOf(inc(today.minusDays(60), 1000.0), inc(today.minusDays(30), 1000.0), inc(today, 1000.0))
        assertEquals(1500.0, ForecastRules.incomePattern(txs, today), 100.0)
    }

    @Test
    fun forecastReturnsThreeMonths() {
        val out = ForecastRules.project(daily(30, 20.0), emptyMap(), emptyList(), today, fmt = fmt)
        assertEquals(listOf("November 2026", "December 2026", "January 2027"), out.map { it.month })
    }

    @Test
    fun forecastAddsBillsToDailySpend() {
        val bills = listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = today.plusDays(2)))
        val with = ForecastRules.project(daily(30, 20.0), emptyMap(), bills, today, fmt = fmt)[0].predictedSpend
        val without = ForecastRules.project(daily(30, 20.0), emptyMap(), emptyList(), today, fmt = fmt)[0].predictedSpend
        assertEquals(500.0, with - without, 0.001)
    }

    @Test
    fun forecastDailySpendTimesDays() {
        val out = ForecastRules.project(daily(30, 20.0), emptyMap(), emptyList(), today, fmt = fmt)
        assertEquals(20.0 * 30, out[0].predictedSpend, 0.001) // November has 30 days
        assertEquals(20.0 * 31, out[1].predictedSpend, 0.001)
    }

    @Test
    fun forecastRiskHighWhenSpendingBeatsIncome() {
        val txs = daily(30, 40.0) + inc(today.minusDays(10), 900.0)
        assertEquals(RiskLevel.HIGH, ForecastRules.project(txs, emptyMap(), emptyList(), today, fmt = fmt)[0].riskLevel)
    }

    @Test
    fun forecastRiskLowWithRoomToSpare() {
        val txs = daily(30, 10.0) + inc(today.minusDays(10), 3000.0)
        assertEquals(RiskLevel.LOW, ForecastRules.project(txs, emptyMap(), emptyList(), today, fmt = fmt)[0].riskLevel)
    }

    @Test
    fun forecastRiskUsesBudgetWhenNoIncome() {
        val budgets = mapOf(TransactionCategory.FOOD to 300.0)
        assertEquals(RiskLevel.HIGH, ForecastRules.project(daily(30, 20.0), budgets, emptyList(), today, fmt = fmt)[0].riskLevel)
    }

    @Test
    fun forecastRecommendedBudgetIsBudgetTotal() {
        val budgets = mapOf(TransactionCategory.FOOD to 300.0, TransactionCategory.RENT to 500.0)
        assertEquals(800.0, ForecastRules.project(daily(30, 20.0), budgets, emptyList(), today, fmt = fmt)[0].recommendedBudget, 0.001)
    }

    @Test
    fun forecastTextMentionsBillsAndIncome() {
        val txs = daily(30, 20.0) + inc(today.minusDays(5), 2000.0)
        val bills = listOf(Bill(name = "Rent", amount = 400.0, nextDueDate = today))
        val text = ForecastRules.project(txs, emptyMap(), bills, today, fmt = fmt)[0].insight
        assertTrue(text, text.contains("\$400"))
        assertTrue(text, text.contains("Income pattern"))
    }

    @Test
    fun forecastIgnoresInactiveBills() {
        val bills = listOf(Bill(name = "Old", amount = 999.0, nextDueDate = today, isActive = false))
        val out = ForecastRules.project(daily(30, 20.0), emptyMap(), bills, today, fmt = fmt)
        assertEquals(600.0, out[0].predictedSpend, 0.001)
    }

    @Test
    fun forecastWithNoDataIsZero() {
        val out = ForecastRules.project(emptyList(), emptyMap(), emptyList(), today, fmt = fmt)
        assertTrue(out.all { it.predictedSpend == 0.0 })
        assertNotNull(out.first().insight)
    }

    // ─── budget advice ───────────────────────────────────────────────────────

    private val mid = LocalDate.of(2026, 10, 15)

    @Test
    fun adviceOverLimit() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 130.0, 100.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("over the Food limit by \$30"))
    }

    @Test
    fun adviceNearLimit() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 85.0, 100.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("85%"))
        assertTrue(text, text.contains("\$15 left"))
    }

    @Test
    fun adviceOnTrack() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 20.0, 100.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("on track"))
    }

    @Test
    fun adviceNoLimit() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 20.0, 0.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("no limit"))
    }

    @Test
    fun advicePaceCapWhenProjectedOverLimit() {
        // 70 spent by day 15 projects to 144 by day 31, over a limit of 100.
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 70.0, 100.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("At this pace you reach \$145"))
        assertTrue(text, text.contains("under \$2 a day"))
    }

    @Test
    fun adviceNoPaceCapWhenOnPace() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 30.0, 100.0), emptyList(), mid, fmt)
        assertFalse(text, text.contains("At this pace"))
    }

    @Test
    fun adviceNoPaceCapVeryEarly() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 90.0, 100.0), emptyList(), LocalDate.of(2026, 10, 3), fmt)
        assertFalse(text, text.contains("At this pace"))
    }

    @Test
    fun adviceTrendAgainstLastMonth() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 60.0, 100.0, lastMonthSpent = 40.0), emptyList(), mid, fmt)
        assertTrue(text, text.contains("50% above last month's \$40"))
    }

    @Test
    fun adviceNoTrendWhenSimilar() {
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 42.0, 100.0, lastMonthSpent = 40.0), emptyList(), mid, fmt)
        assertFalse(text, text.contains("above last month"))
    }

    @Test
    fun adviceReallocatesFromUnderUsedBudget() {
        val others = listOf(BudgetLine(TransactionCategory.ENTERTAINMENT, 10.0, 100.0), BudgetLine(TransactionCategory.TRANSPORT, 60.0, 100.0))
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 90.0, 100.0), others, mid, fmt)
        assertTrue(text, text.contains("moving up to \$45 from Entertainment"))
        assertTrue(text, text.contains("10% used"))
    }

    @Test
    fun adviceNoReallocationWhenHealthy() {
        val others = listOf(BudgetLine(TransactionCategory.ENTERTAINMENT, 10.0, 100.0))
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 20.0, 100.0), others, mid, fmt)
        assertFalse(text, text.contains("moving"))
    }

    @Test
    fun adviceNoReallocationEarlyInMonth() {
        val others = listOf(BudgetLine(TransactionCategory.ENTERTAINMENT, 10.0, 100.0))
        val text = BudgetAdviceRules.advise(BudgetLine(TransactionCategory.FOOD, 95.0, 100.0), others, LocalDate.of(2026, 10, 4), fmt)
        assertFalse(text, text.contains("moving"))
    }

    @Test
    fun adviceNeverReallocatesFromItself() {
        val own = BudgetLine(TransactionCategory.FOOD, 5.0, 100.0)
        val text = BudgetAdviceRules.advise(own.copy(spent = 95.0), listOf(own), mid, fmt)
        assertFalse(text, text.contains("from Food"))
    }
}
