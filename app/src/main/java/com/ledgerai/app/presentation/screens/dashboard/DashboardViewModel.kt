package com.ledgerai.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.InsightDto
import com.ledgerai.app.data.insight.InsightEngine
import com.ledgerai.app.domain.insight.BehaviourInsight
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.FinancialHealthScore
import com.ledgerai.app.presentation.components.LCurrency
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Dashboard card for validated AI insight type (info | watch | alert). */
data class AiInsightCardState(
    val title: String,
    val body: String,
    val severity: String = "info",
    val actions: List<String> = emptyList(),
) {
    val chatContext: String
        get() = buildString {
            append(title)
            if (body.isNotBlank()) {
                append('\n')
                append(body)
            }
        }
}

data class MonthTrendPoint(
    val label: String,
    val income: Double,
    val expenses: Double
)

data class UpcomingItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: UpcomingKind
)

enum class UpcomingKind { EVENT, BILL }

data class DashboardUiState(
    val isLoading: Boolean = true,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val netBalance: Double = 0.0,
    val savingsRate: Double = 0.0,
    val remainingBudget: Double? = null,
    val takeaway: String = "",
    val recentTransactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val categoryTotals: Map<TransactionCategory, Double> = emptyMap(),
    val incomeExpenseTrend: List<MonthTrendPoint> = emptyList(),
    val upcoming: List<UpcomingItem> = emptyList(),
    /** Local heuristic / chat context string. */
    val aiInsight: String = "",
    /** Validated insight type card (info | watch | alert). */
    val aiInsightCard: AiInsightCardState? = null,
    val behaviourInsights: List<BehaviourInsight> = emptyList(),
    val weekSpend: List<Double> = emptyList(),
    val healthScore: FinancialHealthScore? = null,
    val greeting: String = "Hello!",
    val currentMonth: String = "",
    val userName: String = "",
    val trackExpensesOnly: Boolean = false,
    val cashOnHand: Double? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val aiRepo: AiRepository,
    private val calendarRepo: CalendarRepository,
    private val billRepo: BillRepository,
    private val userSession: UserSession,
    private val prefs: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val now = LocalDate.now()

    init {
        loadDashboard()
        loadProfileAndQuote()
    }

    private fun loadProfileAndQuote() {
        viewModelScope.launch {
            combine(prefs.trackMode, prefs.cashOnHand) { mode, cash ->
                mode to cash.toDoubleOrNull()
            }.collect { (mode, cash) ->
                _uiState.update {
                    it.copy(trackExpensesOnly = mode == "expenses", cashOnHand = cash)
                }
            }
        }
        viewModelScope.launch {
            userSession.userInfo.collect { info ->
                val first = info.displayName.trim().substringBefore(' ')
                _uiState.update { it.copy(userName = first) }
            }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            val monthName = now.month.getDisplayName(TextStyle.FULL, Locale.US)
            _uiState.update { it.copy(currentMonth = "$monthName ${now.year}") }

            val financeFlow = combine(
                transactionRepo.getRecentTransactions(8),
                budgetRepo.getBudgetsForMonth(now.monthValue, now.year),
                transactionRepo.getTransactionsForMonth(now.year, now.monthValue)
            ) { recent, budgets, monthTx ->
                Triple(recent, budgets, monthTx)
            }

            val upcomingFlow = combine(
                calendarRepo.observeRange(now, now.plusDays(UPCOMING_DAYS)),
                billRepo.getActiveBills()
            ) { events, bills ->
                events to bills
            }

            combine(financeFlow, upcomingFlow) { finance, upcomingSources ->
                finance to upcomingSources
            }.collect { (finance, upcomingSources) ->
                val (recent, budgets, monthTx) = finance
                val (events, bills) = upcomingSources

                val income = transactionRepo.getTotalIncomeForMonth(now.year, now.monthValue)
                val expenses = transactionRepo.getTotalExpensesForMonth(now.year, now.monthValue)
                val budgetsWithSpending = budgets.map { budget ->
                    val spent = transactionRepo.getSpendingForCategoryMonth(
                        budget.category, now.year, now.monthValue
                    )
                    budget.copy(spent = spent)
                }
                val net = income - expenses
                val savingsRate = if (income > 0) {
                    ((net / income) * 100.0).coerceIn(-999.0, 999.0)
                } else {
                    0.0
                }
                val remaining = if (budgetsWithSpending.isNotEmpty()) {
                    budgetsWithSpending.sumOf { it.remaining }
                } else {
                    null
                }
                val adherence = if (budgetsWithSpending.isNotEmpty()) {
                    budgetsWithSpending.count { !it.isOverBudget }.toDouble() /
                        budgetsWithSpending.size * 100
                } else {
                    100.0
                }

                val categoryTotals = monthTx
                    .filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .toMap()

                val trend = buildIncomeExpenseTrend()
                val upcoming = buildUpcoming(events, bills)
                val allTx = transactionRepo.getAllTransactions().first()
                val behaviour = InsightEngine.rankForHome(InsightEngine.detect(allTx, now))
                val weekSpend = (6 downTo 0).map { offset ->
                    val day = now.minusDays(offset.toLong())
                    allTx.filter { it.type == TransactionType.EXPENSE && it.date == day }.sumOf { it.amount }
                }
                val localInsight = buildInsight(income, expenses, savingsRate, categoryTotals, upcoming)
                val cached = if (aiRepo.insightIsStale()) null else aiRepo.cachedInsight()?.toCardState()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthlyIncome = income,
                        monthlyExpenses = expenses,
                        netBalance = net,
                        savingsRate = savingsRate,
                        remainingBudget = remaining,
                        takeaway = buildTakeaway(net, remaining, savingsRate),
                        recentTransactions = recent,
                        budgets = budgetsWithSpending,
                        categoryTotals = categoryTotals,
                        incomeExpenseTrend = trend,
                        upcoming = upcoming,
                        behaviourInsights = behaviour,
                        weekSpend = weekSpend,
                        aiInsight = cached?.chatContext ?: localInsight,
                        aiInsightCard = cached ?: AiInsightCardState(
                            title = "Today's tip",
                            body = capWords(localInsight, 30),
                            severity = "info",
                        ),
                        greeting = getGreeting()
                    )
                }

                loadHealthScore(income, expenses, adherence)
                refreshAiInsight()
            }
        }
    }

    private fun refreshAiInsight() {
        viewModelScope.launch {
            aiRepo.generateDailyInsight(forceRefresh = aiRepo.insightIsStale()).onSuccess { dto ->
                val card = dto.toCardState() ?: return@onSuccess
                _uiState.update {
                    it.copy(
                        aiInsightCard = card,
                        aiInsight = card.chatContext,
                    )
                }
            }
        }
    }

    private fun InsightDto.toCardState(): AiInsightCardState? {
        val title = title?.takeIf { it.isNotBlank() } ?: return null
        val body = body?.takeIf { it.isNotBlank() } ?: return null
        val severity = when (severity?.lowercase()) {
            "alert" -> "alert"
            "watch", "warn", "warning" -> "watch"
            "tip" -> "tip"
            else -> "info"
        }
        return AiInsightCardState(
            title = capWords(title, 8),
            body = capWords(body, 30),
            severity = severity,
            actions = actions.orEmpty().take(5),
        )
    }

    private suspend fun buildIncomeExpenseTrend(): List<MonthTrendPoint> {
        val current = YearMonth.from(now)
        return (5 downTo 0).map { offset ->
            val ym = current.minusMonths(offset.toLong())
            MonthTrendPoint(
                label = ym.month.getDisplayName(TextStyle.SHORT, Locale.US),
                income = transactionRepo.getTotalIncomeForMonth(ym.year, ym.monthValue),
                expenses = transactionRepo.getTotalExpensesForMonth(ym.year, ym.monthValue)
            )
        }
    }

    private fun buildUpcoming(
        events: List<CalendarEvent>,
        bills: List<Bill>
    ): List<UpcomingItem> {
        val horizon = now.plusDays(UPCOMING_DAYS)
        val nowTime = java.time.LocalDateTime.now()
        val eventItems = events
            .filter { !(it.isCompleted && it.kind == CalendarEventKind.TASK) }
            .filter { !it.startAt.isBefore(nowTime.minusMinutes(30)) || it.allDay }
            .sortedBy { it.startAt }
            .take(5)
            .map {
                UpcomingItem(
                    id = "event-${it.id}",
                    title = it.title.ifBlank { it.kind.label() },
                    subtitle = "${it.kind.label()} · ${it.startAt.toLocalDate()}" +
                        if (it.allDay) "" else " ${it.startAt.toLocalTime().withSecond(0).withNano(0)}",
                    kind = UpcomingKind.EVENT
                )
            }
        val billItems = bills
            .filter { !it.nextDueDate.isAfter(horizon) }
            .sortedBy { it.nextDueDate }
            .take(4)
            .map {
                UpcomingItem(
                    id = "bill-${it.id}",
                    title = it.name,
                    subtitle = "Bill · ${it.nextDueDate} · ${LCurrency.symbol}${"%.0f".format(it.amount)}",
                    kind = UpcomingKind.BILL
                )
            }
        return (eventItems + billItems).take(8)
    }

    private fun buildInsight(
        income: Double,
        expenses: Double,
        savingsRate: Double,
        categoryTotals: Map<TransactionCategory, Double>,
        upcoming: List<UpcomingItem>
    ): String {
        val top = categoryTotals.entries.firstOrNull()
        val upcomingHint = upcoming.firstOrNull()?.let { " Next up: ${it.title}." }.orEmpty()
        return when {
            income <= 0.0 && expenses <= 0.0 ->
                "Log income and expenses to unlock a personalized tip.$upcomingHint"
            savingsRate >= 20 ->
                "You're saving ${"%.0f".format(savingsRate)}% this month — keep that pace.$upcomingHint"
            savingsRate >= 0 ->
                if (top != null) {
                    "Savings rate ${"%.0f".format(savingsRate)}%. ${top.key.displayName} leads spending at ${LCurrency.symbol}${"%.0f".format(top.value)}.$upcomingHint"
                } else {
                    "Savings rate ${"%.0f".format(savingsRate)}% — room to grow your surplus.$upcomingHint"
                }
            else ->
                if (top != null) {
                    "Spending exceeds income. Watch ${top.key.displayName} (${LCurrency.symbol}${"%.0f".format(top.value)}).$upcomingHint"
                } else {
                    "Spending exceeds income this month — chat for a cut plan.$upcomingHint"
                }
        }
    }

    private fun buildTakeaway(net: Double, remainingBudget: Double?, savingsRate: Double): String {
        return when {
            remainingBudget != null && net >= 0 ->
                "Saving ${"%.0f".format(savingsRate)}% · ${LCurrency.symbol}${"%.0f".format(remainingBudget)} left in budgets"
            remainingBudget != null && net < 0 ->
                "Savings ${"%.0f".format(savingsRate)}% · ${LCurrency.symbol}${"%.0f".format(remainingBudget)} still in budgets"
            net >= 0 ->
                "Saving ${"%.0f".format(savingsRate)}% of income this month"
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

    private fun capWords(text: String, maxWords: Int): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= maxWords) return words.joinToString(" ")
        return words.take(maxWords).joinToString(" ")
    }

    private fun CalendarEventKind.label(): String = when (this) {
        CalendarEventKind.TASK -> "Task"
        CalendarEventKind.EXAM -> "Exam"
        CalendarEventKind.CLASS -> "Class"
        CalendarEventKind.ROUTINE -> "Routine"
        CalendarEventKind.ALARM -> "Alarm"
        else -> "Event"
    }

    private fun getGreeting(): String {
        return when (java.time.LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Hello"
        }
    }

    private companion object {
        const val UPCOMING_DAYS = 14L
    }
}
