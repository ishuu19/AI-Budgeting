package com.ledgerai.app.presentation.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.data.local.room.PlanBlockDao
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.Quote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class TodayKind { Event, Class, Task, Alarm, Study, Habit, Bill }

/** One thing on the Today timeline. [id] is the event, block or bill id depending on [kind]. */
data class TodayItem(
    val kind: TodayKind,
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val topic: String = ""
) {
    /** Items that occupy time and can be the hero. */
    val isBlock: Boolean
        get() = !allDay && kind in setOf(TodayKind.Event, TodayKind.Class, TodayKind.Study, TodayKind.Habit) && end.isAfter(start)
}

data class TodayUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Today and tomorrow, sorted by start. */
    val items: List<TodayItem> = emptyList(),
    val overdueTasks: Int = 0,
    val dueTasks: Int = 0,
    val billsDue: Int = 0,
    val billsOverdue: Int = 0,
    val logGaps: Int = 0,
    val gapHours: Double = 0.0,
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    val guide: Double? = null,
    val spentToday: Double = 0.0,
    val guideStatus: SpendGuideStatus? = null,
    val quote: Quote = Quote(text = "")
)

private data class Key(val day: LocalDate, val reload: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val planRepo: PlanRepository,
    private val planBlockDao: PlanBlockDao,
    private val billRepo: BillRepository,
    private val lifeLogRepo: LifeLogRepository,
    private val spendGuideRepo: SpendGuideRepository,
    private val transactionRepo: TransactionRepository,
    private val quoteRepo: QuoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val key = MutableStateFlow(Key(LocalDate.now(), 0))

    init {
        viewModelScope.launch {
            key.flatMapLatest { k -> observe(k.day) }
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Could not load Today") }
                }
                .collect { s -> _state.value = s.copy(quote = _state.value.quote) }
        }
    }

    /** Call when the minute ticker crosses midnight. */
    fun setDay(day: LocalDate) {
        if (key.value.day != day) key.update { it.copy(day = day) }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        key.update { it.copy(reload = it.reload + 1) }
    }

    private fun observe(today: LocalDate) = flow<TodayUiState> {
        runCatching { lifeLogRepo.markExpiredGaps(LocalDateTime.now()) }
        val quote = withContext(Dispatchers.IO) {
            runCatching { quoteRepo.todaysQuote(today) }.getOrDefault(Quote(text = ""))
        }
        _state.update { it.copy(quote = quote) }

        val tomorrow = today.plusDays(1)
        val schedule = combine(
            calendarRepo.observeRange(today, tomorrow),
            planRepo.observeBlocks(),
            calendarRepo.observeTasks(),
            billRepo.getActiveBills()
        ) { events, blocks, tasks, bills -> Sources(events, blocks, tasks, bills) }

        val money = combine(
            transactionRepo.getTransactionsForMonth(today.year, today.monthValue),
            lifeLogRepo.observeDay(today)
        ) { _, windows -> windows }

        emitAll(combine(schedule, money) { src, windows -> build(today, src, windows) })
    }

    private data class Sources(
        val events: List<com.ledgerai.app.domain.model.CalendarEvent>,
        val blocks: List<com.ledgerai.app.domain.model.PlanBlock>,
        val tasks: List<com.ledgerai.app.domain.model.CalendarEvent>,
        val bills: List<com.ledgerai.app.domain.model.Bill>
    )

    private suspend fun build(
        today: LocalDate,
        src: Sources,
        windows: List<com.ledgerai.app.data.local.room.CheckinWindowEntity>
    ): TodayUiState {
        val tomorrow = today.plusDays(1)
        val items = mutableListOf<TodayItem>()

        src.events
            .filter { it.isEnabled && !(it.kind == CalendarEventKind.TASK && it.isCompleted) }
            .filter { it.kind != CalendarEventKind.TASK || it.hasDate }
            .forEach { e ->
                val kind = when (e.kind) {
                    CalendarEventKind.CLASS -> TodayKind.Class
                    CalendarEventKind.TASK -> TodayKind.Task
                    CalendarEventKind.ALARM -> TodayKind.Alarm
                    else -> TodayKind.Event
                }
                items += TodayItem(
                    kind = kind,
                    id = e.masterId,
                    title = e.title.ifBlank { "Untitled" },
                    start = e.startAt,
                    end = if (e.endAt.isBefore(e.startAt)) e.startAt else e.endAt,
                    allDay = e.allDay
                )
            }

        src.blocks
            .filter {
                (it.status == PlanBlockStatus.SCHEDULED || it.status == PlanBlockStatus.IN_PROGRESS) &&
                    (it.startAt.toLocalDate() == today || it.startAt.toLocalDate() == tomorrow)
            }
            .forEach { b ->
                items += TodayItem(
                    kind = if (b.kind == PlanBlockKind.HABIT) TodayKind.Habit else TodayKind.Study,
                    id = b.id,
                    title = b.title.ifBlank { b.topic.ifBlank { "Focus" } },
                    start = b.startAt,
                    end = b.endAt,
                    topic = b.topic.ifBlank { b.title }
                )
            }

        src.bills
            .filter { it.nextDueDate == today || it.nextDueDate == tomorrow }
            .forEach { b ->
                items += TodayItem(
                    kind = TodayKind.Bill,
                    id = b.id,
                    title = b.name,
                    start = b.nextDueDate.atStartOfDay(),
                    end = b.nextDueDate.atStartOfDay(),
                    allDay = true
                )
            }

        val openTasks = src.tasks.filter { !it.isCompleted && it.hasDate }
        val overdue = openTasks.count { it.startAt.toLocalDate().isBefore(today) }
        val due = openTasks.count { !it.startAt.toLocalDate().isAfter(today) }

        val billsDue = src.bills.count { !it.nextDueDate.isBefore(today) && !it.nextDueDate.isAfter(today.plusDays(7)) }
        val billsOverdue = src.bills.count { it.nextDueDate.isBefore(today) }

        val gaps = windows.filter { it.state == CheckinWindowState.GAP }
        val gapHours = gaps.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() } / 60.0

        val habitBlocks = src.blocks.filter { it.kind == PlanBlockKind.HABIT && it.startAt.toLocalDate() == today }
        val habitsDone = habitBlocks.count { it.status == PlanBlockStatus.DONE }

        val guide = runCatching { spendGuideRepo.computeTodayGuide(today) }.getOrNull()
        val spent = if (guide != null) guideSpent(today) else 0.0

        return TodayUiState(
            isLoading = false,
            items = items.sortedWith(compareBy({ !it.allDay }, { it.start })),
            overdueTasks = overdue,
            dueTasks = due,
            billsDue = billsDue,
            billsOverdue = billsOverdue,
            logGaps = gaps.size,
            gapHours = gapHours,
            habitsDone = habitsDone,
            habitsTotal = habitBlocks.size,
            guide = guide?.guideAmount?.coerceAtLeast(0.0),
            spentToday = spent,
            guideStatus = guide?.status
        )
    }

    private suspend fun guideSpent(today: LocalDate): Double =
        runCatching {
            transactionRepo.getTransactionsForMonth(today.year, today.monthValue)
                .first()
                .filter { it.date == today && it.type == com.ledgerai.app.domain.model.TransactionType.EXPENSE }
                .sumOf { it.amount }
        }.getOrDefault(0.0)

    // --- hero actions ------------------------------------------------------------------------

    fun skipBlock(id: Long) {
        viewModelScope.launch {
            planBlockDao.updateStatus(id, PlanBlockStatus.SKIPPED.name, System.currentTimeMillis())
        }
    }

    fun doneBlock(item: TodayItem) {
        viewModelScope.launch {
            val minutes = Duration.between(item.start, item.end).toMinutes().toInt().coerceAtLeast(1)
            planRepo.markBlockDone(item.id, minutes)
        }
    }
}
