package com.ledgerai.app.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private val now = LocalDate.now()
    private val lastMonthDate = now.minusMonths(1)
    private val daysLeft = now.lengthOfMonth() - now.dayOfMonth
    private val dayDivisor = daysLeft.coerceAtLeast(1)

    init {
        viewModelScope.launch {
            combine(
                budgetRepo.getBudgetsForMonth(now.monthValue, now.year),
                budgetRepo.getBudgetsForMonth(lastMonthDate.monthValue, lastMonthDate.year),
                transactionRepo.getTransactionsForMonth(now.year, now.monthValue)
            ) { budgets, lastBudgets, txs -> Triple(budgets, lastBudgets, txs) }
                .collectLatest { (budgets, lastBudgets, txs) ->
                    val spentByCategory = txs
                        .asSequence()
                        .filter { it.type == TransactionType.EXPENSE }
                        .groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }

                    val enriched = budgets
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
                            dailyAllowance = totalRemaining / dayDivisor,
                            unbudgetedSpend = unbudgetedSpend,
                            canCopyLastMonth = enriched.isEmpty() && lastBudgets.isNotEmpty()
                        )
                    }
                }
        }
    }

    fun addBudget(category: TransactionCategory, limit: Double, threshold: Int = 80) {
        viewModelScope.launch {
            val budget = Budget(
                category = category,
                monthlyLimit = limit,
                month = now.monthValue,
                year = now.year,
                alertThreshold = threshold
            )
            budgetRepo.insert(budget)
            _uiState.update { it.copy(snackbarMessage = "Budget added for ${category.displayName}") }
        }
    }

    fun updateBudget(budget: Budget, category: TransactionCategory, limit: Double, threshold: Int) {
        viewModelScope.launch {
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
        }
    }

    fun copyLastMonth() {
        viewModelScope.launch {
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
            _uiState.update { it.copy(snackbarMessage = "Copied") }
        }
    }

    fun getAiAdvice(budget: Budget) {
        viewModelScope.launch {
            aiRepo.getBudgetAdvice(budget.category.displayName, budget.spent, budget.monthlyLimit, budget.usagePercent)
                .onSuccess { advice ->
                    _uiState.update { state ->
                        state.copy(aiAdvice = state.aiAdvice + (budget.id to advice))
                    }
                }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
