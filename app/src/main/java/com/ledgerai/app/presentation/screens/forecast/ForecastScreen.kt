package com.ledgerai.app.presentation.screens.forecast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class ForecastUiState(
    val projectedBalance: Double = 0.0,
    val projectedSpend: Double = 0.0,
    val runwayDays: Int? = null,
    val upcomingBills: List<Bill> = emptyList(),
    val isProjectionReady: Boolean = false,
    val forecasts: List<FinancialForecast> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val billRepo: BillRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForecastUiState())
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()
    private val now = LocalDate.now()

    init {
        viewModelScope.launch {
            combine(
                transactionRepo.getTransactionsForMonth(now.year, now.monthValue),
                billRepo.getActiveBills()
            ) { tx, bills -> tx to bills }.collect { (tx, bills) ->
                val monthEnd = now.withDayOfMonth(now.lengthOfMonth())
                val income = tx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val spentSoFar = tx
                    .filter { it.type == TransactionType.EXPENSE && !it.date.isAfter(now) }
                    .sumOf { it.amount }
                val daysLeft = now.lengthOfMonth() - now.dayOfMonth
                val pace = spentSoFar / now.dayOfMonth
                val billsRest = bills
                    .filter { it.nextDueDate.isAfter(now) && !it.nextDueDate.isAfter(monthEnd) }
                    .sumOf { it.amount }
                val projectedSpend = spentSoFar + maxOf(pace * daysLeft, billsRest)
                val projectedBalance = income - projectedSpend
                val dailyPace = projectedSpend / now.lengthOfMonth().toDouble()
                val runwayDays = if (dailyPace > 0.0 && projectedBalance > 0.0) {
                    (projectedBalance / dailyPace).toInt().takeIf { it > 0 }
                } else {
                    null
                }
                val upcoming = bills
                    .filter { !it.nextDueDate.isBefore(now) && !it.nextDueDate.isAfter(now.plusDays(30)) }
                    .sortedBy { it.nextDueDate }
                    .take(6)

                _uiState.update {
                    it.copy(
                        projectedBalance = projectedBalance,
                        projectedSpend = projectedSpend,
                        runwayDays = runwayDays,
                        upcomingBills = upcoming,
                        isProjectionReady = true
                    )
                }
            }
        }
    }

    fun generateForecast() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val transactions = transactionRepo.getRecentTransactions(100).first()
                val budgets = budgetRepo.getBudgetsForMonth(now.monthValue, now.year).first()
                val budgetMap = budgets.associate { it.category to it.monthlyLimit }

                aiRepo.generateForecast(transactions, budgetMap).fold(
                    onSuccess = { forecasts ->
                        _uiState.update { it.copy(forecasts = forecasts, isLoading = false) }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(isLoading = false, error = err.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val dueFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun ForecastScreen(
    onBack: () -> Unit = {},
    viewModel: ForecastViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LScreen(
        title = "Forecast",
        onBack = onBack,
        action = {
            LIconButton(Icons.Filled.AutoAwesome, "Forecast", onClick = { viewModel.generateForecast() })
        }
    ) {
        item {
            LHero(
                label = "Month end",
                value = if (state.isProjectionReady) money(state.projectedBalance) else "—",
                sub = if (state.isProjectionReady) "${money(state.projectedSpend)} spend" else null,
                valueColor = if (state.projectedBalance < 0) L.Danger else L.OnBox
            )
        }

        state.runwayDays?.let { days ->
            item { LStat("Runway", "$days days") }
        }

        item { LSection("Upcoming") }

        if (state.upcomingBills.isEmpty()) {
            item { LEmpty(Icons.Filled.EventAvailable, "Nothing due") }
        } else {
            items(state.upcomingBills, key = { it.id }) { bill ->
                LRow(
                    title = bill.name,
                    sub = bill.nextDueDate.format(dueFormat),
                    trailing = money(bill.amount),
                    icon = Icons.AutoMirrored.Filled.ReceiptLong
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = L.Gold)
                }
            }
        }

        if (state.error != null && !state.isLoading) {
            item {
                Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            }
        }

        if (state.forecasts.isNotEmpty()) {
            item { LSection("Ahead") }
            items(state.forecasts) { forecast ->
                LRow(
                    title = forecast.month,
                    sub = forecast.riskLevel.label,
                    trailing = money(forecast.predictedSpend),
                    trailingColor = if (forecast.riskLevel == RiskLevel.HIGH) L.Danger else L.Gold
                )
            }
        }
    }
}
