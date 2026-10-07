package com.ledgerai.app.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val isLoading: Boolean = true,
    val aiAdvice: Map<Long, String> = emptyMap(),
    val snackbarMessage: String? = null
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

    init {
        viewModelScope.launch {
            budgetRepo.getBudgetsForMonth(now.monthValue, now.year).collectLatest { budgets ->
                val enriched = budgets.map { budget ->
                    val spent = transactionRepo.getSpendingForCategoryMonth(budget.category, now.year, now.monthValue)
                    budget.copy(spent = spent)
                }
                _uiState.update { it.copy(budgets = enriched, isLoading = false) }
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

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepo.delete(budget)
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
