package com.ledgerai.app.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.insight.BudgetAdviceRules
import com.ledgerai.app.data.insight.BudgetLine
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.ledgerai.app.presentation.screens.money.todayTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val isLoading: Boolean = true,
    val aiAdvice: Map<Long, String> = emptyMap(),
    val snackbarMessage: String? = null,
    val daysLeft: Int = 0,
    val totalRemaining: Double = 0.0,
    val dailyAllowance: Double = 0.0,
    val unbudgetedSpend: Double = 0.0,
    val canCopyLastMonth: Boolean = false
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val transactionRepo: TransactionRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dataFlow = todayTicker().flatMapLatest { now ->
        val last = now.minusMonths(1)
        combine(
            budgetRepo.getBudgetsForMonth(now.monthValue, now.year),
            budgetRepo.getBudgetsForMonth(last.monthValue, last.year),
            transactionRepo.getTransactionsForMonth(now.year, now.monthValue)
        ) { budgets, lastBudgets, txs -> BudgetData(now, budgets, lastBudgets, txs) }
    }

    private class BudgetData(
        val now: LocalDate,
        val budgets: List<Budget>,
        val lastBudgets: List<Budget>,
        val txs: List<com.ledgerai.app.domain.model.Transaction>
    )

    init {
        viewModelScope.launch {
            dataFlow.collectLatest { data ->
                val now = data.now
                val daysLeft = now.lengthOfMonth() - now.dayOfMonth
                val spentByCategory = data.txs
                    .asSequence()
                    .filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }

                val enriched = data.budgets
                    .map { it.copy(spent = spentByCategory[it.category] ?: 0.0) }
                    .sortedWith(
                        compareBy<Budget> {
                            when {
                                it.isOverBudget -> 0
                                it.isNearLimit -> 1
                                else -> 2
                            }
                        }.thenByDescending { it.usagePercent }
                    )

                val budgeted = enriched.map { it.category }.toSet()
                val unbudgetedSpend = spentByCategory
                    .filterKeys { it !in budgeted }
                    .values
                    .sum()
                val totalRemaining = enriched.sumOf { it.remaining }

                _uiState.update {
                    it.copy(
                        budgets = enriched,
                        isLoading = false,
                        daysLeft = daysLeft,
                        totalRemaining = totalRemaining,
                        dailyAllowance = totalRemaining / daysLeft.coerceAtLeast(1),
                        unbudgetedSpend = unbudgetedSpend,
                        canCopyLastMonth = enriched.isEmpty() && data.lastBudgets.isNotEmpty()
                    )
                }
            }
        }
    }

    fun addBudget(category: TransactionCategory, limit: Double, threshold: Int = 80) {
        viewModelScope.launch {
            val now = LocalDate.now()
            if (budgetRepo.getBudgetForCategory(category, now.monthValue, now.year) != null) {
                _uiState.update { it.copy(snackbarMessage = "${category.displayName} already has a budget") }
                return@launch
            }
            budgetRepo.insert(
                Budget(
                    category = category,
                    monthlyLimit = limit,
                    month = now.monthValue,
                    year = now.year,
                    alertThreshold = threshold
                )
            )
            _uiState.update { it.copy(snackbarMessage = "Budget added for ${category.displayName}") }
        }
    }

    fun updateBudget(budget: Budget, category: TransactionCategory, limit: Double, threshold: Int) {
        viewModelScope.launch {
            if (category != budget.category) {
                val clash = budgetRepo.getBudgetForCategory(category, budget.month, budget.year)
                if (clash != null && clash.id != budget.id) {
                    _uiState.update { it.copy(snackbarMessage = "${category.displayName} already has a budget") }
                    return@launch
                }
            }
            budgetRepo.update(
                budget.copy(
                    category = category,
                    monthlyLimit = limit,
                    alertThreshold = threshold
                )
            )
            _uiState.update { it.copy(snackbarMessage = "Budget updated") }
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepo.delete(budget)
            _uiState.update { it.copy(snackbarMessage = "Budget deleted") }
        }
    }

    fun copyLastMonth() {
        viewModelScope.launch {
            val now = LocalDate.now()
            val lastMonthDate = now.minusMonths(1)
            val current = budgetRepo.getBudgetsForMonth(now.monthValue, now.year).first()
            if (current.isNotEmpty()) return@launch
            val lastBudgets = budgetRepo
                .getBudgetsForMonth(lastMonthDate.monthValue, lastMonthDate.year)
                .first()
            if (lastBudgets.isEmpty()) return@launch
            lastBudgets.forEach { src ->
                budgetRepo.insert(
                    Budget(
                        category = src.category,
                        monthlyLimit = src.monthlyLimit,
                        month = now.monthValue,
                        year = now.year,
                        alertThreshold = src.alertThreshold
                    )
                )
            }
            _uiState.update { it.copy(snackbarMessage = "Copied last month") }
        }
    }

    fun getAiAdvice(budget: Budget) {
        // Rules decide the advice from this month's numbers. No cloud call is needed.
        val lines = _uiState.value.budgets.map { BudgetLine(it.category, it.spent, it.monthlyLimit) }
        val own = BudgetLine(budget.category, budget.spent, budget.monthlyLimit)
        val advice = BudgetAdviceRules.advise(
            own, lines.filter { it.category != budget.category }, LocalDate.now(),
            fmt = { com.ledgerai.app.presentation.components.money(it) }
        ) + " (Rules)"
        _uiState.update { state -> state.copy(aiAdvice = state.aiAdvice + (budget.id to advice)) }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
