package com.ledgerai.app.presentation.screens.money

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.LWide
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
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
    val saved: Double = 0.0
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
                val next = payments.maxByOrNull { it.amount }
                val progress = (today.dayOfMonth * 100) / today.lengthOfMonth()
                val score = aiRepo.calculateHealthScore(income, spent, 0.0, adherence).getOrNull()?.score

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
                        saved = s.tx.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                    )
                }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun MoneyOverviewScreen(
    links: AppLinks,
    viewModel: MoneyOverviewViewModel = hiltViewModel(),
    forecastViewModel: ForecastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val forecast by forecastViewModel.uiState.collectAsState()
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
        item(key = "spent") {
            LHero(
                label = "Spent",
                value = money(state.monthSpent),
                sub = "This month",
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClickLabel = "Open spend", role = Role.Button, onClick = { links.money(MoneySeg.Spend) })
            )
        }
        item(key = "safe") {
            LHero(
                label = "Safe today",
                value = guide?.let { money(it.guideAmount) } ?: "—",
                valueColor = if (guide?.status == SpendGuideStatus.OVER) L.Danger else L.OnBox,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClickLabel = "Open safe to spend", role = Role.Button, onClick = links.spendGuide)
            )
        }
        item(key = "saved") {
            LHero(
                label = "Saved",
                value = money(state.saved),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClickLabel = "Open plan", role = Role.Button, onClick = { links.money(MoneySeg.Plan) })
            )
        }
        item(key = "week") { WeekChart(state.week, guide?.guideAmount ?: 0.0, today) }
        item(key = "insights") { InsightStack(state.insights, links) }
        item(key = "pair") {
            val next = state.nextPayment
            LSmallPair(
                left = { m ->
                    LSmallBlock(
                        label = "Forecast",
                        value = if (forecast.isProjectionReady) money(forecast.projectedBalance) else "—",
                        sub = "End of month",
                        valueColor = if (forecast.projectedBalance < 0) L.Danger else L.OnBox,
                        modifier = m,
                        onClick = { links.money(MoneySeg.Plan) }
                    )
                },
                right = { m ->
                    LSmallBlock(
                        label = "Next payment",
                        value = next?.let { money(it.amount) } ?: "—",
                        sub = next?.let { it.name + " · " + if (it.overdue) "Overdue" else dueText(it.due, today) } ?: "Nothing due",
                        valueColor = if (next?.overdue == true) L.Danger else L.OnBox,
                        modifier = m,
                        onClick = { links.money(MoneySeg.Owed) }
                    )
                }
            )
        }
    }
}

private fun dueText(due: LocalDate, today: LocalDate): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days <= 7 -> "In $days days"
        else -> due.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + due.dayOfMonth
    }
}

@Composable
private fun InsightStack(insights: List<BehaviourInsight>, links: AppLinks) {
    val top = insights.firstOrNull() ?: return
    LWide(label = "Insight", onClick = { links.chat(top.headline + "\n" + top.action) }) {
        Text(
            top.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = L.OnBox,
            maxLines = 2
        )
    }
}

@Composable
private fun WeekChart(week: List<DayBar>, guide: Double, today: LocalDate) {
    val chartHeight = 80.dp
    val max = maxOf(week.maxOfOrNull { it.amount } ?: 0.0, guide, 1.0)
    val desc = buildString {
        append("Spend over the last 7 days. ")
        week.forEach {
            append(it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            append(' ')
            append(money(it.amount))
            append(". ")
        }
        if (guide > 0) append("Daily guide ${money(guide)}.")
    }
    LWide(label = "Week") {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = desc },
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        when {
                                            bar.amount <= 0.0 -> L.OnBox.copy(alpha = 0.25f)
                                            over -> L.Danger
                                            else -> L.Gold
                                        }
                                    )
                            )
                        }
                    }
                }
                if (guide > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .offset(y = -(chartHeight * (guide / max).toFloat()))
                            .height(1.dp)
                            .background(L.OnBox.copy(alpha = 0.8f))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { bar ->
                    Text(
                        if (bar.date == today) "•" else bar.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
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
