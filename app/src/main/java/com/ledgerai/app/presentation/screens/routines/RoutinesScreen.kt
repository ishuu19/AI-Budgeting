package com.ledgerai.app.presentation.screens.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.RoutineRepository
import com.ledgerai.app.domain.model.RoutineItem
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

private val REPEAT_PRESETS = listOf(
    "DAILY" to "Daily",
    "WEEKLY" to "Weekly",
    "WEEKDAYS" to "Weekdays",
    "CUSTOM" to "Custom"
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepo: RoutineRepository
) : ViewModel() {

    val routines: StateFlow<List<RoutineItem>> = routineRepo.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRoutine(title: String, notes: String, repeatRule: String, location: String = "") {
        if (title.isBlank()) return
        viewModelScope.launch {
            routineRepo.insert(
                RoutineItem(
                    title = title.trim(),
                    notes = notes.trim(),
                    location = location.trim(),
                    repeatRule = repeatRule.trim().ifBlank { "DAILY" },
                    isActive = true
                )
            )
        }
    }

    fun updateRoutine(routine: RoutineItem, title: String, notes: String, repeatRule: String, location: String = "") {
        if (title.isBlank()) return
        viewModelScope.launch {
            routineRepo.update(
                routine.copy(
                    title = title.trim(),
                    notes = notes.trim(),
                    location = location.trim(),
                    repeatRule = repeatRule.trim().ifBlank { "DAILY" }
                )
            )
        }
    }

    fun setActive(routine: RoutineItem, active: Boolean) {
        viewModelScope.launch { routineRepo.setActive(routine.id, active) }
    }

    fun deleteRoutine(routine: RoutineItem) {
        viewModelScope.launch { routineRepo.delete(routine) }
    }
}

/*
 * repeatRule encoding: `<days>[@HH:mm]` where <days> is DAILY, WEEKDAYS, WEEKENDS,
 * `DAYS:MON,WED`, or any legacy/opaque rule (WEEKLY, free text) that maps to no days.
 * Day mask bit 0 = Monday … bit 6 = Sunday.
 */
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private const val MASK_ALL = 0b1111111
private const val MASK_WEEKDAYS = 0b0011111
private const val MASK_WEEKENDS = 0b1100000
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private data class ParsedRule(val mask: Int, val time: LocalTime?, val base: String)

private fun parseRule(rule: String): ParsedRule {
    val base = rule.substringBefore('@').trim()
    val time = rule.substringAfter('@', "").trim()
        .takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalTime.parse(it, timeFmt) }.getOrNull() }
    val upper = base.uppercase()
    val mask = when {
        upper == "DAILY" -> MASK_ALL
        upper == "WEEKDAYS" -> MASK_WEEKDAYS
        upper == "WEEKENDS" -> MASK_WEEKENDS
        upper.startsWith("DAYS:") -> upper.removePrefix("DAYS:").split(',')
            .map { it.trim() }
            .fold(0) { acc, code ->
                val i = DAY_CODES.indexOf(code)
                if (i >= 0) acc or (1 shl i) else acc
            }
        else -> 0
    }
    return ParsedRule(mask, time, base)
}

private fun buildRule(mask: Int, time: LocalTime?, fallbackBase: String): String {
    val base = when (mask) {
        MASK_ALL -> "DAILY"
        MASK_WEEKDAYS -> "WEEKDAYS"
        0 -> fallbackBase.ifBlank { "DAILY" }
        else -> "DAYS:" + DAY_CODES.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(",")
    }
    return if (time != null) "$base@${time.format(timeFmt)}" else base
}

private fun repeatRuleLabel(rule: String): String =
    REPEAT_PRESETS.firstOrNull { it.first.equals(rule, ignoreCase = true) }?.second
        ?: rule.ifBlank { "Custom" }

/** Next fire from DAILY / WEEKLY / WEEKDAYS / DAYS: mask + optional @HH:mm. WEEKLY → Monday. */
private fun nextRunAt(parsed: ParsedRule, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
    val mask = when {
        parsed.mask != 0 -> parsed.mask
        parsed.base.equals("WEEKLY", ignoreCase = true) -> 1 shl 0
        else -> return null
    }
    val clock = parsed.time ?: LocalTime.MIDNIGHT
    for (offset in 0..7) {
        val day = now.toLocalDate().plusDays(offset.toLong())
        val bit = day.dayOfWeek.value - 1
        if ((mask and (1 shl bit)) == 0) continue
        val candidate = LocalDateTime.of(day, clock)
        if (candidate.isAfter(now)) return candidate
    }
    return null
}

private fun nextRunLabel(rule: String, now: LocalDateTime = LocalDateTime.now()): String {
    val parsed = parseRule(rule)
    val next = nextRunAt(parsed, now)
    if (next != null) {
        val date = next.toLocalDate()
        val today = now.toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        }
        return parsed.time?.let { "$day · ${it.format(timeFmt)}" } ?: day
    }
    if (parsed.base.equals("WEEKLY", ignoreCase = true)) {
        return parsed.time?.let { "Weekly · ${it.format(timeFmt)}" } ?: "Weekly"
    }
    return parsed.time?.format(timeFmt) ?: repeatRuleLabel(parsed.base)
}

/** Sheet target: `null` id = new routine. */
private data class RoutineSheet(val id: Long?)

@Composable
fun RoutinesScreen(onBack: () -> Unit = {}, viewModel: RoutinesViewModel = hiltViewModel()) {
    val routines by viewModel.routines.collectAsState()
    var sheet by remember { mutableStateOf<RoutineSheet?>(null) }
    val now = LocalDateTime.now()
    val active = routines.filter { it.isActive }
    val paused = routines.filter { !it.isActive }
    val next = active
        .mapNotNull { routine -> nextRunAt(parseRule(routine.repeatRule), now)?.let { at -> at to routine } }
        .minByOrNull { it.first }

    LScreen(
        title = "Routines",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { sheet = RoutineSheet(null) }) }
    ) {
        item {
            LHero(
                label = "Active",
                value = active.size.toString(),
                sub = next?.let { nextRunLabel(it.second.repeatRule, now) }
            )
        }
        if (routines.isEmpty()) {
            item { LEmpty(Icons.Filled.Repeat, "No routines") }
        } else {
            if (active.isNotEmpty()) {
                item { LSection("Active") }
                items(active, key = { "a-${it.id}" }) { routine ->
                    RoutineRow(
                        routine = routine,
                        onClick = { sheet = RoutineSheet(routine.id) },
                        onToggleActive = { viewModel.setActive(routine, it) }
                    )
                }
            }
            if (paused.isNotEmpty()) {
                item { LSection("Paused") }
                items(paused, key = { "p-${it.id}" }) { routine ->
                    RoutineRow(
                        routine = routine,
                        onClick = { sheet = RoutineSheet(routine.id) },
                        onToggleActive = { viewModel.setActive(routine, it) }
                    )
                }
            }
        }
    }

    sheet?.let { target ->
        val existing = target.id?.let { id -> routines.firstOrNull { it.id == id } }
        if (target.id != null && existing == null) {
            LaunchedEffect(target) { sheet = null }
        } else {
            RoutineEditSheet(
                existing = existing,
                onDismiss = { sheet = null },
                onSave = { title, notes, rule, place ->
                    if (existing == null) viewModel.addRoutine(title, notes, rule, place)
                    else viewModel.updateRoutine(existing, title, notes, rule, place)
                    sheet = null
                },
                onDelete = {
                    existing?.let { viewModel.deleteRoutine(it) }
                    sheet = null
                }
            )
        }
    }
}

@Composable
private fun RoutineRow(
    routine: RoutineItem,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit
) {
    LRow(
        title = routine.title,
        sub = nextRunLabel(routine.repeatRule),
        onClick = onClick,
        end = {
            Switch(
                checked = routine.isActive,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = L.Gold,
                    checkedTrackColor = L.BoxDeep,
                    checkedBorderColor = L.BoxDeep,
                    uncheckedThumbColor = L.OnBoxMuted,
                    uncheckedTrackColor = L.Box,
                    uncheckedBorderColor = L.OnBoxMuted
                )
            )
        }
    )
}

@Composable
private fun DayLetters(
    mask: Int,
    onBox: Boolean,
    modifier: Modifier = Modifier,
    onToggle: ((Int) -> Unit)? = null
) {
    val size = if (onToggle != null) 38.dp else 26.dp
    Row(
        modifier = modifier,
        horizontalArrangement = if (onToggle != null) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
    ) {
        DAY_LETTERS.forEachIndexed { i, letter ->
            val on = (mask and (1 shl i)) != 0
            val base = Modifier.size(size).clip(CircleShape)
            Box(
                modifier = (if (onToggle != null) base.clickable { onToggle(i) } else base)
                    .background(
                        when {
                            on -> L.Gold
                            onBox -> L.BoxDeep
                            else -> L.Line
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        on -> L.BoxDeep
                        onBox -> L.OnBoxMuted
                        else -> L.Ink
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditSheet(
    existing: RoutineItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, repeatRule: String, location: String) -> Unit,
    onDelete: () -> Unit
) {
    val initial = remember { parseRule(existing?.repeatRule ?: "DAILY") }
    val fallbackBase = if (initial.mask == 0 && existing != null) initial.base else ""
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var mask by remember { mutableIntStateOf(initial.mask) }
    var hasTime by remember { mutableStateOf(initial.time != null) }
    val timeState = rememberTimePickerState(
        initialHour = initial.time?.hour ?: 7,
        initialMinute = initial.time?.minute ?: 0,
        is24Hour = false
    )

    LSheet(
        title = if (existing == null) "New routine" else "Routine",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            val time = if (hasTime) LocalTime.of(timeState.hour, timeState.minute) else null
            onSave(title, notes, buildRule(mask, time, fallbackBase), location)
        },
        primaryEnabled = title.isNotBlank() && (mask != 0 || fallbackBase.isNotBlank()),
        secondary = if (existing != null) "Delete" else null,
        onSecondary = onDelete
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LField(title, { title = it }, "Name")
            LField(location, { location = it }, "Place")
            LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
            DayLetters(
                mask = mask,
                onBox = false,
                modifier = Modifier.fillMaxWidth(),
                onToggle = { i -> mask = mask xor (1 shl i) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip(
                    if (hasTime) LocalTime.of(timeState.hour, timeState.minute).format(timeFmt) else "Time",
                    selected = hasTime,
                    onClick = { hasTime = true }
                )
                if (hasTime) LChip("None", selected = false, onClick = { hasTime = false })
            }
            if (hasTime) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState, colors = lTimeColors())
                }
            }
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
