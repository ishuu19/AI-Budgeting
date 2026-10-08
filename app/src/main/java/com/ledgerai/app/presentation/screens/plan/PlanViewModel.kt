package com.ledgerai.app.presentation.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.ScheduleDraftRepository
import com.ledgerai.app.data.schedule.FreeSlotOption
import com.ledgerai.app.data.schedule.ScheduleImportService
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.HabitOutcome
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.model.StudyPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class AgendaState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val items: List<AgendaItem> = emptyList()
)

data class TasksState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val tasks: List<CalendarEvent> = emptyList()
)

/** Study proposals. Nothing is saved until the user confirms. */
data class StudyState(
    val topic: String,
    val hours: Double,
    val deadline: LocalDate?,
    val loading: Boolean = true,
    val error: Boolean = false,
    val slots: List<FreeSlotOption> = emptyList()
)

fun monthGrid(month: YearMonth): List<LocalDate> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = mutableListOf<LocalDate>()
    var d = start
    while (!d.isAfter(end)) {
        days += d
        d = d.plusDays(1)
    }
    return days
}

/** One ViewModel for the Plan tab's Calendar and Tasks segments, the Add sheet and item sheets. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val planRepo: PlanRepository,
    private val habitRepo: HabitRepository,
    private val leaveByRepo: LeaveByRepository,
    private val scheduleImport: ScheduleImportService,
    private val scheduleDraftRepo: ScheduleDraftRepository
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val month: StateFlow<LocalDate> = _month.asStateFlow()

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun retry() { reload.value++ }

    /** Events, saved plan blocks and habit sessions in one sorted list. */
    val agenda: StateFlow<AgendaState> = combine(_month, reload) { m, _ -> m }
        .flatMapLatest { m ->
            val grid = monthGrid(YearMonth.from(m))
            val today = LocalDate.now()
            val from = minOf(today, grid.first())
            val to = maxOf(grid.last(), today.plusDays(30))
            combine(
                calendarRepo.observeRange(from, to),
                planRepo.observeBlocks(),
                habitRepo.observeHabits(),
                habitRepo.observeDone(from, to)
            ) { events, blocks, habits, done ->
                AgendaState(loading = false, items = buildAgenda(events, blocks, habits, done, from, to))
            }.catch { emit(AgendaState(loading = false, error = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgendaState())

    val tasks: StateFlow<TasksState> = reload
        .flatMapLatest {
            calendarRepo.observeTasks()
                .map { TasksState(loading = false, tasks = it) }
                .catch { emit(TasksState(loading = false, error = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksState())

    private fun buildAgenda(
        events: List<CalendarEvent>,
        blocks: List<PlanBlock>,
        habits: List<Habit>,
        done: Set<Pair<Long, LocalDate>>,
        from: LocalDate,
        to: LocalDate
    ): List<AgendaItem> {
        val out = mutableListOf<AgendaItem>()
        events.filter { it.hasDate && !(it.kind == CalendarEventKind.TASK && it.isCompleted) }
            .forEach { out += it.toAgendaItem() }
        val inRange = blocks.filter {
            val d = it.startAt.toLocalDate()
            !d.isBefore(from) && !d.isAfter(to) && it.status != PlanBlockStatus.SKIPPED
        }
        inRange.forEach { out += it.toAgendaItem() }
        val habitBlockDays = inRange.filter { it.kind == PlanBlockKind.HABIT && it.habitId != null }
            .map { it.habitId!! to it.startAt.toLocalDate() }.toSet()
        habits.forEach { habit ->
            var d = from
            while (!d.isAfter(to)) {
                if (habit.occursOn(d) && (habit.id to d) !in habitBlockDays) {
                    out += habit.sessionOn(d, done = (habit.id to d) in done)
                }
                d = d.plusDays(1)
            }
        }
        return out.sortedWith(compareBy<AgendaItem> { it.date }.thenBy { !it.allDay }.thenBy { it.start })
    }

    // --- lookup ------------------------------------------------------------------------------

    /** Finds the item for [key], from the loaded list or, if it is outside it, from storage. */
    suspend fun resolve(key: String): AgendaItem? {
        agenda.value.items.firstOrNull { it.key == key }?.let { return it }
        val parts = key.split(":")
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return when (parts[0]) {
            "e" -> calendarRepo.getById(id)?.toAgendaItem()
            "b" -> planRepo.getBlock(id)?.toAgendaItem()
            "h" -> {
                val date = parts.getOrNull(2)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                habitRepo.getById(id)?.sessionOn(date, done = false)
            }
            else -> null
        }
    }

    suspend fun loadMaster(id: Long): CalendarEvent? = calendarRepo.getById(id)
    suspend fun loadHabit(id: Long): Habit? = habitRepo.getById(id)
    suspend fun isLeaveByEnabled(eventId: Long): Boolean =
        eventId > 0 && leaveByRepo.isEnabled(LeaveRefType.CALENDAR_EVENT, eventId)

    // --- events and tasks --------------------------------------------------------------------

    fun saveEvent(event: CalendarEvent, leaveByEnabled: Boolean) {
        viewModelScope.launch {
            val id = calendarRepo.upsert(event)
            leaveByRepo.setLeaveBy(
                refType = LeaveRefType.CALENDAR_EVENT,
                refId = id,
                enabled = leaveByEnabled,
                placeLabel = event.location,
                eventStart = event.startAt.takeIf { event.hasDate },
                title = event.title
            )
        }
    }

    fun setDone(event: CalendarEvent, done: Boolean) {
        viewModelScope.launch {
            calendarRepo.setCompleted(event.masterId, done, event.instanceDate ?: LocalDate.now())
        }
    }

    fun setAlarmEnabled(event: CalendarEvent, enabled: Boolean) {
        viewModelScope.launch { calendarRepo.setEnabled(event.masterId, enabled) }
    }

    fun reschedule(event: CalendarEvent, date: LocalDate) {
        viewModelScope.launch { calendarRepo.rescheduleInstance(event, date) }
    }

    fun deleteEvent(event: CalendarEvent, scope: RecurrenceDeleteScope) {
        viewModelScope.launch {
            val masterId = event.masterId.takeIf { it > 0 } ?: return@launch
            calendarRepo.deleteRecurring(masterId, event.instanceDate ?: event.startAt.toLocalDate(), scope)
        }
    }

    // --- study -------------------------------------------------------------------------------

    private val _study = MutableStateFlow<StudyState?>(null)
    val study: StateFlow<StudyState?> = _study.asStateFlow()

    /** Generates proposals. Persists nothing. */
    fun findTime(topic: String, hours: Double, deadline: LocalDate?) {
        _study.value = StudyState(topic = topic, hours = hours, deadline = deadline)
        viewModelScope.launch {
            val next = runCatching { planRepo.findStudySlots(hours, 50, deadline) }
                .fold(
                    onSuccess = { StudyState(topic, hours, deadline, loading = false, slots = it) },
                    onFailure = { StudyState(topic, hours, deadline, loading = false, error = true) }
                )
            // Dismissed while loading: stay closed.
            if (_study.value != null) _study.value = next
        }
    }

    fun confirmStudy(selected: List<FreeSlotOption>) {
        val s = _study.value ?: return
        _study.value = null
        if (selected.isEmpty()) return
        viewModelScope.launch {
            planRepo.saveStudyPlanWithBlocks(
                StudyPlan(topic = s.topic, hoursTotal = s.hours, deadline = s.deadline),
                selected
            )
        }
    }

    fun clearStudy() { _study.value = null }

    // --- habits and blocks -------------------------------------------------------------------

    suspend fun saveHabit(habit: Habit): Result<Unit> = runCatching {
        require(habit.title.isNotBlank()) { "Name needed" }
        habitRepo.save(habit)
        Unit
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch { habitRepo.delete(id) }
    }

    fun completeHabit(habit: Habit, date: LocalDate) {
        viewModelScope.launch {
            habitRepo.logOutcome(habit.id, HabitOutcome.DONE, habit.durationMinutes, date)
        }
    }

    fun setBlockDone(block: PlanBlock, done: Boolean) {
        viewModelScope.launch {
            if (done) {
                val minutes = java.time.Duration.between(block.startAt, block.endAt).toMinutes().toInt()
                planRepo.markBlockDone(block.id, minutes)
            } else {
                planRepo.setBlockStatus(block.id, PlanBlockStatus.SCHEDULED)
            }
        }
    }

    fun deleteBlock(id: Long) {
        viewModelScope.launch { planRepo.deleteBlock(id) }
    }

    // --- import and suggestions --------------------------------------------------------------

    suspend fun importText(text: String, replace: Boolean) = scheduleImport.importText(text, replace)
    suspend fun importCsv(uri: android.net.Uri, replace: Boolean) = scheduleImport.importCsvUri(uri, replace)
    suspend fun importImage(uri: android.net.Uri, replace: Boolean) = scheduleImport.importImageUri(uri, replace)

    private val _suggestions = MutableStateFlow<List<ScheduleDraftDto>>(emptyList())
    val suggestions: StateFlow<List<ScheduleDraftDto>> = _suggestions.asStateFlow()
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading.asStateFlow()

    fun loadSuggestions() {
        viewModelScope.launch {
            _suggestionsLoading.value = true
            scheduleDraftRepo.loadSuggestions()
                .onSuccess { _suggestions.value = it }
                .onFailure { _suggestions.value = emptyList() }
            _suggestionsLoading.value = false
        }
    }

    fun acceptSuggestion(draft: ScheduleDraftDto) {
        viewModelScope.launch {
            scheduleDraftRepo.acceptDraft(draft)
            _suggestions.value = _suggestions.value.filter { it != draft }
        }
    }

    fun dismissSuggestion(draft: ScheduleDraftDto) {
        _suggestions.value = _suggestions.value.filter { it != draft }
    }
}
