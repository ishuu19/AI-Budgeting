package com.ledgerai.app.presentation.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.SectionTitle
import com.ledgerai.app.presentation.theme.CategoryColors
import com.ledgerai.app.presentation.theme.ExpenseRed
import com.ledgerai.app.presentation.theme.IncomeGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class AnalyticsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categoryTotals: Map<TransactionCategory, Double> = emptyMap(),
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()
    private val now = LocalDate.now()

    init {
        viewModelScope.launch {
            transactionRepo.getTransactionsForMonth(now.year, now.monthValue).collectLatest { transactions ->
                val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
                val categoryTotals = expenses.groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                    .toList().sortedByDescending { it.second }.toMap()

                val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val expenseTotal = expenses.sumOf { it.amount }

                _uiState.update {
                    it.copy(
                        transactions = transactions,
                        categoryTotals = categoryTotals,
                        monthlyIncome = income,
                        monthlyExpenses = expenseTotal,
                        isLoading = false
                    )
                }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }) }) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Income vs Expense
            item {
                SectionTitle("This Month")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsStat("Income", "\$${"%.2f".format(state.monthlyIncome)}", IncomeGreen, Modifier.weight(1f))
                    AnalyticsStat("Expenses", "\$${"%.2f".format(state.monthlyExpenses)}", ExpenseRed, Modifier.weight(1f))
                    val saved = state.monthlyIncome - state.monthlyExpenses
                    AnalyticsStat("Saved", "\$${"%.2f".format(saved)}", if (saved >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
                }
            }

            // Pie Chart
            if (state.categoryTotals.isNotEmpty()) {
                item {
                    SectionTitle("Spending by Category")
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SpendingPieChart(categoryTotals = state.categoryTotals)
                            Spacer(Modifier.height(12.dp))
                            CategoryLegend(categoryTotals = state.categoryTotals, totalExpenses = state.monthlyExpenses)
                        }
                    }
                }
            }

            // Bar Chart
            if (state.categoryTotals.isNotEmpty()) {
                item {
                    SectionTitle("Top Categories")
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            state.categoryTotals.entries.take(6).forEach { (cat, amount) ->
                                CategoryBarRow(
                                    category = cat,
                                    amount = amount,
                                    maxAmount = state.categoryTotals.values.maxOrNull() ?: 1.0
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SpendingPieChart(categoryTotals: Map<TransactionCategory, Double>) {
    val total = categoryTotals.values.sum()
    if (total == 0.0) return

    val entries = categoryTotals.entries.toList()
    val sweepAngles = entries.map { (it.value / total * 360f).toFloat() }

    Canvas(modifier = Modifier.size(180.dp)) {
        var startAngle = -90f
        entries.forEachIndexed { i, _ ->
            val colorIndex = entries[i].key.ordinal % CategoryColors.size
            drawArc(
                color = CategoryColors[colorIndex],
                startAngle = startAngle,
                sweepAngle = sweepAngles[i],
                useCenter = true,
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngles[i]
        }
    }
}

@Composable
private fun ColumnScope.CategoryLegend(categoryTotals: Map<TransactionCategory, Double>, totalExpenses: Double) {
    categoryTotals.entries.take(6).forEach { (cat, amount) ->
        val colorIndex = cat.ordinal % CategoryColors.size
        val pct = if (totalExpenses > 0) (amount / totalExpenses * 100).toInt() else 0
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).also {}) // color dot via background
                Text("${cat.displayName} ($pct%)", style = MaterialTheme.typography.bodySmall)
            }
            Text("\$${"%.2f".format(amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CategoryBarRow(category: TransactionCategory, amount: Double, maxAmount: Double) {
    val colorIndex = category.ordinal % CategoryColors.size
    val color = CategoryColors[colorIndex]
    val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(category.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = color.copy(alpha = 0.15f))
                drawRect(color = color, size = Size(size.width * fraction, size.height))
            }
        }
        Text("\$${"%.0f".format(amount)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
    }
}
