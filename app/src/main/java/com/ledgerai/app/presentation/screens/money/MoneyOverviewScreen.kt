package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.finance.SpendGuideResult
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.data.insight.InsightEngine
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.insight.BehaviourInsight
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.Delta
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LWide
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.theme.CategoryColors
import com.ledgerai.app.presentation.navigation.MoneySeg
import com.ledgerai.app.presentation.screens.forecast.ForecastViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class DayBar(val date: LocalDate, val amount: Double)

data class NextPayment(val name: String, val amount: Double, val due: LocalDate, val overdue: Boolean)

data class OverviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val guide: SpendGuideResult? = null,
    val monthProgress: Int = 0,
    val healthScore: Int? = null,
    val week: List<DayBar> = emptyList(),
    val insights: List<BehaviourInsight> = emptyList(),
    val nextPayment: NextPayment? = null,
    val monthSpent: Double = 0.0,
    /** This month's income minus spending. */
    val saved: Double = 0.0,
    val savedRate: Int? = null,
    val lastSavedRate: Int? = null,
    val spendDeltaPct: Int? = null,
    val priorWeek: Double = 0.0,
    val topCategories: List<Pair<String, Double>> = emptyList()
)

@HiltViewModel
class MoneyOverviewViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val billRepo: BillRepository,
    private val debtRepo: DebtRepository,
    private val guideRepo: SpendGuideRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()
    private val retry = MutableStateFlow(0)

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        retry.update { it + 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source = combine(todayTicker(), retry) { today, _ -> today }.flatMapLatest { today ->
        combine(
            transactionRepo.getAllTransactions(),
            budgetRepo.getBudgetsForMonth(today.monthValue, today.year),
            billRepo.getActiveBills(),
            debtRepo.getActiveDebts()
        ) { tx, budgets, bills, debts -> Sources(today, tx, budgets, bills, debts) }
    }

    private class Sources(
        val today: LocalDate,
        val tx: List<com.ledgerai.app.domain.model.Transaction>,
        val budgets: List<com.ledgerai.app.domain.model.Budget>,
        val bills: List<com.ledgerai.app.domain.model.Bill>,
        val debts: List<com.ledgerai.app.domain.model.Debt>
    )

    init {
        viewModelScope.launch {
            source.collect { s ->
                val today = s.today
                val month = s.tx.filter { it.date.year == today.year && it.date.monthValue == today.monthValue }
                val expenses = month.filter { it.type == TransactionType.EXPENSE }
                val income = month.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val spent = expenses.sumOf { it.amount }
                val spentByCategory = expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
                val adherence = if (s.budgets.isEmpty()) 100.0 else
                    s.budgets.count { (spentByCategory[it.category] ?: 0.0) <= it.monthlyLimit }.toDouble() / s.budgets.size * 100
                val week = (6 downTo 0).map { back ->
                    val day = today.minusDays(back.toLong())
                    DayBar(day, s.tx.filter { it.type == TransactionType.EXPENSE && it.date == day }.sumOf { it.amount })
                }
                val insights = InsightEngine.rankForHome(InsightEngine.detect(s.tx, today))
                val horizon = today.plusDays(30)
                val payments = s.bills.filter { !it.nextDueDate.isAfter(horizon) }
                    .map { NextPayment(it.name, it.amount, it.nextDueDate, it.nextDueDate.isBefore(today)) } +
                    s.debts.filter { it.direction == DebtDirection.I_OWE && it.dueDate != null && !it.dueDate.isAfter(horizon) }
                        .map { NextPayment(it.friendName, it.amount, it.dueDate!!, it.dueDate.isBefore(today)) }
                val next = payments.minByOrNull { it.due }
                val progress = (today.dayOfMonth * 100) / today.lengthOfMonth()
                val owed = s.debts.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.amount }
                val score = aiRepo.calculateHealthScore(income, spent, owed, adherence).getOrNull()?.score
                val lastAnchor = today.minusMonths(1)
                val lastCut = lastAnchor.withDayOfMonth(minOf(today.dayOfMonth, lastAnchor.lengthOfMonth()))
                val lastWindow = s.tx.filter { !it.date.isBefore(lastAnchor.withDayOfMonth(1)) && !it.date.isAfter(lastCut) }
                val lastIncome = lastWindow.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val lastSpent = lastWindow.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val priorWeek = (13 downTo 7).sumOf { back ->
                    val day = today.minusDays(back.toLong())
                    s.tx.filter { it.type == TransactionType.EXPENSE && it.date == day }.sumOf { it.amount }
                }
                val top = spentByCategory.entries.sortedByDescending { it.value }.take(3)
                    .map { it.key.displayName to it.value }

                val guideResult = runCatching { guideRepo.computeTodayGuide(today) }
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (guideResult.isFailure) "Could not load" else null,
                        guide = guideResult.getOrNull() ?: it.guide,
                        monthProgress = progress,
                        healthScore = score,
                        week = week,
                        insights = insights,
                        nextPayment = next,
                        monthSpent = spent,
                        saved = income - spent,
                        savedRate = savingsRate(income, spent),
                        lastSavedRate = savingsRate(lastIncome, lastSpent),
                        spendDeltaPct = if (lastSpent > 0.0) ((spent - lastSpent) / lastSpent * 100).toInt() else null,
                        priorWeek = priorWeek,
                        topCategories = top
                    )
                }
            }
        }
    }
}

private fun savingsRate(income: Double, spent: Double): Int? =
    if (income > 0.0) ((income - spent) / income * 100).toInt() else null

private fun spendDelta(pct: Int): Delta? =
    if (pct == 0) null else Delta("${kotlin.math.abs(pct)}% vs last month", up = pct > 0, good = pct < 0)

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MoneyOverviewScreen(
    links: AppLinks,
    viewModel: MoneyOverviewViewModel = hiltViewModel(),
    forecastViewModel: ForecastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val forecast by forecastViewModel.uiState.collectAsState()
    val subscriptions by hiltViewModel<SubscriptionTotalViewModel>().monthly.collectAsState()
    val today = rememberToday()
    val guide = state.guide

    LScreen(title = "Overview") {
        if (state.isLoading) {
            item(key = "loading") { LLoading() }
            return@LScreen
        }
        if (state.error != null && guide == null) {
            item(key = "error") { LError(state.error ?: "Could not load", onRetry = viewModel::retry) }
        }
        item(key = "safe") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LHero(
                    label = "Safe to spend",
                    value = guide?.let { money(it.guideAmount) } ?: "—",
                    sub = "Spent ${money(state.monthSpent)} this month",
                    delta = state.spendDeltaPct?.let { spendDelta(it) },
                    valueColor = if (guide?.status == SpendGuideStatus.OVER) L.Danger else L.OnBox,
                    modifier = Modifier
                        .clip(RoundedCornerShape(L.Radius))
                        .clickable(onClickLabel = "Open safe to spend", role = Role.Button, onClick = links.spendGuide)
                )
                LProgress(state.monthProgress / 100f)
                state.healthScore?.let { score -> HealthRing(score) }
            }
        }
        item(key = "week") {
            WeekChart(state.week, state.priorWeek, guide?.guideAmount ?: 0.0, today)
        }
        if (state.topCategories.isNotEmpty()) {
            item(key = "categories") { CategoryStack(state.topCategories, onClick = links.insights) }
        }
        item(key = "insights") { InsightStack(state.insights, links) }
        item(key = "tiles") {
            val next = state.nextPayment
            val savedSub = buildString {
                append("Income minus spending")
                state.savedRate?.let { rate ->
                    append(" · $rate%")
                    state.lastSavedRate?.let { append(" vs $it%") }
                }
            }
            LGroup {
                OverviewRow(
                    title = "Forecast",
                    sub = "End of month",
                    value = if (forecast.isProjectionReady) money(forecast.projectedBalance) else "—",
                    valueColor = if (forecast.isProjectionReady && forecast.projectedBalance < 0) L.Danger else L.OnBox,
                    onClick = { links.money(MoneySeg.Plan) }
                )
                LGroupDivider()
                OverviewRow(
                    title = "Next payment",
                    sub = next?.let { it.name + " · " + dueLabel(it.due, today) } ?: "Nothing due",
                    value = next?.let { money(it.amount) } ?: "—",
                    valueColor = if (next?.overdue == true) L.Danger else L.OnBox,
                    onClick = { links.money(MoneySeg.Owed) }
                )
                LGroupDivider()
                OverviewRow(
                    title = "Saved",
                    sub = savedSub,
                    value = money(state.saved),
                    valueColor = L.OnBox,
                    onClick = { links.money(MoneySeg.Plan) }
                )
                LGroupDivider()
                OverviewRow(
                    title = "Subscriptions",
                    sub = null,
                    value = subscriptionFigure(subscriptions),
                    valueColor = L.OnBox,
                    onClick = links.subscriptions
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(title: String, sub: String?, value: String?, valueColor: Color, onClick: () -> Unit) {
    LGroupRow(
        title = title,
        sub = sub,
        trailing = value,
        trailingColor = valueColor,
        onClick = onClick,
        end = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = L.OnBoxMuted) }
    )
}

@Composable
private fun HealthRing(score: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(Modifier.size(36.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(L.BoxDeep, 0f, 360f, useCenter = false, style = stroke)
            drawArc(L.Gold, -90f, 360f * (score / 100f), useCenter = false, style = stroke)
        }
        Text("$score", style = MaterialTheme.typography.titleSmall, color = L.OnBox)
    }
}

@Composable
private fun InsightStack(insights: List<BehaviourInsight>, links: AppLinks) {
    val top = insights.firstOrNull() ?: return
    LWide(label = "Insight") {
        Text(
            top.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = L.OnBox,
            maxLines = 2
        )
        if (top.action.isNotBlank()) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(L.RadiusPill)
                    .clickable(role = Role.Button, onClick = { links.chat(top.action) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = L.Highlight, modifier = Modifier.size(16.dp))
                Text(
                    top.action,
                    style = MaterialTheme.typography.labelLarge,
                    color = L.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CategoryStack(parts: List<Pair<String, Double>>, onClick: () -> Unit) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1.0)
    LWide(label = "Where it went", onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().height(L.Space.x8).clip(L.RadiusPill)
        ) {
            parts.forEachIndexed { index, (_, amount) ->
                Box(
                    Modifier
                        .weight((amount / total).toFloat().coerceAtLeast(0.04f))
                        .fillMaxHeight()
                        .background(CategoryColors[index % CategoryColors.size])
                )
            }
        }
        Text(
            parts.joinToString(" · ") { it.first },
            style = MaterialTheme.typography.bodySmall,
            color = L.OnBoxMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun WeekChart(week: List<DayBar>, prior: Double, guide: Double, today: LocalDate) {
    val chartHeight = 80.dp
    val max = maxOf(week.maxOfOrNull { it.amount } ?: 0.0, guide, 1.0)
    val weekTotal = week.sumOf { it.amount }
    val desc = buildString {
        append("Spend over the last 7 days, ${money(weekTotal)}, versus prior 7 days ${money(prior)}. ")
        week.forEach {
            append(it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            append(' ')
            append(money(it.amount))
            append(". ")
        }
        if (guide > 0) append("Guide ${money(guide)}.")
    }
    LWide(label = "${money(weekTotal)} · vs prior ${money(prior)}") {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = desc },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(chartHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEach { bar ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            val frac = (bar.amount / max).toFloat().coerceAtLeast(0.03f)
                            val over = guide > 0 && bar.amount > guide
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(frac)
                                    .clip(RoundedCornerShape(topStart = L.Space.x4, topEnd = L.Space.x4))
                                    .background(
                                        when {
                                            bar.amount <= 0.0 -> L.BoxDeep
                                            over -> L.Danger
                                            else -> L.Gold
                                        }
                                    )
                            )
                        }
                    }
                }
                if (guide > 0) {
                    val guideFrac = (guide / max).toFloat().coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height * (1f - guideFrac)
                        drawLine(
                            color = L.InkMuted,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                    Text(
                        "guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = L.InkMuted,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { bar ->
                    Text(
                        bar.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.date == today) L.Gold else L.OnBoxMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
