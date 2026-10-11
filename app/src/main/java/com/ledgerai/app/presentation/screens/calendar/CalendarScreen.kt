package com.ledgerai.app.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSwitch
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.ScheduleImportSheet
import com.ledgerai.app.presentation.components.SuggestionsSheet
import com.ledgerai.app.presentation.screens.plan.AgendaItem
import com.ledgerai.app.presentation.screens.plan.PlanKind
import com.ledgerai.app.presentation.screens.plan.PlanKindFilters
import com.ledgerai.app.presentation.screens.plan.PlanViewModel
import com.ledgerai.app.presentation.screens.plan.matches
import com.ledgerai.app.presentation.screens.plan.monthGrid
import com.ledgerai.app.presentation.theme.CategoryColors
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val GROUP_CAP = 5

/** A job follow-up shown on the main calendar. Jobs does not keep a second month grid. */
data class DayFollowUp(val id: Long, val title: String, val day: LocalDate)

/** Hooks the Plan tab's icon row to this calendar: suggest and import. */
class CalendarTools {
    var suggest: () -> Unit = {}
    var import: () -> Unit = {}
}

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val DueFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/** Two letters, so Tuesday and Thursday are not both "T". */
private fun dowLabel(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Mo"
    DayOfWeek.TUESDAY -> "Tu"
    DayOfWeek.WEDNESDAY -> "We"
    DayOfWeek.THURSDAY -> "Th"
    DayOfWeek.FRIDAY -> "Fr"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "Su"
}
private val MonthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

/**
 * Calendar segment. A headline, then Week or Month, then that day's agenda.
 * Tapping a row calls [onOpen] with the item key; the Plan tab owns the item sheet.
 */
@Composable
fun CalendarScreen(
    vm: PlanViewModel,
    onOpen: (String) -> Unit,
    onAdd: (LocalDate) -> Unit,
    tools: CalendarTools = CalendarTools(),
    query: String = "",
    followUps: List<DayFollowUp> = emptyList(),
    onOpenJob: (Long) -> Unit = {},
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

    val activeKind = if (kind in PlanKindFilters) kind else PlanKind.All
    val items = remember(state.items, activeKind, query) {
        state.items.filter { activeKind.matches(it) && it.matchesQuery(query) }
    }
    val jobs = remember(followUps, query) {
        val q = query.trim()
        if (q.isEmpty()) followUps else followUps.filter { it.title.contains(q, ignoreCase = true) }
    }
    val toggleDone: (AgendaItem) -> Unit = { it.event?.let { e -> vm.setDone(e, !e.isCompleted) } }
    val toggleEnabled: (AgendaItem, Boolean) -> Unit = { item, on -> item.event?.let { vm.setAlarmEnabled(it, on) } }

    val today = LocalDate.now()
    val selected = LocalDate.ofEpochDay(selectedDay)

    SideEffect {
        tools.suggest = { showSuggestions = true; vm.loadSuggestions() }
        tools.import = { showImport = true }
    }

    LScreen(
        title = "Calendar",
        fab = { LFab(Icons.Default.Add, onClick = { onAdd(selected) }, label = "New event") }
    ) {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = vm::retry) }
            else -> {
                val byDay = items.groupBy { it.date }
                val dayItems = byDay[selected].orEmpty()
                val dayJobs = jobs.filter { it.day == selected }
                val ym = YearMonth.from(month)
                val cells: List<LocalDate> = if (monthView) {
                    monthGrid(ym)
                } else {
                    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    List(7) { monday.plusDays(it.toLong()) }
                }
                val prev: () -> Unit = { if (monthView) { vm.prevMonth() } else { selectedDay = selectedDay - 7 } }
                val next: () -> Unit = { if (monthView) { vm.nextMonth() } else { selectedDay = selectedDay + 7 } }
                val headline = todayHeadline(state.items, LocalDateTime.now())

                if (headline != null) {
                    item(key = "headline") {
                        LHeroCard(label = headline.first, title = headline.second)
                    }
                }
                item(key = "cal") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LSegments(
                            options = listOf(false, true),
                            selected = monthView,
                            label = { if (it) "Month" else "Week" },
                            onSelect = { monthView = it }
                        )
                        NavHeader(
                            title = (if (monthView) month else selected).format(MonthFmt),
                            prevLabel = if (monthView) "Previous month" else "Previous week",
                            nextLabel = if (monthView) "Next month" else "Next week",
                            onPrev = prev,
                            onNext = next,
                            onToday = if (selected != today) ({ selectedDay = today.toEpochDay() }) else null
                        )
                        if (monthView) {
                            Row(Modifier.fillMaxWidth()) {
                                DayOfWeek.entries.forEach { dow ->
                                    Text(
                                        dowLabel(dow),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = L.InkMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { cell ->
                                    val planned = (byDay[cell]?.size ?: 0) + jobs.count { it.day == cell }
                                    DayCell(
                                        date = cell,
                                        weekday = if (monthView) null else dowLabel(cell.dayOfWeek),
                                        inMonth = !monthView || cell.month == ym.month,
                                        selected = cell == selected,
                                        isToday = cell == today,
                                        marks = marksFor(cell, byDay, jobs),
                                        planned = planned,
                                        onClick = { selectedDay = cell.toEpochDay() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                val present = state.items.map { it.kind }.toSet()
                val choices = PlanKindFilters.filter { it == PlanKind.All || it in present }
                if (choices.size >= 3) {
                    item(key = "chips") {
                        LKindChips(choices, activeKind, { it.label }, { kind = it })
                    }
                }

                if (query.isNotBlank()) {
                    val count = items.size + jobs.size
                    item(key = "results-head") {
                        LSection(if (count == 0) "No matches" else if (count == 1) "1 match" else "$count matches")
                    }
                    if (count > 0) {
                        item(key = "results") {
                            LGroup {
                                var first = true
                                items.sortedBy { it.start }.take(60).forEach { row ->
                                    if (!first) LGroupDivider()
                                    first = false
                                    AgendaRow(row, true, onOpen, toggleDone, toggleEnabled)
                                }
                                jobs.take(20).forEach { job ->
                                    if (!first) LGroupDivider()
                                    first = false
                                    FollowUpRow(job, onOpenJob)
                                }
                            }
                        }
                    }
                    return@LScreen
                }

                item(key = "day-head") { LSection(dayTitle(selected, today)) }
                if (dayItems.isEmpty() && dayJobs.isEmpty()) {
                    item(key = "day-empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LEmpty(Icons.Default.CalendarMonth, "Free day")
                            LGhostButton("Ask AI to plan it", onClick = {
                                showSuggestions = true
                                vm.loadSuggestions()
                            })
                        }
                    }
                } else {
                    item(key = "day-rows") {
                        DayBody(
                            rows = dayItems,
                            follows = dayJobs,
                            showNow = selected == today,
                            onOpen = onOpen,
                            onOpenJob = onOpenJob,
                            onToggleDone = toggleDone,
                            onToggleEnabled = toggleEnabled
                        )
                    }
                }

                if (!monthView) {
                    comingUp(
                        items = items,
                        selected = selected,
                        expanded = expanded,
                        onExpand = { expanded = "$expanded|$it" },
                        onOpen = onOpen,
                        onToggleDone = toggleDone,
                        onToggleEnabled = toggleEnabled
                    )
                }
            }
        }
    }
}

private fun AgendaItem.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return title.contains(q, ignoreCase = true) || place.contains(q, ignoreCase = true) || label.contains(q, ignoreCase = true)
}

/** "Next" when something is coming up today, otherwise how full the day is. */
private fun todayHeadline(items: List<AgendaItem>, now: LocalDateTime): Pair<String, String>? {
    val open = items.filter { it.date == now.toLocalDate() && !it.done && !(it.isAlarm && !it.enabled) }
    if (open.isEmpty()) return null
    val current = open.firstOrNull { !it.allDay && !it.point && !it.start.isAfter(now) && it.end.isAfter(now) }
    if (current != null) return "Now" to current.title
    val upcoming = open.filter { !it.allDay && it.start.isAfter(now) }.minByOrNull { it.start }
    if (upcoming != null) {
        val mins = Duration.between(now, upcoming.start).toMinutes().coerceAtLeast(1)
        val whenLabel = if (mins < 60) "in $mins min" else "in ${mins / 60}h"
        return "Next" to "${upcoming.title} $whenLabel"
    }
    val lastEnd = open.filter { !it.allDay && !it.point }.maxOfOrNull { it.end }
    val things = if (open.size == 1) "1 thing today" else "${open.size} things today"
    val free = lastEnd?.let { ", free after ${it.format(TimeFmt)}" }.orEmpty()
    return "Today" to (things + free)
}

private fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today · " + day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    today.plusDays(1) -> "Tomorrow · " + day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    today.minusDays(1) -> "Yesterday · " + day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    else -> day.format(DayFmt)
}

private fun marksFor(day: LocalDate, byDay: Map<LocalDate, List<AgendaItem>>, jobs: List<DayFollowUp>): List<Color> {
    val kinds = byDay[day].orEmpty().map { dotColor(it.kind) }.distinct()
    val job = if (jobs.any { it.day == day }) L.Accent else null
    return (kinds + listOfNotNull(job)).distinct().take(3)
}

/** Days after the selected one, in two capped groups. */
private fun LazyListScope.comingUp(
    items: List<AgendaItem>,
    selected: LocalDate,
    expanded: String,
    onExpand: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit
) {
    val after = items.filter { it.date.isAfter(selected) }
    val groups = listOf(
        "Next 7 days" to after.filter { !it.date.isAfter(selected.plusDays(7)) },
        "Later" to after.filter { it.date.isAfter(selected.plusDays(7)) }
    )
    groups.forEach { (label, rows) ->
        if (rows.isEmpty()) return@forEach
        val all = expanded.split("|").contains(label)
        val shown = if (all) rows else rows.take(GROUP_CAP)
        item(key = "g-$label") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LSection(
                    label,
                    action = if (!all && rows.size > GROUP_CAP) "All ${rows.size}" else null,
                    onAction = { onExpand(label) }
                )
                LGroup {
                    shown.forEachIndexed { i, row ->
                        if (i > 0) LGroupDivider()
                        AgendaRow(row, true, onOpen, onToggleDone, onToggleEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavHeader(
    title: String,
    prevLabel: String,
    nextLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: (() -> Unit)?
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LToolButton(Icons.Default.ChevronLeft, prevLabel, onPrev)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = L.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        LToolButton(Icons.Default.ChevronRight, nextLabel, onNext)
        if (onToday != null) LTextLink("Today", onToday)
    }
}

private fun dotColor(kind: PlanKind): Color = when (kind) {
    PlanKind.Events -> CategoryColors[2]
    PlanKind.Tasks -> CategoryColors[1]
    PlanKind.Classes -> CategoryColors[3]
    PlanKind.Alarms -> CategoryColors[5]
    PlanKind.Study -> CategoryColors[4]
    PlanKind.Habits -> CategoryColors[6]
    PlanKind.All -> CategoryColors[7]
}

@Composable
private fun DayBody(
    rows: List<AgendaItem>,
    follows: List<DayFollowUp>,
    showNow: Boolean,
    onOpen: (String) -> Unit,
    onOpenJob: (Long) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit,
) {
    val points = rows.filter { it.allDay || it.point }.sortedBy { it.start }
    val timed = rows.filter { !it.allDay && !it.point }.sortedBy { it.start }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (points.isNotEmpty() || follows.isNotEmpty()) {
            LGroup {
                var first = true
                follows.forEach { job ->
                    if (!first) LGroupDivider()
                    first = false
                    FollowUpRow(job, onOpenJob)
                }
                points.forEach { row ->
                    if (!first) LGroupDivider()
                    first = false
                    AgendaRow(row, false, onOpen, onToggleDone, onToggleEnabled)
                }
            }
        }
        if (timed.isNotEmpty()) {
            val now = LocalDateTime.now()
            LGroup {
                timed.forEachIndexed { i, row ->
                    if (i > 0) {
                        val prev = timed[i - 1]
                        val gapMin = Duration.between(prev.end, row.start).toMinutes()
                        if (showNow && now.isAfter(prev.end) && now.isBefore(row.start)) {
                            LGroupDivider()
                            NowMark()
                        }
                        if (gapMin >= 60) {
                            LGroupDivider()
                            Text(
                                freeLabel(gapMin),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 16.dp)
                                    .wrapContentHeight(Alignment.CenterVertically),
                                style = MaterialTheme.typography.bodySmall,
                                color = L.InkMuted
                            )
                        }
                        LGroupDivider()
                    }
                    val minutes = Duration.between(row.start, row.end).toMinutes().coerceAtLeast(15)
                    val block = ((minutes / 60f) * 64f).dp.coerceIn(48.dp, 192.dp)
                    AgendaRow(
                        row,
                        false,
                        onOpen,
                        onToggleDone,
                        onToggleEnabled,
                        Modifier.heightIn(min = block)
                    )
                    if (showNow && !now.isBefore(row.start) && now.isBefore(row.end)) NowMark()
                }
            }
        }
    }
}

private fun freeLabel(minutes: Long): String = when {
    minutes < 60 -> "${minutes}m free"
    else -> "${minutes / 60}h free"
}

@Composable
private fun NowMark() {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Now", style = MaterialTheme.typography.labelSmall, color = L.Accent)
        Box(Modifier.weight(1f).height(1.dp).background(L.Accent))
    }
}

private fun dueDay(day: LocalDate): String {
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, day)
    return when {
        days < 0 -> if (days == -1L) "1 day overdue" else "${-days} days overdue"
        days == 0L -> "Due today"
        else -> "Due ${day.format(DueFmt)}"
    }
}

@Composable
private fun FollowUpRow(job: DayFollowUp, onOpenJob: (Long) -> Unit) {
    LGroupRow(
        title = job.title,
        sub = "Follow up",
        trailing = dueDay(job.day),
        trailingColor = if (job.day.isBefore(LocalDate.now())) L.Danger else L.OnBoxMuted,
        onClick = { onOpenJob(job.id) }
    )
}

@Composable
private fun AgendaRow(
    item: AgendaItem,
    showDay: Boolean,
    onOpen: (String) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onToggleEnabled: (AgendaItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = LocalDateTime.now()
    val late = item.isTask && !item.done && item.start.isBefore(now) && !item.allDay
    LGroupRow(
        title = item.title,
        sub = item.place.takeIf { it.isNotBlank() },
        onClick = { onOpen(item.key) },
        modifier = modifier,
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.isTask) {
                    LToolButton(
                        if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        (if (item.done) "Reopen " else "Complete ") + item.title,
                        { onToggleDone(item) }
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (item.allDay) "All day" else item.start.format(TimeFmt),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (late) L.Danger else L.InkMuted,
                        maxLines = 1
                    )
                    if (showDay) {
                        Text(
                            item.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = L.InkMuted,
                            maxLines = 1
                        )
                    } else if (!item.allDay && !item.point) {
                        Text(
                            item.end.format(TimeFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = L.InkMuted,
                            maxLines = 1
                        )
                    }
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor(item.kind)))
            }
        },
        end = {
            if (item.isAlarm) {
                LSwitch(
                    checked = item.enabled,
                    onCheckedChange = { onToggleEnabled(item, it) },
                    modifier = Modifier.semantics {
                        contentDescription = (if (item.enabled) "Turn off " else "Turn on ") + item.title
                    }
                )
            }
        }
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    marks: List<Color>,
    planned: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weekday: String? = null,
) {
    val ink = when {
        selected -> L.OnAccent
        !inMonth -> L.InkMuted
        else -> L.Ink
    }
    val desc = date.format(DateTimeFormatter.ofPattern("MMMM d")) + if (planned > 0) ", $planned planned" else ""
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (weekday != null) {
            Text(weekday, style = MaterialTheme.typography.labelSmall, color = L.InkMuted, maxLines = 1)
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) L.Accent else Color.Transparent)
                .then(if (isToday && !selected) Modifier.border(1.dp, L.Primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                textAlign = TextAlign.Center
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (marks.isEmpty()) {
                Box(Modifier.size(8.dp))
            } else {
                marks.forEach { color ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}
