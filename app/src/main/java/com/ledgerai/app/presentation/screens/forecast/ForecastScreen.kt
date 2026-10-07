package com.ledgerai.app.presentation.screens.forecast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.presentation.components.EmptyStateCard
import com.ledgerai.app.presentation.components.LoadingCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class ForecastUiState(
    val forecasts: List<FinancialForecast> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForecastUiState())
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()
    private val now = LocalDate.now()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Forecast") },
                actions = {
                    IconButton(onClick = { viewModel.generateForecast() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh forecast")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column {
                            Text("AI-Powered Forecast", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Based on your last 3 months of spending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            if (state.isLoading) {
                item { LoadingCard() }
            }

            state.error?.let { err ->
                item {
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (state.forecasts.isEmpty() && !state.isLoading) {
                item {
                    EmptyStateCard(
                        emoji = "🔮",
                        title = "No forecast yet",
                        subtitle = "Tap the refresh button to generate your AI forecast"
                    )
                }
            }

            items(state.forecasts) { forecast ->
                ForecastCard(forecast)
            }
        }
    }
}

@Composable
private fun ForecastCard(forecast: FinancialForecast) {
    val riskColor = when (forecast.riskLevel) {
        RiskLevel.LOW -> Color(0xFF22C55E)
        RiskLevel.MEDIUM -> Color(0xFFF59E0B)
        RiskLevel.HIGH -> Color(0xFFEF4444)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(forecast.month, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = MaterialTheme.shapes.small, color = riskColor.copy(alpha = 0.15f)) {
                    Text(forecast.riskLevel.label, color = riskColor,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ForecastStat("Predicted Spend", "\$${"%.0f".format(forecast.predictedSpend)}")
                ForecastStat("Recommended Budget", "\$${"%.0f".format(forecast.recommendedBudget)}")
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(forecast.insight, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ForecastStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
