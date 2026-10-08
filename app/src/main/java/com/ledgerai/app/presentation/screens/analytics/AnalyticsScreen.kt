package com.ledgerai.app.presentation.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

enum class AnalyticsPeriod(val label: String, val months: Long) {
    MONTH("Month", 1),
    QUARTER("3M", 3),
    YEAR("Year", 12)
}

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val transactions: List<Transaction> = emptyList(),
    val categoryTotals: Map<TransactionCategory, Double> = emptyMap(),
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val savingsRate: Double? = null,
    val dailyAverageSpend: Double = 0.0,
    val topMerchant: String? = null,
    val topMerchantTotal: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()
    private val period = MutableStateFlow(AnalyticsPeriod.MONTH)

    init {
        viewModelScope.launch {
            combine(period, transactionRepo.getAllTransactions()) { p, all -> p to all }
                .collectLatest { (p, all) ->
                    val monthStart = LocalDate.now().withDayOfMonth(1)
                    val start = monthStart.minusMonths(p.months - 1)
                    val end = monthStart.plusMonths(1)
                    val transactions = all.filter { !it.date.isBefore(start) && it.date.isBefore(end) }
                    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
                    val categoryTotals = expenses.groupBy { it.category }
                        .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                        .toList().sortedByDescending { it.second }.toMap()
                    val incomeTotal = transactions.filter { t -> t.type == TransactionType.INCOME }.sumOf { t -> t.amount }
                    val expenseTotal = expenses.sumOf { t -> t.amount }
                    val savingsRate = if (incomeTotal > 0) ((incomeTotal - expenseTotal) / incomeTotal) * 100.0 else null
                    val today = LocalDate.now()
                    val lastDay = minOf(today, end.minusDays(1))
                    val dayCount = ChronoUnit.DAYS.between(start, lastDay).toInt().coerceAtLeast(0) + 1
                    val topMerchant = expenses
                        .groupBy { it.merchant.ifBlank { it.category.displayName } }
                        .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                        .maxByOrNull { it.value }

                    _uiState.update {
                        it.copy(
                            period = p,
                            transactions = transactions,
                            categoryTotals = categoryTotals,
                            monthlyIncome = incomeTotal,
                            monthlyExpenses = expenseTotal,
                            savingsRate = savingsRate,
                            dailyAverageSpend = expenseTotal / dayCount,
                            topMerchant = topMerchant?.key,
                            topMerchantTotal = topMerchant?.value ?: 0.0,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun setPeriod(value: AnalyticsPeriod) {
        period.value = value
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val slicePalette = listOf(
    L.Gold,
    L.OnBox,
    L.GoldSoft,
    L.OnBox.copy(alpha = 0.55f),
    L.Gold.copy(alpha = 0.55f),
    L.OnBox.copy(alpha = 0.3f)
)

private fun sliceColor(index: Int): Color = slicePalette[index % slicePalette.size]

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val entries = state.categoryTotals.entries.toList()
    val total = state.monthlyExpenses

    LScreen(title = "Insights", onBack = onBack) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsPeriod.entries.forEach { p ->
                    LChip(p.label, selected = state.period == p, onClick = { viewModel.setPeriod(p) })
                }
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = L.Gold)
                }
            }
            return@LScreen
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LStat("Income", money(state.monthlyIncome), Modifier.weight(1f))
                LStat("Spent", money(total), Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                state.savingsRate?.let { rate ->
                    LStat("Savings", "${"%.0f".format(rate)}%", Modifier.weight(1f))
                }
                LStat("Daily", money(state.dailyAverageSpend), Modifier.weight(1f))
            }
        }

        state.topMerchant?.let { merchant ->
            item {
                LRow(title = merchant, trailing = money(state.topMerchantTotal))
            }
        }

        if (entries.isEmpty()) {
            item { LEmpty(Icons.Filled.PieChart, "No spending") }
            return@LScreen
        }

        item {
            LCard(padding = 24.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SpendDonut(entries.map { it.value }, Modifier.size(180.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Spent", style = MaterialTheme.typography.labelSmall, color = L.Gold)
                        Text(money(total), style = MaterialTheme.typography.titleLarge, color = L.OnBox, maxLines = 1)
                    }
                }
            }
        }

        item { LSection("Categories") }

        val max = entries.maxOf { it.value }.coerceAtLeast(1.0)
        itemsIndexed(entries, key = { _, e -> e.key.name }) { index, (cat, amount) ->
            val pct = if (total > 0) (amount / total * 100).toInt() else 0
            LRow(
                title = cat.displayName,
                sub = "$pct%",
                trailing = money(amount),
                end = {
                    LProgress(
                        fraction = (amount / max).toFloat(),
                        modifier = Modifier.width(48.dp),
                        color = sliceColor(index)
                    )
                }
            )
        }
    }
}

@Composable
private fun SpendDonut(values: List<Double>, modifier: Modifier = Modifier) {
    val sum = values.sum()
    if (sum <= 0.0) return
    Canvas(modifier) {
        val stroke = size.minDimension * 0.14f
        val inset = stroke / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val gap = if (values.size > 1) 2f else 0f
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v / sum * 360f).toFloat()
            drawArc(
                color = sliceColor(i),
                startAngle = start + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            start += sweep
        }
    }
}
