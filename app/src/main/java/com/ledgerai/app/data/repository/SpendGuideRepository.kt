package com.ledgerai.app.data.repository

import com.ledgerai.app.data.finance.SpendGuideCalculator
import com.ledgerai.app.data.finance.SpendGuideResult
import com.ledgerai.app.data.local.room.SpendGuideDayDao
import com.ledgerai.app.data.local.room.SpendGuideDayEntity
import com.ledgerai.app.data.local.room.SpendSpeculationDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.SpendSpeculation
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpendGuideRepository @Inject constructor(
    private val speculationDao: SpendSpeculationDao,
    private val guideDayDao: SpendGuideDayDao,
    private val budgetRepository: BudgetRepository,
    private val billRepository: BillRepository,
    private val debtRepository: DebtRepository,
    private val goalRepository: GoalRepository,
    private val transactionRepository: TransactionRepository
) {

    fun observeSpeculations(): Flow<List<SpendSpeculation>> =
        speculationDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addSpeculation(spec: SpendSpeculation): Long =
        speculationDao.insert(spec.toEntity())

    suspend fun computeTodayGuide(date: LocalDate = LocalDate.now()): SpendGuideResult {
        val (periodStart, periodEnd) = SpendGuideCalculator.monthPeriod(date)
        val now = date
        val budgets = budgetRepository.getBudgetsForMonth(now.monthValue, now.year).first()
        val txs = transactionRepository.getTransactionsForMonth(now.year, now.monthValue).first()
        // Budget.spent is not maintained in storage, so spending comes from this month's transactions.
        val spentByCategory = txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val remainingBudget = budgets.sumOf { it.monthlyLimit - (spentByCategory[it.category] ?: 0.0) }
        val bills = billRepository.getAllBills().first()
        val billsDue = bills.sumOf {
            SpendGuideCalculator.billAmountDueInPeriod(
                it.amount, it.frequency, it.nextDueDate, periodStart, periodEnd
            )
        }
        val debts = debtRepository.getAllDebts().first()
        val debtDue = debts.filter { !it.isPaid && it.dueDate != null }
            .filter { !it.dueDate!!.isBefore(date) && !it.dueDate!!.isAfter(periodEnd) }
            .sumOf { it.amount }
        val goals = goalRepository.getAllGoals().first()
        val goalContrib = goals.filter { !it.isCompleted }.sumOf { g ->
            val monthsLeft = 1.0
            (g.targetAmount - g.savedAmount).coerceAtLeast(0.0) / monthsLeft.coerceAtLeast(1.0)
        }
        val specs = speculationDao.observeAll().first().map { it.toDomain() }
        val periodTx = txs.filter { !it.date.isBefore(periodStart) && !it.date.isAfter(periodEnd) }
        val spentPeriod = periodTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val spentToday = periodTx.filter { it.date == date && it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        val weekdayAvg = periodTx
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.date.dayOfWeek }
            .mapValues { (_, list) -> list.sumOf { it.amount } / 4.0 }

        val prior = guideDayDao.getForDate(date.minusDays(1))
        val priorUnused = prior?.let { (it.guideAmount - it.spent).coerceAtLeast(0.0) } ?: 0.0

        val result = SpendGuideCalculator.compute(
            today = date,
            periodStart = periodStart,
            periodEnd = periodEnd,
            remainingBudget = remainingBudget,
            billsDueBeforePeriodEnd = billsDue,
            debtDueBeforePeriodEnd = debtDue,
            goalContribution = goalContrib,
            speculations = specs,
            spentToday = spentToday,
            spentThisPeriod = spentPeriod,
            weekdaySpendAvg = weekdayAvg,
            priorGuideUnused = priorUnused
        )

        guideDayDao.upsert(
            SpendGuideDayEntity(
                date = date,
                guideAmount = result.guideAmount,
                spent = spentToday,
                buffer = result.buffer,
                marginUsed = result.guideAmount
            )
        )
        return result
    }
}
