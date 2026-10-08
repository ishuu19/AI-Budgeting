package com.ledgerai.app.presentation.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.screens.plan.AgendaKeys
import com.ledgerai.app.presentation.screens.plan.PlanViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val GROUP_CAP = 5

/** Tasks are calendar events of kind TASK. Groups never overlap: a task sits in exactly one. */
private enum class TaskGroup(val label: String) { Late("Late"), Today("Today"), Soon("Soon"), Done("Done") }

private fun CalendarEvent.group(today: LocalDate, now: LocalDateTime): TaskGroup = when {
    isCompleted -> TaskGroup.Done
    !hasDate -> TaskGroup.Soon
    isRecurring -> if (startAt.toLocalDate().isAfter(today)) TaskGroup.Soon else TaskGroup.Today
    allDay -> when {
        startAt.toLocalDate().isBefore(today) -> TaskGroup.Late
        startAt.toLocalDate() == today -> TaskGroup.Today
        else -> TaskGroup.Soon
    }
    startAt.isBefore(now) -> TaskGroup.Late
    startAt.toLocalDate() == today -> TaskGroup.Today
    else -> TaskGroup.Soon
}

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private fun dueLabel(task: CalendarEvent): String {
    if (!task.hasDate) return "Anytime"
    val today = LocalDate.now()
    val day = when (task.startAt.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> task.startAt.format(DateFmt)
    }
    return if (task.allDay) day else "$day ${task.startAt.format(TimeFmt)}"
}

@Composable
fun TasksScreen(vm: PlanViewModel, onOpen: (String) -> Unit, onAdd: (LocalDate) -> Unit) {
    val state by vm.tasks.collectAsState()
    var expanded by rememberSaveable { mutableStateOf("") }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val grouped = state.tasks.groupBy { it.group(today, now) }
    val open = state.tasks.count { !it.isCompleted }
    val late = grouped[TaskGroup.Late]?.size ?: 0

    LScreen(
        title = "Tasks",
        fab = { LFab(Icons.Filled.Add, onClick = { onAdd(today) }, label = "Add") }
    ) {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = vm::retry) }
            state.tasks.isEmpty() -> item { LEmpty(Icons.Filled.CheckCircle, "No tasks") }
            else -> {
                item(key = "open") {
                    LHero(label = "Open", value = open.toString(), sub = if (late > 0) "$late late" else null)
                }
                TaskGroup.entries.forEach { group ->
                    val rows = grouped[group].orEmpty()
                    if (rows.isEmpty()) return@forEach
                    val all = expanded.split("|").contains(group.label)
                    val shown = if (all) rows else rows.take(GROUP_CAP)
                    item(key = "g-${group.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${group.label} · ${rows.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = L.Ink,
                                modifier = Modifier.padding(top = 4.dp).semantics { heading() }
                            )
                            LGroup {
                                shown.forEachIndexed { i, task ->
                                    if (i > 0) LGroupDivider()
                                    TaskRow(
                                        task = task,
                                        late = group == TaskGroup.Late,
                                        onOpen = { onOpen(AgendaKeys.event(task.id, null)) },
                                        onToggle = { vm.setDone(task, !task.isCompleted) }
                                    )
                                }
                                if (rows.size > shown.size) {
                                    LGroupDivider()
                                    LGroupRow(
                                        title = "More (${rows.size - shown.size})",
                                        onClick = { expanded = "$expanded|${group.label}" }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: CalendarEvent, late: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val ink: Color = if (task.isCompleted) L.OnBoxMuted else L.OnBox
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = (if (task.isCompleted) "Reopen " else "Complete ") + task.title,
                tint = if (task.isCompleted) L.Gold else L.OnBoxMuted
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            )
            Text(
                dueLabel(task),
                style = MaterialTheme.typography.bodySmall,
                color = if (late) L.Danger else L.OnBoxMuted,
                maxLines = 1
            )
        }
    }
}
