package com.ledgerai.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.FinancialHealthScore
import com.ledgerai.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val netBalance: Double = 0.0,
    val remainingBudget: Double? = null,
    val takeaway: String = "",
    val recentTransactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val healthScore: FinancialHealthScore? = null,
    val greeting: String = "Hello!",
    val currentMonth: String = ""
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val now = LocalDate.now()

    init {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val monthName = now.month.name.lowercase().replaceFirstChar { it.uppercase() }
            _uiState.update { it.copy(currentMonth = "$monthName ${now.year}") }

            combine(
                transactionRepo.getRecentTransactions(8),
                budgetRepo.getBudgetsForMonth(now.monthValue, now.year)
            ) { recent, budgets ->
                Pair(recent, budgets)
            }.collectLatest { (recent, budgets) ->
                val income = transactionRepo.getTotalIncomeForMonth(now.year, now.monthValue)
                val expenses = transactionRepo.getTotalExpensesForMonth(now.year, now.monthValue)
                val budgetsWithSpending = budgets.map { budget ->
                    val spent = transactionRepo.getSpendingForCategoryMonth(
                        budget.category, now.year, now.monthValue
                    )
                    budget.copy(spent = spent)
                }
                val net = income - expenses
                val remaining = if (budgetsWithSpending.isNotEmpty()) {
                    budgetsWithSpending.sumOf { it.remaining }
                } else null
                val adherence = if (budgetsWithSpending.isNotEmpty()) {
                    budgetsWithSpending.count { !it.isOverBudget }.toDouble() /
                        budgetsWithSpending.size * 100
                } else 100.0

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthlyIncome = income,
                        monthlyExpenses = expenses,
                        netBalance = net,
                        remainingBudget = remaining,
                        takeaway = buildTakeaway(net, remaining),
                        recentTransactions = recent,
                        budgets = budgetsWithSpending,
                        greeting = getGreeting()
                    )
                }

                loadHealthScore(income, expenses, adherence)
            }
        }
    }

    private fun buildTakeaway(net: Double, remainingBudget: Double?): String {
        return when {
            remainingBudget != null && net >= 0 ->
                "On track — \$${"%.0f".format(remainingBudget)} left in budgets"
            remainingBudget != null && net < 0 ->
                "Spending ahead — \$${"%.0f".format(remainingBudget)} still in budgets"
            net >= 0 ->
                "Income covers spending this month"
            else ->
                "Spending exceeds income this month"
        }
    }

    private fun loadHealthScore(income: Double, expenses: Double, adherence: Double) {
        viewModelScope.launch {
            aiRepo.calculateHealthScore(income, expenses, 0.0, adherence)
                .onSuccess { score ->
                    _uiState.update { it.copy(healthScore = score) }
                }
        }
    }

    private fun getGreeting(): String {
        return when (java.time.LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Hello"
        }
    }
}
