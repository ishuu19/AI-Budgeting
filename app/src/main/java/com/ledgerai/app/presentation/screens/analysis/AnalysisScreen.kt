package com.ledgerai.app.presentation.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.appusage.AppUsageDao
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.local.room.FocusSessionDao
import com.ledgerai.app.data.local.room.JobApplicationDao
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.subscriptions.SubscriptionDao
import com.ledgerai.app.data.vault.FolderDao
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishStatus
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.TripStatus
import com.ledgerai.app.domain.dates.Trips
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class Stat(val label: String, val value: String, val sub: String = "")
data class Bars(val title: String, val rows: List<Pair<String, Double>>, val format: (Double) -> String)
data class Block(val title: String, val stats: List<Stat> = emptyList(), val bars: List<Bars> = emptyList())
data class Snapshot(val highlights: List<String> = emptyList(), val blocks: List<Block> = emptyList(), val loaded: Boolean = false)

private fun pct(part: Double, whole: Double): Int = if (whole <= 0) 0 else Math.round(part / whole * 100).toInt()
private fun mins(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }
private fun proper(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

/** Reads every part of the app once and turns it into numbers. Money, time, life and phone, side by side. */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val debts: DebtRepository,
    private val goals: GoalRepository,
    private val bills: BillRepository,
    private val subscriptions: SubscriptionDao,
    private val calendar: CalendarRepository,
    private val habits: HabitRepository,
    private val focus: FocusSessionDao,
    private val jobs: JobApplicationDao,
    private val achievements: AchievementDao,
    private val wishes: WishDao,
    private val birthdays: BirthdayDao,
    private val trips: TripDao,
    private val notes: NoteDao,
    private val folders: FolderDao,
    private val people: PeopleRepository,
    private val usage: AppUsageDao,
    private val session: UserSession,
) : ViewModel() {

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun load() {
        viewModelScope.launch { _state.value = runCatching { build() }.getOrDefault(Snapshot(loaded = true)) }
    }

    private suspend fun build(): Snapshot {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val ym = YearMonth.from(today)
        val highlights = mutableListOf<String>()
        val blocks = mutableListOf<Block>()

        // ── Money ──
        val all = transactions.getAllTransactions().first()
        fun spent(m: YearMonth) = all.filter { it.type == TransactionType.EXPENSE && YearMonth.from(it.date) == m }
        fun income(m: YearMonth) = all.filter { it.type == TransactionType.INCOME && YearMonth.from(it.date) == m }.sumOf { it.amount }
        val thisSpent = spent(ym).sumOf { it.amount }
        val lastSpent = spent(ym.minusMonths(1)).sumOf { it.amount }
        val thisIncome = income(ym)
        val daysSoFar = today.dayOfMonth
        val avgDaily = if (daysSoFar > 0) thisSpent / daysSoFar else 0.0
        val projected = avgDaily * ym.lengthOfMonth()
        val savings = if (thisIncome > 0) pct(thisIncome - thisSpent, thisIncome) else null
        val byCategory = spent(ym).groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        val biggest = spent(ym).maxByOrNull { it.amount }
        val weekday = all.filter { it.type == TransactionType.EXPENSE && !it.date.isBefore(today.minusDays(55)) }
            .groupBy { it.date.dayOfWeek }.mapValues { it.value.sumOf { t -> t.amount } / 8.0 }
        val months = (5 downTo 0).map { ym.minusMonths(it.toLong()) }
        val monthlyBills = bills.getTotalMonthlyBills()
        val subs = subscriptions.listActive(session.userInfo.first().userId.ifBlank { null })
        val subMonthly = subs.sumOf { s ->
            val a = s.amount ?: 0.0
            when (s.period.lowercase()) { "year", "yearly", "annual" -> a / 12; "week", "weekly" -> a * 4.33; else -> a }
        }
        val owedToMe = debts.getTotalOwedToMe()
        val iOwe = debts.getTotalIOwe()
        val budgetList = budgets.getBudgetsForMonth(ym.monthValue, ym.year).first()
        val over = budgetList.filter { it.isOverBudget }
        val near = budgetList.filter { !it.isOverBudget && it.isNearLimit }
        val goalList = goals.getAllGoals().first().filter { !it.isCompleted }
        val goalTarget = goalList.sumOf { it.targetAmount }
        val goalSaved = goalList.sumOf { it.savedAmount }

        if (lastSpent > 0 && thisSpent > lastSpent * 1.15) highlights += "You have spent ${pct(thisSpent - lastSpent, lastSpent)}% more than last month."
        else if (lastSpent > 0 && thisSpent < lastSpent * 0.85) highlights += "You are spending ${pct(lastSpent - thisSpent, lastSpent)}% less than last month."
        if (savings != null) highlights += if (savings >= 0) "You are saving $savings% of this month's income." else "You have spent more than you earned this month."
        byCategory.firstOrNull()?.let { if (thisSpent > 0) highlights += "${it.key.displayName} is ${pct(it.value, thisSpent)}% of your spending." }
        weekday.maxByOrNull { it.value }?.let { if (it.value > 0) highlights += "You spend most on ${it.key.getDisplayName(TextStyle.FULL, Locale.getDefault())}s." }
        if (over.isNotEmpty()) highlights += "${over.size} budget${if (over.size > 1) "s are" else " is"} over the limit."

        blocks += Block(
            "Money this month",
            stats = listOf(
                Stat("Spent", money(thisSpent), if (lastSpent > 0) "${if (thisSpent >= lastSpent) "+" else "-"}${pct(Math.abs(thisSpent - lastSpent), lastSpent)}% vs last month" else ""),
                Stat("Income", money(thisIncome)),
                Stat("Net", money(thisIncome - thisSpent), savings?.let { "$it% saved" }.orEmpty()),
                Stat("Daily average", money(avgDaily), "on track for ${money(projected)}"),
                Stat("Biggest expense", biggest?.let { money(it.amount) } ?: "None", biggest?.merchant.orEmpty()),
                Stat("Budgets", "${budgetList.size - over.size - near.size} ok", "${near.size} near, ${over.size} over"),
            ),
            bars = listOf(
                Bars("Where it went", byCategory.take(6).map { it.key.displayName to it.value }) { money(it) },
                Bars("Spending by weekday (average)", DayOfWeek.values().map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to (weekday[it] ?: 0.0) }) { money(it) },
                Bars("Last 6 months spent", months.map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to spent(it).sumOf { t -> t.amount } }) { money(it) },
                Bars("Last 6 months income", months.map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to income(it) }) { money(it) },
            ),
        )
        blocks += Block(
            "Commitments and savings",
            stats = listOf(
                Stat("Bills each month", money(monthlyBills), if (thisIncome > 0) "${pct(monthlyBills, thisIncome)}% of income" else ""),
                Stat("Subscriptions", money(subMonthly), "${subs.size} active, ${money(subMonthly * 12)} a year"),
                Stat("Owed to you", money(owedToMe)),
                Stat("You owe", money(iOwe), "net ${money(owedToMe - iOwe)}"),
                Stat("Goals", "${pct(goalSaved, goalTarget)}%", "${money(goalSaved)} of ${money(goalTarget)}"),
            ),
        )

        // ── Time ──
        val tasks = calendar.observeTasks().first()
        val monthAgo = now.minusDays(30)
        val doneRecent = tasks.count { it.isCompleted && (it.completedAt ?: it.startAt).isAfter(monthAgo) }
        val open = tasks.filter { !it.isCompleted }
        val overdue = open.count { it.hasDate && it.startAt.toLocalDate().isBefore(today) }
        val habitList = habits.observeHabits().first()
        val habitDone = habits.observeDone(today.minusDays(29), today).first().size
        val habitRate = if (habitList.isEmpty()) 0 else pct(habitDone.toDouble(), habitList.size * 30.0)
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
        val sessions = focus.listForSync(0).filter { it.endedAt != null && !it.startedAt.isBefore(weekStart) }
        val focusMs = sessions.sumOf { Duration.between(it.startedAt, it.endedAt).toMillis() }
        val events = calendar.observeAll().first().filter { it.kind != CalendarEventKind.TASK && it.kind != CalendarEventKind.ALARM }
        val eventsThisWeek = events.count { !it.startAt.isBefore(weekStart) && it.startAt.isBefore(weekStart.plusDays(7)) }
        val appointmentsAhead = events.count { it.kind == CalendarEventKind.APPOINTMENT && it.startAt.isAfter(now) && it.status != "CANCELLED" }
        val jobList = jobs.observeAll().first()
        if (overdue > 0) highlights += "$overdue task${if (overdue > 1) "s are" else " is"} overdue."
        blocks += Block(
            "Time and habits",
            stats = listOf(
                Stat("Tasks done", "$doneRecent", "last 30 days"),
                Stat("Open tasks", "${open.size}", "$overdue overdue"),
                Stat("Habits", "$habitRate%", "${habitList.size} habits, last 30 days"),
                Stat("Focus", mins(focusMs), "${sessions.size} sessions this week"),
                Stat("Events this week", "$eventsThisWeek"),
                Stat("Appointments ahead", "$appointmentsAhead"),
            ),
            bars = if (jobList.isEmpty()) emptyList() else listOf(
                Bars("Job applications", jobList.groupBy { it.status }.map { proper(it.key.name) to it.value.size.toDouble() }) { it.toInt().toString() }
            ),
        )

        // ── Life ──
        val ach = achievements.observeActive().first()
        val wishList = wishes.observeActive().first().filter { it.status == WishStatus.WANT || it.status == WishStatus.SAVING }
        val bds = birthdays.listActive()
        val upcomingBirthdays = bds.count { java.time.temporal.ChronoUnit.DAYS.between(now, BirthdayLadder.nextCelebration(it.month, it.day, it.celebrateMinute, now)) in 0..30 }
        val tripList = trips.listActive()
        val tripsAhead = tripList.count { Trips.status(it.startDate, it.endDate, today) != TripStatus.DONE }
        val oldestWish = wishList.minByOrNull { it.createdAt }
        val noteCount = notes.observeAll().first().size
        blocks += Block(
            "Life",
            stats = listOf(
                Stat("Achievements", "${ach.count { it.happenedOn.year == today.year }}", "this year, ${ach.size} in total"),
                Stat("Wishes", "${wishList.size}", "${money(wishList.sumOf { it.amount ?: 0.0 })} known value"),
                Stat("Oldest wish", oldestWish?.let { "${(System.currentTimeMillis() - it.createdAt) / 86_400_000L} days" } ?: "None", oldestWish?.name.orEmpty()),
                Stat("Birthdays", "$upcomingBirthdays", "in the next 30 days, ${bds.size} saved"),
                Stat("Trips ahead", "$tripsAhead", "${tripList.size} in total"),
                Stat("Notes and folders", "$noteCount", "${folders.observeActive().first().size} folders, ${people.listPeople(session.userInfo.first().userId.ifBlank { com.ledgerai.app.data.dates.LOCAL_OWNER }).size} people"),
            ),
        )

        // ── Phone ──
        val week = usage.observeTotals(today.minusDays(6), today).first()
        val prev = usage.observeTotals(today.minusDays(13), today.minusDays(7)).first()
        val todayTotals = usage.observeTotals(today, today).first()
        val weekMs = week.sumOf { it.foregroundMs }
        val prevMs = prev.sumOf { it.foregroundMs }
        if (prevMs > 0 && weekMs > prevMs * 1.15) highlights += "Phone time is up ${pct((weekMs - prevMs).toDouble(), prevMs.toDouble())}% on last week."
        if (week.isNotEmpty()) blocks += Block(
            "Phone",
            stats = listOf(
                Stat("Today", mins(todayTotals.sumOf { it.foregroundMs })),
                Stat("Daily average", mins(weekMs / 7), "last 7 days"),
                Stat("Top app", week.first().label, mins(week.first().foregroundMs)),
                Stat("Vs last week", if (prevMs > 0) "${if (weekMs >= prevMs) "+" else "-"}${pct(Math.abs(weekMs - prevMs).toDouble(), prevMs.toDouble())}%" else "Not enough data"),
            ),
            bars = listOf(Bars("Top apps this week", week.take(5).map { it.label to it.foregroundMs / 60_000.0 }) { mins((it * 60_000).toLong()) }),
        )
        return Snapshot(highlights.take(6), blocks, loaded = true)
    }
}

@Composable
fun AnalysisScreen(onBack: () -> Unit, vm: AnalysisViewModel = hiltViewModel()) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }
    val s by vm.state.collectAsState()
    LScreen(title = "Analysis", onBack = onBack, itemSpacing = 12.dp) {
        if (!s.loaded) { item { Text("Reading your data…", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }; return@LScreen }
        if (s.highlights.isNotEmpty()) {
            item { Heading("Highlights") }
            s.highlights.forEach { h -> item { Text(h, style = MaterialTheme.typography.bodyLarge, color = L.Ink) } }
        }
        s.blocks.forEach { b ->
            item { Heading(b.title) }
            b.stats.chunked(2).forEach { pair ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { StatBox(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            b.bars.filter { it.rows.any { r -> r.second > 0 } }.forEach { bar -> barBlock(bar) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = L.Ink, modifier = Modifier.padding(top = 20.dp))
}

@Composable
private fun StatBox(s: Stat, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 92.dp).clip(shape).background(L.BoxDeep).border(1.dp, L.Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(s.label, style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        Text(s.value, style = MaterialTheme.typography.titleLarge, color = L.Ink, maxLines = 1)
        Text(if (s.sub.isBlank()) " " else s.sub, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 2)
    }
}

private fun LazyListScope.barBlock(b: Bars) {
    item { Text(b.title, style = MaterialTheme.typography.labelLarge, color = L.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
    val top = b.rows.maxOf { it.second }.coerceAtLeast(0.0001)
    b.rows.forEach { (label, v) ->
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = L.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(b.format(v), style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                }
                Row(Modifier.fillMaxWidth().height(4.dp).background(L.Line)) {
                    Row(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(L.Accent)) {}
                }
            }
        }
    }
}
