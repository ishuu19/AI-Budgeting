package com.ledgerai.app.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.ScheduleImportSheet
import com.ledgerai.app.presentation.components.SuggestionsSheet
import com.ledgerai.app.presentation.screens.plan.AgendaItem
import com.ledgerai.app.presentation.screens.plan.PlanKind
import com.ledgerai.app.presentation.screens.plan.PlanViewModel
import com.ledgerai.app.presentation.screens.plan.matches
import com.ledgerai.app.presentation.screens.plan.monthGrid
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val GROUP_CAP = 5

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Calendar segment: agenda (default) or month, filtered by kind chips.
 * Tapping a row calls [onOpen] with the item key; the Plan tab owns the item sheet.
 */
@Composable
fun CalendarScreen(
    vm: PlanViewModel,
    onOpen: (String) -> Unit,
    onAdd: (LocalDate) -> Unit
) {
    val state by vm.agenda.collectAsState()
    val month by vm.month.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val suggestionsLoading by vm.suggestionsLoading.collectAsState()
    var kind by rememberSaveable { mutableStateOf(PlanKind.All) }
    var monthView by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf("") }

    if (showImport) {
        ScheduleImportSheet(
            title = "Import",
            onDismiss = { showImport = false },
            onImportText = { t, r -> vm.importText(t, r) },
            onImportCsv = { u, r -> vm.importCsv(u, r) },
            onImportImage = { u, r -> vm.importImage(u, r) }
        )
    }
    if (showSuggestions) {
        SuggestionsSheet(
            drafts = suggestions,
            loading = suggestionsLoading,
            onDismiss = { showSuggestions = false },
            onAccept = { vm.acceptSuggestion(it) },
            onDismissDraft = { vm.dismissSuggestion(it) }
        )
    }

    val items = remember(state.items, kind) { state.items.filter { kind.matches(it) } }
    val toggleDone: (AgendaItem) -> Unit = { it.event?.let { e -> vm.setDone(e, !e.isCompleted) } }
    val toggleEnabled: (AgendaItem, Boolean) -> Unit = { item, on -> item.event?.let { vm.setAlarmEnabled(it, on) } }

    LScreen(
        title = "Calendar",
        action = {
            IconButton(onClick = { showSuggestions = true; vm.loadSuggestions() }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Suggest", tint = L.Box)
            }
            IconButton(onClick = { showImport = true }) {
                Icon(Icons.Default.Upload, contentDescription = "Import", tint = L.Box)
            }
            IconButton(onClick = { monthView = !monthView }) {
                Icon(
                    if (monthView) Icons.Default.ViewAgenda else Icons.Default.CalendarMonth,
                    contentDescription = if (monthView) "Agenda view" else "Month view",
                    tint = L.Box
                )
            }
        },
        fab = {
            LFab(
                Icons.Default.Add,
                onClick = { onAdd(if (monthView) LocalDate.ofEpochDay(selectedDay) else LocalDate.now()) },
                label = "Add"
            )
        }
    ) {
        item {
            LKindChips(PlanKind.entries, kind, { it.label }, { kind = it })
        }
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = vm::retry) }
            monthView -> monthItems(
                month = month,
                items = items,
                selected = LocalDate.ofEpochDay(selectedDay),
                onSelect = { selectedDay = it.toEpochDay() },
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onOpen = onOpen,
                onToggleDone = toggleDone,
                onToggleEnabled = toggleEnabled
            )
            else -> agendaItems(
                items = items,
                expanded = expanded,
                onExpand = { expanded = "$expanded|$it" },
                onOpen = onOpen,
                onToggleDone = toggleDone,
                onToggleEnabled = toggleEnabled
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.agendaItems(
    items: List<AgendaItem>,
    expanded: String,
    onExpand: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit
) {
    val today = LocalDate.now()
    val upcoming = items.filter { !it.date.isBefore(today) }
    if (upcoming.isEmpty()) {
        item { LEmpty(Icons.Default.CalendarMonth, "Nothing planned") }
        return
    }
    val now = LocalDateTime.now()
    val next = upcoming.firstOrNull { !it.done && it.enabled && (it.date.isAfter(today) || it.allDay || !it.end.isBefore(now)) }
    if (next != null) {
        item(key = "next") {
            LHeroCard(
                label = "Next",
                title = next.title,
                sub = leadText(next, withDay = next.date != today) + if (next.place.isNotBlank()) " · ${next.place}" else "",
                onClick = { onOpen(next.key) }
            )
        }
    }
    val groups = listOf(
        Triple("Today", false, { d: LocalDate -> d == today }),
        Triple("Tomorrow", false, { d: LocalDate -> d == today.plusDays(1) }),
        Triple("This week", true, { d: LocalDate -> d.isAfter(today.plusDays(1)) && !d.isAfter(today.plusDays(6)) }),
        Triple("Later", true, { d: LocalDate -> d.isAfter(today.plusDays(6)) })
    )
    groups.forEach { (label, showDay, test) ->
        val rows = upcoming.filter { test(it.date) }
        if (rows.isEmpty()) return@forEach
        val all = expanded.split("|").contains(label)
        val shown = if (all) rows else rows.take(GROUP_CAP)
        item(key = "g-$label") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(label)
                LGroup {
                    shown.forEachIndexed { i, row ->
                        if (i > 0) LGroupDivider()
                        AgendaRow(row, showDay, onOpen, onToggleDone, onToggleEnabled)
                    }
                    if (rows.size > shown.size) {
                        LGroupDivider()
                        LGroupRow(title = "More (${rows.size - shown.size})", onClick = { onExpand(label) })
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.monthItems(
    month: LocalDate,
    items: List<AgendaItem>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit
) {
    val ym = YearMonth.from(month)
    val grid = monthGrid(ym)
    val byDay = items.groupBy { it.date }
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    item(key = "month-head") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = L.Box)
            }
            Text(
                month.format(monthFmt),
                style = MaterialTheme.typography.titleLarge,
                color = L.Ink,
                modifier = Modifier.semantics { heading() }
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = L.Box)
            }
        }
    }
    item(key = "month-dow") {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = L.InkMuted,
                    maxLines = 1
                )
            }
        }
    }
    item(key = "month-grid") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            grid.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            date = cell,
                            inMonth = cell.month == ym.month,
                            selected = cell == selected,
                            isToday = cell == LocalDate.now(),
                            count = byDay[cell]?.size ?: 0,
                            onClick = { onSelect(cell) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    item(key = "month-day") {
        GroupLabel(selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")))
    }
    val dayRows = byDay[selected].orEmpty()
    if (dayRows.isEmpty()) {
        item(key = "month-empty") { LEmpty(Icons.Default.CalendarMonth, "Free day") }
    } else {
        item(key = "month-rows") {
            LGroup {
                dayRows.forEachIndexed { i, row ->
                    if (i > 0) LGroupDivider()
                    AgendaRow(row, false, onOpen, onToggleDone, onToggleEnabled)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = L.Ink,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() }
    )
}

private fun leadText(item: AgendaItem, withDay: Boolean): String {
    val time = when {
        item.allDay -> "All day"
        else -> item.start.format(TimeFmt)
    }
    return if (withDay) "${item.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $time" else time
}

private fun dotColor(kind: PlanKind): Color = when (kind) {
    PlanKind.Tasks, PlanKind.Study -> L.Gold
    PlanKind.Alarms, PlanKind.Habits -> L.GoldSoft
    PlanKind.Classes -> L.OnBoxMuted
    else -> L.OnBox
}

/** Timed items are taller (two lines of time); tasks and alarms are one compact line. */
@Composable
private fun AgendaRow(
    item: AgendaItem,
    showDay: Boolean,
    onOpen: (String) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit
) {
    val now = LocalDateTime.now()
    val late = item.isTask && !item.done && item.start.isBefore(now) && !item.allDay
    val dim = item.isAlarm && !item.enabled
    val titleColor = if (dim || item.done) L.OnBoxMuted else L.OnBox
    val top = when {
        item.allDay -> "All day"
        showDay -> item.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + item.date.dayOfMonth
        else -> item.start.format(TimeFmt)
    }
    val bottom = when {
        item.allDay -> null
        showDay -> item.start.format(TimeFmt)
        item.point -> null
        else -> item.end.format(TimeFmt)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (item.point) 48.dp else 64.dp)
            .clickable { onOpen(item.key) }
            .padding(start = 16.dp, end = if (item.isTask || item.isAlarm) 4.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.width(80.dp)) {
            Text(top, style = MaterialTheme.typography.labelLarge, color = if (late) L.Danger else L.Gold)
            if (bottom != null) Text(bottom, style = MaterialTheme.typography.labelSmall, color = L.OnBoxMuted)
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor(item.kind)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null
            )
            if (!item.point || item.place.isNotBlank()) {
                Text(
                    listOf(item.label, item.place).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.reminders > 0) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Has reminder",
                tint = L.OnBoxMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        if (item.isTask) {
            IconButton(onClick = { onToggleDone(item) }) {
                Icon(
                    if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = (if (item.done) "Reopen " else "Complete ") + item.title,
                    tint = if (item.done) L.Gold else L.OnBoxMuted
                )
            }
        }
        if (item.isAlarm) {
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggleEnabled(item, it) },
                modifier = Modifier.semantics {
                    contentDescription = (if (item.enabled) "Turn off " else "Turn on ") + item.title
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = L.Gold,
                    checkedTrackColor = L.BoxDeep,
                    checkedBorderColor = L.BoxDeep,
                    uncheckedThumbColor = L.OnBoxMuted,
                    uncheckedTrackColor = L.Box,
                    uncheckedBorderColor = L.OnBoxMuted
                )
            )
            Box(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        selected -> L.Gold
        isToday -> L.Box
        else -> L.Page
    }
    val ink = when {
        selected -> L.BoxDeep
        isToday -> L.OnBox
        !inMonth -> L.InkMuted.copy(alpha = 0.55f)
        else -> L.Ink
    }
    val desc = date.format(DateTimeFormatter.ofPattern("MMMM d")) + if (count > 0) ", $count planned" else ""
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        count == 0 -> Color.Transparent
                        selected -> L.BoxDeep
                        isToday -> L.Gold
                        else -> L.Box
                    }
                )
        )
    }
}
