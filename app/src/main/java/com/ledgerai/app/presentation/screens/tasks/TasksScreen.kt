package com.ledgerai.app.presentation.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.screens.plan.AgendaKeys
import com.ledgerai.app.presentation.screens.plan.PlanViewModel
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

private fun dueLabel(task: CalendarEvent, today: LocalDate): String {
    if (!task.hasDate) return "Anytime"
    val day = task.startAt.toLocalDate()
    val lateDays = ChronoUnit.DAYS.between(day, today)
    if (lateDays > 0 && !task.isCompleted) {
        return if (lateDays == 1L) "1 day overdue" else "$lateDays days overdue"
    }
    val whenLabel = when (day) {
        today -> "Due today"
        today.plusDays(1) -> "Due tomorrow"
        else -> "Due ${task.startAt.format(DateFmt)}"
    }
    return if (task.allDay) whenLabel else "$whenLabel ${task.startAt.format(TimeFmt)}"
}

/**
 * Tasks segment. The top card is the one task that needs you most;
 * below it the rest are grouped Late / Today / Soon, with done collapsed to one line.
 * Adding lives on the Plan tab's AI bar, not a second button here.
 */
@Composable
fun TasksScreen(vm: PlanViewModel, onOpen: (String) -> Unit, query: String = "") {
    val state by vm.tasks.collectAsState()
    val tasks = remember(state.tasks, query) {
        if (query.isBlank()) state.tasks else state.tasks.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    var expanded by rememberSaveable { mutableStateOf("") }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val grouped = tasks.groupBy { it.group(today, now) }
    val focusGroup = listOf(TaskGroup.Late, TaskGroup.Today, TaskGroup.Soon).firstOrNull { !grouped[it].isNullOrEmpty() }
    val focus: CalendarEvent? = focusGroup?.let { grouped[it]?.firstOrNull() }
    val todayOpen = grouped[TaskGroup.Today].orEmpty().size
    val doneToday = tasks.count { task ->
        task.isCompleted && (
            task.completedAt?.toLocalDate() == today ||
                (task.completedAt == null && task.hasDate && task.startAt.toLocalDate() == today)
            )
    }
    val todayTotal = todayOpen + doneToday

    LScreen(title = "Tasks") {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = vm::retry) }
            tasks.isEmpty() -> item { LEmpty(Icons.Filled.CheckCircle, if (query.isBlank()) "No tasks" else "No matches") }
            else -> {
                if (focus != null && focusGroup != null) {
                    item(key = "focus") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LHeroCard(
                                label = when (focusGroup) {
                                    TaskGroup.Late -> "Late"
                                    TaskGroup.Today -> "Due today"
                                    else -> "Up next"
                                },
                                title = focus.title,
                                onClick = { onOpen(AgendaKeys.event(focus.id, null)) },
                                actions = {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LButton(
                                                "Done",
                                                { vm.setDone(focus, true) },
                                                Modifier.weight(1f)
                                            )
                                            LGhostButton(
                                                "Tomorrow",
                                                { vm.reschedule(focus, today.plusDays(1)) },
                                                Modifier.weight(1f)
                                            )
                                        }
                                        DatePickChip(
                                            date = today.plusDays(1),
                                            selected = false,
                                            onDate = { vm.reschedule(focus, it) },
                                            label = "Pick date"
                                        )
                                    }
                                }
                            )
                            if (todayTotal > 0) {
                                Text(
                                    "$doneToday of $todayTotal done today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = L.InkMuted
                                )
                                LProgress(doneToday.toFloat() / todayTotal)
                            }
                        }
                    }
                } else {
                    item(key = "clear") { LEmpty(Icons.Filled.CheckCircle, "All clear") }
                }
                TaskGroup.entries.filter { it != TaskGroup.Done }.forEach { group ->
                    val rows = grouped[group].orEmpty().filter { it !== focus }
                    if (rows.isEmpty()) return@forEach
                    val all = expanded.split("|").contains(group.label)
                    val shown = if (all) rows else rows.take(GROUP_CAP)
                    item(key = "g-${group.name}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionCount(group.label, rows.size)
                            LGroup {
                                shown.forEachIndexed { i, task ->
                                    if (i > 0) LGroupDivider()
                                    TaskRow(
                                        task = task,
                                        today = today,
                                        onOpen = { onOpen(AgendaKeys.event(task.id, null)) },
                                        onToggle = { vm.setDone(task, !task.isCompleted) },
                                        onMoveToday = if (group == TaskGroup.Late) ({ vm.reschedule(task, today) }) else null
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
                val done = grouped[TaskGroup.Done].orEmpty()
                if (done.isNotEmpty()) {
                    val weekStart = today.minusDays(6)
                    val doneThisWeek = done.count { task ->
                        val day = task.completedAt?.toLocalDate() ?: task.startAt.toLocalDate()
                        !day.isBefore(weekStart) && !day.isAfter(today)
                    }
                    val doneDays = done.mapNotNull { it.completedAt?.toLocalDate() }.toSet()
                    item(key = "g-done") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LGroup {
                                LGroupRow(
                                    title = "$doneThisWeek done this week",
                                    sub = doneStreak(doneDays, today).takeIf { it > 0 }?.let { "$it-day streak" },
                                    trailing = if (showDone) "▾" else "▸",
                                    onClick = { showDone = !showDone },
                                    end = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            repeat(7) { index ->
                                                val day = today.minusDays((6 - index).toLong())
                                                Box(
                                                    Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (day in doneDays) L.Gold else L.BoxDeep)
                                                )
                                            }
                                        }
                                    }
                                )
                                if (showDone) {
                                    done.forEach { task ->
                                        LGroupDivider()
                                        TaskRow(
                                            task = task,
                                            today = today,
                                            onOpen = { onOpen(AgendaKeys.event(task.id, null)) },
                                            onToggle = { vm.setDone(task, !task.isCompleted) },
                                            onMoveToday = null
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
}

@Composable
private fun SectionCount(title: String, count: Int) {
    Box(Modifier.fillMaxWidth()) {
        LSection(title)
        Text(
            count.toString(),
            modifier = Modifier.align(Alignment.CenterEnd),
            style = MaterialTheme.typography.titleMedium,
            color = L.InkMuted
        )
    }
}

@Composable
private fun TaskRow(
    task: CalendarEvent,
    today: LocalDate,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMoveToday: (() -> Unit)?
) {
    val late = !task.isCompleted && task.hasDate && task.startAt.toLocalDate().isBefore(today)
    LGroupRow(
        title = task.title,
        trailing = dueLabel(task, today),
        trailingColor = if (late) L.Danger else L.OnBoxMuted,
        onClick = onOpen,
        leading = {
            LToolButton(
                if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                (if (task.isCompleted) "Reopen " else "Complete ") + task.title,
                onToggle
            )
        },
        end = {
            if (onMoveToday != null) LTextLink("Today", onMoveToday)
        }
    )
}

private fun doneStreak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minusDays(1)
    if (day !in days) return 0
    var n = 0
    while (day in days && n < 7) {
        n++
        day = day.minusDays(1)
    }
    return n
}
