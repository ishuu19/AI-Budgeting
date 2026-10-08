package com.ledgerai.app.presentation.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_TASK
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TasksUiState(
    val tasks: List<TaskItem> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepo: TaskRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TasksUiState> = combine(
        taskRepo.observeTasks(),
        _message
    ) { tasks, message ->
        TasksUiState(tasks = tasks, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun clearMessage() {
        _message.value = null
    }

    fun addTask(title: String, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepo.insert(TaskItem(title = title.trim(), notes = notes.trim()))
        }
    }

    /** Creates or updates a task. [beforeDue] are minutes-before offsets for a new task. */
    fun saveTask(
        existing: TaskItem?,
        title: String,
        notes: String,
        dueAt: LocalDateTime?,
        beforeDue: List<Pair<String, Long>> = emptyList(),
        location: String = "",
        links: String = ""
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            val place = location.trim()
            val linkText = links.trim()
            val taskId = if (existing == null) {
                taskRepo.insert(
                    TaskItem(
                        title = cleanTitle,
                        notes = cleanNotes,
                        dueAt = dueAt,
                        location = place,
                        links = linkText
                    )
                )
            } else {
                val reminders = shiftReminders(existing.reminders, existing.dueAt, dueAt)
                taskRepo.update(
                    existing.copy(
                        title = cleanTitle,
                        notes = cleanNotes,
                        dueAt = dueAt,
                        location = place,
                        links = linkText,
                        reminders = reminders
                    )
                )
                existing.id
            }
            if (existing != null || dueAt == null) return@launch
            val now = LocalDateTime.now()
            beforeDue.distinctBy { it.second }.take(MAX_REMINDERS_PER_TASK).forEach { (label, minutes) ->
                val at = dueAt.minusMinutes(minutes)
                if (!at.isAfter(now)) {
                    _message.value = "Reminder time must be in the future"
                } else if (!taskRepo.addReminder(taskId, label, at, minutes.toInt())) {
                    _message.value = "Maximum $MAX_REMINDERS_PER_TASK reminders per task"
                }
            }
        }
    }

    fun toggleCompleted(task: TaskItem) {
        viewModelScope.launch { taskRepo.setCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch { taskRepo.delete(task) }
    }

    fun addReminder(task: TaskItem, label: String, remindAt: LocalDateTime, offsetMinutes: Int? = null) {
        viewModelScope.launch {
            if (!task.canAddReminder) {
                _message.value = "Maximum $MAX_REMINDERS_PER_TASK reminders per task"
                return@launch
            }
            if (!remindAt.isAfter(LocalDateTime.now())) {
                _message.value = "Reminder time must be in the future"
                return@launch
            }
            val ok = taskRepo.addReminder(
                taskId = task.id,
                label = label.ifBlank { "Reminder" },
                remindAt = remindAt,
                offsetMinutes = offsetMinutes
            )
            if (!ok) {
                _message.value = "Maximum $MAX_REMINDERS_PER_TASK reminders per task"
            }
        }
    }

    fun removeReminder(task: TaskItem, reminder: TaskReminder) {
        viewModelScope.launch { taskRepo.removeReminder(task.id, reminder.id) }
    }
}

/** Moves reminders with the due time. Offsets stay; absolute reminders shift by the same amount. */
private fun shiftReminders(
    reminders: List<TaskReminder>,
    oldDue: LocalDateTime?,
    newDue: LocalDateTime?
): List<TaskReminder> {
    if (oldDue == newDue) return reminders
    val delta = if (oldDue != null && newDue != null) Duration.between(oldDue, newDue) else null
    return reminders.map { reminder ->
        val at = when {
            reminder.offsetMinutes != null && newDue != null ->
                newDue.minusMinutes(reminder.offsetMinutes.toLong())
            delta != null -> reminder.remindAt.plus(delta)
            else -> reminder.remindAt
        }
        reminder.copy(remindAt = at)
    }
}

private enum class TaskFilter(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    LATE("Late"),
    DONE("Done")
}

/** Sheet target: `null` id = new task. */
private data class TaskSheet(val id: Long?)

/** Minutes before the due time, same idea as a calendar event. */
private val reminderOffsets: List<Pair<String, Long>> = listOf(
    "At time" to 0L,
    "5 min before" to 5L,
    "10 min before" to 10L,
    "15 min before" to 15L,
    "30 min before" to 30L,
    "1 hour before" to 60L,
    "2 hours before" to 120L,
    "1 day before" to 1_440L,
    "2 days before" to 2_880L,
    "1 week before" to 10_080L
)

private fun TaskItem.isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean =
    !isCompleted && dueAt?.isBefore(now) == true

private fun TaskItem.matches(filter: TaskFilter, today: LocalDate, now: LocalDateTime): Boolean = when (filter) {
    TaskFilter.DONE -> isCompleted
    TaskFilter.LATE -> isOverdue(now)
    TaskFilter.TODAY -> !isCompleted && (dueAt == null || !dueAt.toLocalDate().isAfter(today))
    TaskFilter.UPCOMING -> !isCompleted && dueAt != null && dueAt.toLocalDate().isAfter(today)
}

private fun TaskItem.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) || notes.contains(query, ignoreCase = true)
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun dueLabel(due: LocalDateTime): String {
    val today = LocalDate.now()
    return when (due.toLocalDate()) {
        today -> due.format(timeFmt)
        today.plusDays(1) -> "Tomorrow ${due.format(timeFmt)}"
        else -> due.format(dateTimeFmt)
    }
}

@Composable
fun TasksScreen(onBack: () -> Unit = {}, viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(TaskFilter.TODAY) }
    var sheet by remember { mutableStateOf<TaskSheet?>(null) }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    var query by remember { mutableStateOf("") }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val visible = state.tasks.filter { it.matches(filter, today, now) && it.matchesQuery(query) }
    val openCount = state.tasks.count { !it.isCompleted }
    val lateCount = state.tasks.count { it.isOverdue(now) }

    LScreen(
        title = "Tasks",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { sheet = TaskSheet(null) }) }
    ) {
        item {
            LHero(
                label = "Open",
                value = openCount.toString(),
                sub = if (lateCount > 0) "$lateCount late" else null
            )
        }
        item {
            LField(query, { query = it }, "Search")
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilter.entries.forEach { f ->
                    val n = state.tasks.count { it.matches(f, today, now) }
                    LChip(
                        if (n > 0) "${f.label} $n" else f.label,
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Filled.CheckCircle, "No tasks") }
        } else {
            items(visible, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { viewModel.toggleCompleted(task) },
                    onClick = { sheet = TaskSheet(task.id) }
                )
            }
        }
    }

    sheet?.let { target ->
        val existing = target.id?.let { id -> state.tasks.firstOrNull { it.id == id } }
        if (target.id != null && existing == null) {
            LaunchedEffect(target) { sheet = null }
        } else {
            TaskEditSheet(
                existing = existing,
                onDismiss = { sheet = null },
                onSave = { title, notes, dueAt, beforeDue, location, links ->
                    viewModel.saveTask(existing, title, notes, dueAt, beforeDue, location, links)
                    sheet = null
                },
                onDelete = {
                    existing?.let { viewModel.deleteTask(it) }
                    sheet = null
                },
                onAddReminder = { label, at, offset ->
                    existing?.let { viewModel.addReminder(it, label, at, offset) }
                },
                onRemoveReminder = { r -> existing?.let { viewModel.removeReminder(it, r) } }
            )
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onClick: () -> Unit) {
    val done = task.isCompleted
    val overdue = task.isOverdue()
    LRow(
        title = task.title,
        sub = task.notes.takeIf { it.isNotBlank() },
        trailing = task.dueAt?.let { dueLabel(it) },
        trailingColor = if (overdue) L.Danger else L.Gold,
        onClick = onClick,
        end = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (done) "Undo" else "Done",
                    tint = if (done) L.Gold else L.OnBoxMuted
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditSheet(
    existing: TaskItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, dueAt: LocalDateTime?, beforeDue: List<Pair<String, Long>>, location: String, links: String) -> Unit,
    onDelete: () -> Unit,
    onAddReminder: (label: String, at: LocalDateTime, offsetMinutes: Int) -> Unit,
    onRemoveReminder: (TaskReminder) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var links by remember { mutableStateOf(existing?.links.orEmpty()) }
    var dueDate by remember { mutableStateOf(existing?.dueAt?.toLocalDate()) }
    var pending by remember { mutableStateOf(listOf<Pair<String, Long>>()) }
    var showOffsets by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = existing?.dueAt?.hour ?: 9,
        initialMinute = existing?.dueAt?.minute ?: 0,
        is24Hour = false
    )
    val dueAt = dueDate?.atTime(timeState.hour, timeState.minute)

    LSheet(
        title = if (existing == null) "New task" else "Task",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            onSave(title, notes, dueAt, if (existing == null) pending else emptyList(), location, links)
        },
        primaryEnabled = title.isNotBlank(),
        secondary = if (existing != null) "Delete" else null,
        onSecondary = onDelete
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LField(title, { title = it }, "Title")
            LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
            LField(location, { location = it }, "Place")
            LField(links, { links = it }, "Links", singleLine = false, minLines = 2)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LChip(
                    dueDate?.format(dateFmt) ?: "Date",
                    selected = dueDate != null,
                    onClick = { showDatePicker = true }
                )
                if (dueDate != null) {
                    LChip("None", selected = false, onClick = { dueDate = null })
                }
            }
            if (dueDate != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState, colors = lTimeColors())
                }
            }

            Text("Reminders", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            existing?.reminders.orEmpty().forEach { reminder ->
                ReminderLine(
                    text = "${reminder.label} · ${reminder.remindAt.format(dateTimeFmt)}",
                    onRemove = { onRemoveReminder(reminder) }
                )
            }
            if (existing == null) {
                pending.forEach { (label, minutes) ->
                    val whenLabel = dueAt?.minusMinutes(minutes)?.format(dateTimeFmt) ?: label
                    ReminderLine(text = "$label · $whenLabel", onRemove = {
                        pending = pending.filterNot { it.second == minutes }
                    })
                }
            }
            val savedCount = existing?.reminders?.size ?: pending.size
            if (savedCount < MAX_REMINDERS_PER_TASK) {
                Box {
                    IconButton(onClick = {
                        if (dueAt == null) {
                            Toast.makeText(context, "Set a time first", Toast.LENGTH_SHORT).show()
                        } else {
                            showOffsets = true
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add reminder", tint = L.Box)
                    }
                    DropdownMenu(expanded = showOffsets, onDismissRequest = { showOffsets = false }) {
                        reminderOffsets.forEach { (label, minutes) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showOffsets = false
                                    val at = dueAt?.minusMinutes(minutes) ?: return@DropdownMenuItem
                                    if (existing == null) {
                                        if (pending.none { it.second == minutes }) {
                                            pending = pending + (label to minutes)
                                        }
                                    } else {
                                        onAddReminder(label, at, minutes.toInt())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dueDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Done", color = L.Box) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = L.InkMuted) }
            },
            colors = DatePickerDefaults.colors(containerColor = L.Page)
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = L.Page,
                    selectedDayContainerColor = L.Box,
                    selectedDayContentColor = L.OnBox,
                    todayDateBorderColor = L.Gold,
                    todayContentColor = L.Box
                )
            )
        }
    }
}

@Composable
private fun ReminderLine(text: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = L.Gold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = L.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = L.InkMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun lTimeColors(): TimePickerColors = TimePickerDefaults.colors(
    clockDialColor = L.Line,
    clockDialSelectedContentColor = L.OnBox,
    clockDialUnselectedContentColor = L.Ink,
    selectorColor = L.Box,
    containerColor = L.Page,
    periodSelectorBorderColor = L.Box,
    periodSelectorSelectedContainerColor = L.Box,
    periodSelectorUnselectedContainerColor = L.Page,
    periodSelectorSelectedContentColor = L.OnBox,
    periodSelectorUnselectedContentColor = L.Ink,
    timeSelectorSelectedContainerColor = L.Box,
    timeSelectorUnselectedContainerColor = L.Line,
    timeSelectorSelectedContentColor = L.OnBox,
    timeSelectorUnselectedContentColor = L.Ink
)
