package com.ledgerai.app.presentation.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_TASK
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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

    fun toggleCompleted(task: TaskItem) {
        viewModelScope.launch { taskRepo.setCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch { taskRepo.delete(task) }
    }

    fun addReminder(task: TaskItem, label: String, hoursFromNow: Long) {
        viewModelScope.launch {
            if (!task.canAddReminder) {
                _message.value = "Maximum $MAX_REMINDERS_PER_TASK reminders per task"
                return@launch
            }
            val ok = taskRepo.addReminder(
                taskId = task.id,
                label = label.ifBlank { "Reminder" },
                remindAt = LocalDateTime.now().plusHours(hoursFromNow)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var reminderTask by remember { mutableStateOf<TaskItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks")
                        Text(
                            "One list. Up to $MAX_REMINDERS_PER_TASK reminders each.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.tasks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No tasks yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a task, then attach reminders when you need them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.toggleCompleted(task) },
                        onDelete = { viewModel.deleteTask(task) },
                        onAddReminder = { reminderTask = task },
                        onRemoveReminder = { viewModel.removeReminder(task, it) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, notes ->
                viewModel.addTask(title, notes)
                showAdd = false
            }
        )
    }

    reminderTask?.let { task ->
        AddReminderDialog(
            task = task,
            onDismiss = { reminderTask = null },
            onConfirm = { label, hours ->
                viewModel.addReminder(task, label, hours)
                reminderTask = null
            }
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAddReminder: () -> Unit,
    onRemoveReminder: (TaskReminder) -> Unit
) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (task.isCompleted) "Mark incomplete" else "Mark complete",
                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (task.notes.isNotBlank()) {
                        Text(
                            task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${task.reminderCount}/$MAX_REMINDERS_PER_TASK reminders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddReminder, enabled = task.canAddReminder) {
                    Icon(Icons.Filled.Alarm, contentDescription = "Add reminder")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                }
            }
            task.reminders.forEach { reminder ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${reminder.label} · ${reminder.remindAt.format(timeFmt)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = { onRemoveReminder(reminder) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, notes) }, enabled = title.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddReminderDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var label by remember { mutableStateOf("In 1 hour") }
    var hours by remember { mutableStateOf("1") }
    val atCap = !task.canAddReminder

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (atCap) {
                    Text(
                        "This task already has $MAX_REMINDERS_PER_TASK reminders.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "${task.reminderCount} of $MAX_REMINDERS_PER_TASK used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Hours from now") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label, hours.toLongOrNull()?.coerceAtLeast(1) ?: 1L) },
                enabled = !atCap
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
