package com.ledgerai.app.presentation.screens.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LWide
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.todayTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
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
    val overdueCount: Int = 0,
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
    private val aiRepo: AiRepository,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForecastUiState())
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    private class Inputs(
        val today: LocalDate,
        val tx: List<com.ledgerai.app.domain.model.Transaction>,
        val bills: List<Bill>,
        val cash: Double?
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val inputs = todayTicker().flatMapLatest { today ->
        combine(
            transactionRepo.getTransactionsForMonth(today.year, today.monthValue),
            billRepo.getActiveBills(),
            prefs.cashOnHand
        ) { tx, bills, cash -> Inputs(today, tx, bills, cash.toDoubleOrNull()) }
    }

    init {
        viewModelScope.launch {
            inputs.collect { input ->
                val now = input.today
                val monthEnd = now.withDayOfMonth(now.lengthOfMonth())
                val income = input.tx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val spentSoFar = input.tx
                    .filter { it.type == TransactionType.EXPENSE && !it.date.isAfter(now) }
                    .sumOf { it.amount }
                val daysLeft = now.lengthOfMonth() - now.dayOfMonth
                val pace = spentSoFar / now.dayOfMonth
                // Overdue bills are still owed, so everything due up to month end counts.
                val billsRest = input.bills
                    .filter { !it.nextDueDate.isAfter(monthEnd) }
                    .sumOf { it.amount }
                val projectedSpend = spentSoFar + maxOf(pace * daysLeft, billsRest)
                // Same base as the dashboard: cash on hand (if set) plus this month's income.
                val projectedBalance = (input.cash ?: 0.0) + income - projectedSpend
                val dailyPace = projectedSpend / now.lengthOfMonth().toDouble()
                val runwayDays = if (dailyPace > 0.0 && projectedBalance > 0.0) {
                    (projectedBalance / dailyPace).toInt().takeIf { it > 0 }
                } else {
                    null
                }
                val upcoming = input.bills
                    .filter { !it.nextDueDate.isAfter(now.plusDays(30)) }
                    .sortedBy { it.nextDueDate }
                    .take(6)

                _uiState.update {
                    it.copy(
                        projectedBalance = projectedBalance,
                        projectedSpend = projectedSpend,
                        runwayDays = runwayDays,
                        upcomingBills = upcoming,
                        overdueCount = input.bills.count { b -> b.nextDueDate.isBefore(now) },
                        isProjectionReady = true
                    )
                }
            }
        }
    }

    fun generateForecast() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val now = LocalDate.now()
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
                        _uiState.update { it.copy(isLoading = false, error = err.message ?: "Forecast unavailable") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Forecast unavailable") }
            }
        }
    }
}

// ─── Section ──────────────────────────────────────────────────────────────────

private val dueFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** Forecast section for the Money Plan segment: one wide block that expands. */
fun LazyListScope.forecastItems(
    state: ForecastUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPredict: () -> Unit
) {
    item(key = "forecast-header") { LSection("Forecast") }
    item(key = "forecast-block") {
        LWide(label = "Month end", onClick = onToggle) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.isProjectionReady) money(state.projectedBalance) else "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.projectedBalance < 0) L.Danger else L.OnBox,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse forecast" else "Expand forecast",
                    tint = L.Gold
                )
            }
            if (state.isProjectionReady) {
                Text("${money(state.projectedSpend)} projected spend", style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
            }
            if (expanded) {
                state.runwayDays?.let { days ->
                    ForecastLine("Runway", "$days days")
                }
                if (state.overdueCount > 0) ForecastLine("Overdue bills", state.overdueCount.toString(), L.Danger)
                if (state.upcomingBills.isEmpty()) {
                    Text("Nothing due", style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
                } else {
                    Text("DUE SOON", style = MaterialTheme.typography.labelSmall, color = L.Gold)
                    val today = LocalDate.now()
                    state.upcomingBills.forEach { bill ->
                        val late = bill.nextDueDate.isBefore(today)
                        ForecastLine(
                            bill.name + " · " + (if (late) "Overdue" else bill.nextDueDate.format(dueFormat)),
                            money(bill.amount),
                            if (late) L.Danger else L.Gold
                        )
                    }
                }
                state.forecasts.forEach { forecast ->
                    ForecastLine(
                        "${forecast.month} · ${forecast.riskLevel.label}",
                        money(forecast.predictedSpend),
                        if (forecast.riskLevel == RiskLevel.HIGH) L.Danger else L.Gold
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = L.Gold,
                        modifier = Modifier.size(24.dp).semantics { contentDescription = "Loading forecast" }
                    )
                } else {
                    LButton(if (state.forecasts.isEmpty()) "Predict months" else "Refresh prediction", onPredict)
                }
            }
        }
    }
    if (state.error != null && !state.isLoading) {
        item(key = "forecast-error") { LError("Forecast unavailable", onRetry = onPredict) }
    }
}

@androidx.compose.runtime.Composable
private fun ForecastLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = L.OnBox) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
