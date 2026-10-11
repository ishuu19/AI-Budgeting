package com.ledgerai.app.presentation.screens.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ledgerai.app.presentation.theme.CategoryColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

enum class AnalyticsPeriod(val label: String, val months: Long) {
    MONTH("1M", 1),
    QUARTER("3M", 3),
    YEAR("1Y", 12)
}

data class MonthBar(val label: String, val amount: Double)

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
    val previousSpend: Double = 0.0,
    val monthBars: List<MonthBar> = emptyList(),
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
                    val prevStart = start.minusMonths(p.months)
                    val previousSpend = all
                        .filter { it.type == TransactionType.EXPENSE && !it.date.isBefore(prevStart) && it.date.isBefore(start) }
                        .sumOf { it.amount }
                    val bars = (0 until p.months).map { index ->
                        val month = start.plusMonths(index.toLong())
                        val ym = YearMonth.from(month)
                        val amount = expenses.filter { YearMonth.from(it.date) == ym }.sumOf { it.amount }
                        MonthBar(month.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), amount)
                    }

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
                            previousSpend = previousSpend,
                            monthBars = bars,
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

private const val MAX_SLICES = 5

private fun sliceColor(index: Int): Color = CategoryColors[index % CategoryColors.size]

private data class Slice(val name: String, val amount: Double)

/** Top five categories plus one "Other" slice, so the donut never has more than six parts. */
private fun toSlices(entries: List<Map.Entry<TransactionCategory, Double>>): List<Slice> {
    val top = entries.take(MAX_SLICES).map { Slice(it.key.displayName, it.value) }
    val rest = entries.drop(MAX_SLICES).sumOf { it.value }
    return if (rest > 0.0) top + Slice("Other", rest) else top
}

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val entries = state.categoryTotals.entries.toList()
    val total = state.monthlyExpenses

    val delta = if (state.previousSpend > 0.0) {
        val pct = ((total - state.previousSpend) / state.previousSpend * 100).toInt()
        if (pct == 0) null else Delta("${kotlin.math.abs(pct)}% vs prior", up = pct > 0, good = pct < 0)
    } else {
        null
    }

    LScreen(title = "Trends", onBack = onBack) {
        item(key = "period") {
            LSegments(AnalyticsPeriod.entries.toList(), state.period, { it.label }, viewModel::setPeriod)
        }

        if (state.isLoading) {
            item(key = "loading") { LLoading() }
            return@LScreen
        }

        item(key = "hero") {
            LHero(label = "Spent", value = money(total), delta = delta)
        }
        item(key = "pair") {
            LSmallPair(
                left = { modifier ->
                    LSmallBlock(
                        "Savings rate",
                        state.savingsRate?.let { "${"%.0f".format(it)}%" } ?: "—",
                        modifier = modifier
                    )
                },
                right = { modifier ->
                    LSmallBlock("Daily average", money(state.dailyAverageSpend), modifier = modifier)
                }
            )
        }
        state.topMerchant?.let { merchant ->
            item(key = "merchant") {
                LGroup {
                    LGroupRow(title = merchant, trailing = money(state.topMerchantTotal))
                }
            }
        }
        if (state.monthBars.size > 1) {
            item(key = "bars") { MonthBars(state.monthBars) }
        }

        if (entries.isEmpty()) {
            item(key = "empty") { LEmpty(Icons.Filled.PieChart, "No spending") }
            return@LScreen
        }

        val slices = toSlices(entries)
        val sliceColors = slices.indices.map { index ->
            if (slices[index].name == "Other") L.InkMuted else sliceColor(index)
        }
        item(key = "donut") {
            val description = "Spending by category: " + slices.joinToString(", ") { it.name }
            Box(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                LDonut(slices.map { it.amount.toFloat() }, colors = sliceColors)
            }
        }

        item(key = "categories") {
            LGroup {
                entries.forEachIndexed { index, (cat, amount) ->
                    androidx.compose.runtime.key(cat.name) {
                        if (index > 0) LGroupDivider()
                        val pct = if (total > 0) (amount / total * 100).toInt() else 0
                        LGroupRow(
                            title = cat.displayName,
                            sub = "$pct%",
                            trailing = money(amount),
                            end = {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(sliceColor(index))
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthBars(bars: List<MonthBar>) {
    val max = bars.maxOf { it.amount }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((bar.amount / max).toFloat().coerceAtLeast(0.04f))
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = L.Space.x4, topEnd = L.Space.x4))
                            .background(L.Gold)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = L.OnBoxMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

