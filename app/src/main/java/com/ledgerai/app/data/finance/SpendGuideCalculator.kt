package com.ledgerai.app.data.finance

import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
import com.ledgerai.app.domain.model.SpendSpeculation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

data class SpendGuideResult(
    val guideAmount: Double,
    val hardLimit: Double,
    val buffer: Double,
    val daysLeft: Int,
    val status: SpendGuideStatus,
    val reasons: List<String> = emptyList()
)

enum class SpendGuideStatus { UNDER, NEAR, OVER }

object SpendGuideCalculator {

    private const val DEFAULT_MARGIN = 0.15
    private const val CARRY_FORWARD_RATE = 0.5

    fun confidenceWeight(c: SpeculationConfidence): Double = when (c) {
        SpeculationConfidence.HIGH -> 1.0
        SpeculationConfidence.MEDIUM -> 0.6
        SpeculationConfidence.LOW -> 0.3
    }

    fun compute(
        today: LocalDate,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        remainingBudget: Double,
        billsDueBeforePeriodEnd: Double,
        debtDueBeforePeriodEnd: Double,
        goalContribution: Double,
        speculations: List<SpendSpeculation>,
        spentToday: Double,
        spentThisPeriod: Double,
        weekdaySpendAvg: Map<DayOfWeek, Double> = emptyMap(),
        safetyMargin: Double = DEFAULT_MARGIN,
        priorGuideUnused: Double = 0.0
    ): SpendGuideResult {
        val specExpense = speculations
            .filter { it.direction == SpeculationDirection.EXPENSE }
            .filter { !it.expectedDate.isBefore(today) && !it.expectedDate.isAfter(periodEnd) }
            .sumOf { it.amount * confidenceWeight(it.confidence) }
        val specIncome = speculations
            .filter { it.direction == SpeculationDirection.INCOME }
            .filter { !it.expectedDate.isBefore(today) && !it.expectedDate.isAfter(periodEnd) }
            .sumOf { it.amount * confidenceWeight(it.confidence) * 0.5 }

        val pool = remainingBudget - billsDueBeforePeriodEnd - debtDueBeforePeriodEnd -
            goalContribution - specExpense + specIncome
        val daysLeft = max(1, java.time.temporal.ChronoUnit.DAYS.between(today, periodEnd).toInt() + 1)

        val weekdayWeight = today.dayOfWeek
        val profile = weekdaySpendAvg[weekdayWeight] ?: 1.0
        val avgProfile = if (weekdaySpendAvg.isEmpty()) 1.0 else weekdaySpendAvg.values.average().coerceAtLeast(1.0)
        val dayWeight = profile / avgProfile

        val baseDaily = pool / daysLeft
        val withCarry = baseDaily + priorGuideUnused * CARRY_FORWARD_RATE
        val weighted = withCarry * dayWeight.coerceIn(0.7, 1.3)
        val guide = (weighted * (1.0 - safetyMargin)).coerceAtLeast(0.0)
        val hardLimit = weighted.coerceAtLeast(0.0)
        val buffer = hardLimit - guide

        val status = when {
            spentToday >= hardLimit -> SpendGuideStatus.OVER
            spentToday >= guide * 0.8 -> SpendGuideStatus.NEAR
            else -> SpendGuideStatus.UNDER
        }

        val reasons = buildList {
            if (billsDueBeforePeriodEnd > 0) add("Bills reserved")
            if (specExpense > 0) add("Planned expenses")
            if (goalContribution > 0) add("Savings goal")
            if (priorGuideUnused > 0) add("Carry from yesterday")
        }

        return SpendGuideResult(
            guideAmount = guide,
            hardLimit = hardLimit,
            buffer = buffer,
            daysLeft = daysLeft,
            status = status,
            reasons = reasons
        )
    }

    fun monthPeriod(date: LocalDate): Pair<LocalDate, LocalDate> {
        val start = date.withDayOfMonth(1)
        val end = date.with(TemporalAdjusters.lastDayOfMonth())
        return start to end
    }

    fun billAmountDueInPeriod(
        amount: Double,
        frequency: BillFrequency,
        nextDue: LocalDate,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): Double {
        if (nextDue.isBefore(periodStart) || nextDue.isAfter(periodEnd)) return 0.0
        return when (frequency) {
            BillFrequency.MONTHLY -> amount
            BillFrequency.WEEKLY -> amount * 4
            BillFrequency.QUARTERLY -> amount / 3
            BillFrequency.YEARLY -> if (nextDue.month == periodStart.month) amount else 0.0
        }
    }
}
