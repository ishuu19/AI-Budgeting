package com.ledgerai.app.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.data.repository.ScheduleDraftRepository
import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.presentation.components.SuggestionsSheet
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.data.repository.ScheduleRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.schedule.ScheduleImportService
import com.ledgerai.app.data.schedule.expandSlotsForMonth
import com.ledgerai.app.data.schedule.expandTasksForMonth
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.model.RoutineSlotReminder
import com.ledgerai.app.domain.model.ScheduleSlot
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

data class CalendarUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList(),
    val importMessage: String? = null
) {
    fun eventsOn(date: LocalDate): List<CalendarEvent> =
        events.filter { it.startAt.toLocalDate() == date }.sortedBy { it.startAt }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val scheduleRepo: ScheduleRepository,
    private val scheduleImport: ScheduleImportService,
    private val taskRepo: TaskRepository,
    private val leaveByRepo: LeaveByRepository,
    private val scheduleDraftRepo: ScheduleDraftRepository
) : ViewModel() {

    private val _suggestions = MutableStateFlow<List<ScheduleDraftDto>>(emptyList())
    val suggestions: StateFlow<List<ScheduleDraftDto>> = _suggestions.asStateFlow()
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading.asStateFlow()

    private val month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    private val selected = MutableStateFlow(LocalDate.now())
    private val importMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CalendarUiState> = combine(month, selected, importMessage) { m, sel, msg ->
        Triple(m, sel, msg)
    }.flatMapLatest { (m, sel, msg) ->
        combine(
            calendarRepo.observeMonth(m),
            scheduleRepo.observeAllSlots(),
            taskRepo.observeTasks()
        ) { stored, slots, tasks ->
            val taskById = tasks.associateBy { it.id }
            val storedEnriched = stored.map { e ->
                if (e.taskId != null && e.location.isBlank()) {
                    e.copy(location = taskById[e.taskId]?.location.orEmpty())
                } else e
            }
            val classes = expandSlotsForMonth(slots, m)
            val dueTasks = expandTasksForMonth(tasks, m)
            val merged = (storedEnriched + classes + dueTasks)
                .distinctBy { "${it.kind}-${it.startAt}-${it.title}-${it.scheduleSlotId}-${it.taskId}" }
                .sortedBy { it.startAt }
            CalendarUiState(month = m, selectedDate = sel, events = merged, importMessage = msg)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
    }

    fun clearImportMessage() {
        importMessage.value = null
    }

    suspend fun importText(text: String, replace: Boolean) =
        scheduleImport.importText(text, routineId = null, replace = replace)
            .also { r -> r.onSuccess { importMessage.value = "Added $it classes to your calendar" } }

    suspend fun importCsv(uri: android.net.Uri, replace: Boolean) =
        scheduleImport.importCsvUri(uri, null, replace)
            .also { r -> r.onSuccess { importMessage.value = "Added $it classes to your calendar" } }

    suspend fun importImage(uri: android.net.Uri, replace: Boolean) =
        scheduleImport.importImageUri(uri, null, replace)
            .also { r -> r.onSuccess { importMessage.value = "Added $it classes to your calendar" } }

    suspend fun loadSlot(slotId: Long): ScheduleSlot? = scheduleRepo.getSlot(slotId)

    fun saveSlot(
        slotId: Long,
        location: String,
        reminders: List<RoutineSlotReminder>,
        dayOfWeek: Int
    ) {
        viewModelScope.launch {
            scheduleRepo.updateSlotLocation(slotId, location)
            scheduleRepo.updateSlotDayOfWeek(slotId, dayOfWeek)
            scheduleRepo.replaceSlotReminders(slotId, reminders)
        }
    }

    fun moveClassOccurrence(slotId: Long, fromDate: java.time.LocalDate, toDate: java.time.LocalDate) {
        viewModelScope.launch {
            scheduleRepo.moveClassOccurrence(slotId, fromDate, toDate)
        }
    }

    fun rescheduleEvent(event: CalendarEvent, newDate: java.time.LocalDate) {
        viewModelScope.launch {
            when {
                event.kind == CalendarEventKind.TASK && event.taskId != null -> {
                    val task = taskRepo.findById(event.taskId) ?: return@launch
                    val due = task.dueAt ?: return@launch
                    taskRepo.update(task.copy(dueAt = java.time.LocalDateTime.of(newDate, due.toLocalTime())))
                }
                event.scheduleSlotId != null -> {
                    val from = event.instanceDate ?: event.startAt.toLocalDate()
                    scheduleRepo.moveClassOccurrence(event.scheduleSlotId, from, newDate)
                }
                else -> calendarRepo.rescheduleInstance(event, newDate)
            }
        }
    }

    fun saveCalendarEvent(event: CalendarEvent, leaveByEnabled: Boolean) {
        viewModelScope.launch {
            val id = calendarRepo.upsert(event)
            leaveByRepo.setLeaveBy(
                refType = LeaveRefType.CALENDAR_EVENT,
                refId = id,
                enabled = leaveByEnabled,
                placeLabel = event.location,
                eventStart = event.startAt,
                title = event.title
            )
        }
    }

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

    suspend fun isLeaveByEnabled(eventId: Long): Boolean =
        leaveByRepo.isEnabled(LeaveRefType.CALENDAR_EVENT, eventId)

    suspend fun loadCalendarMaster(seriesId: Long): CalendarEvent? =
        calendarRepo.getById(seriesId)

    fun removeEvent(event: CalendarEvent, scope: RecurrenceDeleteScope) {
        viewModelScope.launch {
            val instanceDate = event.instanceDate ?: event.startAt.toLocalDate()
            val slotId = event.scheduleSlotId
            if (slotId != null) {
                scheduleRepo.deleteSlotOccurrence(slotId, instanceDate, scope)
                return@launch
            }
            if (event.kind == CalendarEventKind.TASK && event.taskId != null) {
                taskRepo.findById(event.taskId)?.let { taskRepo.delete(it) }
                return@launch
            }
            val masterId = event.seriesEventId ?: event.id.takeIf { it > 0 }
            if (masterId != null) {
                calendarRepo.deleteRecurring(masterId, instanceDate, scope)
                if (scope == RecurrenceDeleteScope.ALL) {
                    leaveByRepo.removeForRef(LeaveRefType.CALENDAR_EVENT, masterId)
                }
                return@launch
            }
            event.taskId?.let { calendarRepo.removeForTask(it) }
        }
    }
}

private fun isRecurringCalendarEvent(event: CalendarEvent): Boolean =
    event.scheduleSlotId != null || event.recurrence?.repeats == true

@Composable
fun CalendarScreen(onBack: (() -> Unit)? = null, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showImport by remember { mutableStateOf(false) }
    var editSlot by remember { mutableStateOf<ScheduleSlot?>(null) }
    var pendingSlotId by remember { mutableStateOf<Long?>(null) }
    var confirmRemove by remember { mutableStateOf<CalendarEvent?>(null) }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showCreateEvent by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    var editMaster by remember { mutableStateOf<CalendarEvent?>(null) }
    var editLeaveBy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val ym = remember(state.month) { YearMonth.from(state.month) }
    val grid = remember(ym) { buildMonthGrid(ym) }

    LaunchedEffect(pendingSlotId) {
        val id = pendingSlotId ?: return@LaunchedEffect
        pendingSlotId = null
        editSlot = viewModel.loadSlot(id)
    }

    if (showImport) {
        ScheduleImportSheet(
            title = "Import schedule",
            onDismiss = { showImport = false },
            onImportText = { t, r -> viewModel.importText(t, r) },
            onImportCsv = { u, r -> viewModel.importCsv(u, r) },
            onImportImage = { u, r -> viewModel.importImage(u, r) }
        )
    }

    editSlot?.let { slot ->
        ScheduleSlotEditSheet(
            slot = slot,
            occurrenceDate = state.selectedDate,
            onDismiss = { editSlot = null },
            onSave = { loc, reminders, dow ->
                viewModel.saveSlot(slot.id, loc, reminders, dow)
                editSlot = null
            },
            onMoveOccurrenceDate = { newDate ->
                viewModel.moveClassOccurrence(slot.id, state.selectedDate, newDate)
                editSlot = null
            },
            onRemove = {
                confirmRemove = CalendarEvent(
                    title = slot.title,
                    scheduleSlotId = slot.id,
                    location = slot.location,
                    startAt = slot.startTime.atDate(state.selectedDate),
                    endAt = slot.endTime.atDate(state.selectedDate),
                    kind = CalendarEventKind.CLASS,
                    instanceDate = state.selectedDate
                )
                editSlot = null
            }
        )
    }

    showCreateEvent.takeIf { it }?.let {
        CalendarEventEditSheet(
            existing = null,
            defaultDate = state.selectedDate,
            onDismiss = { showCreateEvent = false },
            onSave = { ev, leaveBy ->
                viewModel.saveCalendarEvent(ev, leaveBy)
                showCreateEvent = false
            }
        )
    }

    editMaster?.let { master ->
        LaunchedEffect(master.id) {
            editLeaveBy = if (master.id > 0) viewModel.isLeaveByEnabled(master.id) else false
        }
        CalendarEventEditSheet(
            existing = master,
            defaultDate = state.selectedDate,
            initialLeaveBy = editLeaveBy,
            onDismiss = { editMaster = null },
            onSave = { ev, leaveBy ->
                viewModel.saveCalendarEvent(ev, leaveBy)
                editMaster = null
            }
        )
    }

    if (showSuggestions) {
        SuggestionsSheet(
            drafts = suggestions,
            loading = suggestionsLoading,
            onDismiss = { showSuggestions = false },
            onAccept = { viewModel.acceptSuggestion(it) },
            onDismissDraft = { viewModel.dismissSuggestion(it) }
        )
    }

    selectedEvent?.let { event ->
        if (event.scheduleSlotId == null) {
            CalendarEventDetailSheet(
                event = event,
                onDismiss = { selectedEvent = null },
                onEdit = {
                    val id = event.seriesEventId ?: event.id
                    if (id > 0) {
                        scope.launch {
                            editMaster = viewModel.loadCalendarMaster(id)
                            selectedEvent = null
                        }
                    }
                },
                onChangeDate = { newDate ->
                    viewModel.rescheduleEvent(event, newDate)
                    selectedEvent = null
                },
                onRemove = {
                    confirmRemove = event
                    selectedEvent = null
                }
            )
        }
    }

    confirmRemove?.let { event ->
        if (event.kind == CalendarEventKind.TASK) {
            SimpleDeleteConfirmDialog(
                title = "Delete task?",
                message = "“${event.title}” will be deleted from Tasks and the calendar.",
                onDismiss = { confirmRemove = null },
                onConfirm = {
                    viewModel.removeEvent(event, RecurrenceDeleteScope.THIS)
                    confirmRemove = null
                }
            )
        } else {
            RecurrenceDeleteSheet(
                title = event.title,
                isRecurring = isRecurringCalendarEvent(event),
                onDismiss = { confirmRemove = null },
                onThisOnly = {
                    viewModel.removeEvent(event, RecurrenceDeleteScope.THIS)
                    confirmRemove = null
                },
                onThisAndFuture = {
                    viewModel.removeEvent(event, RecurrenceDeleteScope.THIS_AND_FUTURE)
                    confirmRemove = null
                },
                onAll = {
                    viewModel.removeEvent(event, RecurrenceDeleteScope.ALL)
                    confirmRemove = null
                }
            )
        }
    }

    LScreen(
        title = "Calendar",
        onBack = onBack,
        action = {
            Row {
                IconButton(onClick = {
                    showSuggestions = true
                    viewModel.loadSuggestions()
                }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Suggestions", tint = L.Box)
                }
                IconButton(onClick = { showCreateEvent = true }) {
                    Icon(Icons.Default.Event, contentDescription = "Add event", tint = L.Box)
                }
                IconButton(onClick = { showImport = true }) {
                    Icon(Icons.Default.Upload, contentDescription = "Import schedule", tint = L.Box)
                }
            }
        }
    ) {
        item {
            val classes = state.events.count { it.kind == CalendarEventKind.CLASS }
            val tasks = state.events.count { it.kind == CalendarEventKind.TASK }
            val other = state.events.count {
                it.kind == CalendarEventKind.EXAM || it.kind == CalendarEventKind.PERSONAL
            }
            LHero(
                label = "This month",
                value = state.events.size.toString(),
                sub = "$classes classes · $tasks tasks · $other events"
            )
        }
        item {
            LButton("Import schedule", onClick = { showImport = true })
        }
        state.importMessage?.let { msg ->
            item {
                Text(msg, color = L.Gold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::prevMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = L.Gold)
                }
                Text(state.month.format(monthFmt), style = MaterialTheme.typography.titleLarge, color = L.Ink)
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = L.Gold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DayOfWeek.values().forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = L.InkMuted
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grid.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { cell ->
                            CalendarDayCell(
                                date = cell,
                                inMonth = cell.month == ym.month,
                                selected = cell == state.selectedDate,
                                isToday = cell == LocalDate.now(),
                                eventCount = state.eventsOn(cell).size,
                                onClick = { viewModel.selectDate(cell) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.titleMedium,
                color = L.Ink,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        val dayEvents = state.eventsOn(state.selectedDate)
        if (dayEvents.isEmpty()) {
            item {
                LEmpty(Icons.Default.CalendarMonth, "Nothing this day — import a timetable")
            }
        } else {
            items(dayEvents, key = { "${it.kind}-${it.startAt}-${it.title}-${it.scheduleSlotId}-${it.taskId}" }) { event ->
                CalendarAgendaRow(
                    event = event,
                    timeFmt = timeFmt,
                    onClick = {
                        if (event.scheduleSlotId != null) {
                            pendingSlotId = event.scheduleSlotId
                        } else {
                            selectedEvent = event
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CalendarAgendaRow(
    event: CalendarEvent,
    timeFmt: DateTimeFormatter,
    onClick: () -> Unit
) {
    val kindLabel = when (event.kind) {
        CalendarEventKind.TASK -> "Task"
        CalendarEventKind.CLASS -> "Class"
        CalendarEventKind.EXAM -> "Exam"
        CalendarEventKind.PERSONAL -> "Event"
    }
    val accent = when (event.kind) {
        CalendarEventKind.TASK -> L.Gold
        CalendarEventKind.CLASS -> L.Box
        CalendarEventKind.EXAM -> L.Gold
        CalendarEventKind.PERSONAL -> L.InkMuted
    }
    val icon = when (event.kind) {
        CalendarEventKind.TASK -> Icons.Default.CheckCircle
        CalendarEventKind.CLASS -> Icons.Default.School
        else -> Icons.Default.Event
    }
    val shape = RoundedCornerShape(L.Radius)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .clickable(onClick = onClick)

    if (event.kind == CalendarEventKind.TASK) {
        Row(
            base
                .background(L.Page)
                .border(1.5.dp, L.Gold, shape)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = L.Gold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                Text(
                    "${event.startAt.format(timeFmt)} · $kindLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = L.InkMuted
                )
                if (event.location.isNotBlank()) {
                    Text(event.location, style = MaterialTheme.typography.bodySmall, color = L.Box)
                }
            }
        }
    } else {
        Row(
            base.background(L.Box).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Icon(icon, contentDescription = null, tint = L.OnBoxMuted, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, color = L.OnBox)
                Text(
                    "${event.startAt.format(timeFmt)} – ${event.endAt.format(timeFmt)} · $kindLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted
                )
                if (event.location.isNotBlank()) {
                    Text(event.location, style = MaterialTheme.typography.bodySmall, color = L.Gold)
                }
            }
        }
    }
}

private fun buildMonthGrid(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = month.atEndOfMonth()
    val end = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = mutableListOf<LocalDate>()
    var d = start
    while (!d.isAfter(end)) {
        days += d
        d = d.plusDays(1)
    }
    return days
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        selected -> L.Gold
        isToday -> L.Box
        else -> L.Page
    }
    val ink = when {
        selected -> L.Page
        !inMonth -> L.InkMuted.copy(alpha = 0.45f)
        else -> L.Ink
    }
    Column(
        modifier = modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            textAlign = TextAlign.Center
        )
        if (eventCount > 0 && inMonth) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) L.Page else L.Gold)
            )
        }
    }
}
