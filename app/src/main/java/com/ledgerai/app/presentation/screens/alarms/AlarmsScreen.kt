package com.ledgerai.app.presentation.screens.alarms

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AlarmRepository
import com.ledgerai.app.domain.model.AlarmItem
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.service.AlarmToneHelper
import com.ledgerai.app.service.AlarmTriggerCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Weekday bits matching AlarmItem.repeatDays (Sun=1 … Sat=64). */
private val WEEKDAY_BITS: Map<DayOfWeek, Int> = mapOf(
    DayOfWeek.SUNDAY to 1,
    DayOfWeek.MONDAY to 2,
    DayOfWeek.TUESDAY to 4,
    DayOfWeek.WEDNESDAY to 8,
    DayOfWeek.THURSDAY to 16,
    DayOfWeek.FRIDAY to 32,
    DayOfWeek.SATURDAY to 64
)

/** Display order Monday → Sunday. */
private val DISPLAY_DAYS: List<Pair<String, Int>> = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
).map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) to WEEKDAY_BITS.getValue(it) }

private fun localFromMillis(millis: Long, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

private fun nextFireDay(at: LocalDateTime, now: LocalDateTime): String {
    val date = at.toLocalDate()
    return if (date == now.toLocalDate()) {
        "Today"
    } else {
        at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replace(".", "")
    }
}

/** Short local next-fire, e.g. "Today 7:00" or "Mon 7:00". */
private fun formatNextFire(
    millis: Long,
    now: LocalDateTime,
    zone: ZoneId,
    timeFmt: DateTimeFormatter
): String {
    val at = localFromMillis(millis, zone)
    return "${nextFireDay(at, now)} ${at.toLocalTime().format(timeFmt)}"
}

/** Enabled first, then soonest [AlarmTriggerCalc.nextTriggerMillis]. */
private fun sortAlarms(
    alarms: List<AlarmItem>,
    now: LocalDateTime,
    zone: ZoneId
): List<AlarmItem> = alarms.sortedWith(
    compareByDescending<AlarmItem> { it.isEnabled }
        .thenBy { AlarmTriggerCalc.nextTriggerMillis(it.time, it.repeatDays, now, zone) }
        .thenBy { it.id }
)

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarmRepo: AlarmRepository
) : ViewModel() {

    val alarms: StateFlow<List<AlarmItem>> = alarmRepo.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAlarm(label: String, hour: Int, minute: Int, toneUri: String?, repeatDays: Int, location: String = "") {
        viewModelScope.launch {
            val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            alarmRepo.insert(
                AlarmItem(
                    label = label.trim().ifBlank { "Alarm" },
                    time = time,
                    isEnabled = true,
                    repeatDays = repeatDays,
                    toneUri = toneUri,
                    location = location.trim()
                )
            )
        }
    }

    /** Persists edits; [AlarmRepository.update] reschedules or cancels as needed. */
    fun updateAlarm(
        alarm: AlarmItem,
        label: String,
        hour: Int,
        minute: Int,
        toneUri: String?,
        repeatDays: Int,
        location: String = ""
    ) {
        viewModelScope.launch {
            alarmRepo.update(
                alarm.copy(
                    label = label.trim().ifBlank { "Alarm" },
                    time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                    repeatDays = repeatDays,
                    toneUri = toneUri,
                    location = location.trim()
                )
            )
        }
    }

    fun setEnabled(alarm: AlarmItem, enabled: Boolean) {
        viewModelScope.launch { alarmRepo.setEnabled(alarm.id, enabled) }
    }

    fun setTone(alarm: AlarmItem, toneUri: String?) {
        viewModelScope.launch { alarmRepo.setToneUri(alarm.id, toneUri) }
    }

    fun setRepeatDays(alarm: AlarmItem, repeatDays: Int) {
        viewModelScope.launch {
            alarmRepo.update(alarm.copy(repeatDays = repeatDays))
        }
    }

    fun deleteAlarm(alarm: AlarmItem) {
        viewModelScope.launch { alarmRepo.delete(alarm) }
    }
}

/** Sheet target: `null` id = new alarm. */
private data class AlarmSheet(val id: Long?)

@Composable
fun AlarmsScreen(onBack: () -> Unit = {}, viewModel: AlarmsViewModel = hiltViewModel()) {
    val alarms by viewModel.alarms.collectAsState()
    var sheet by remember { mutableStateOf<AlarmSheet?>(null) }
    val context = LocalContext.current
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm")
    }
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(alarms) { LocalDateTime.now() }
    val sorted = remember(alarms, now, zone) { sortAlarms(alarms, now, zone) }
    val nextEnabled = sorted.firstOrNull { it.isEnabled }

    LScreen(
        title = "Alarms",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { sheet = AlarmSheet(null) }) }
    ) {
        item(key = "hero") {
            if (nextEnabled == null) {
                LHero(label = "Next", value = "Off")
            } else {
                val millis = AlarmTriggerCalc.nextTriggerMillis(
                    nextEnabled.time,
                    nextEnabled.repeatDays,
                    now,
                    zone
                )
                LHero(
                    label = "Next",
                    value = formatNextFire(millis, now, zone, timeFmt),
                    sub = nextEnabled.label.takeIf { it.isNotBlank() }
                )
            }
        }

        if (sorted.isEmpty()) {
            item(key = "empty") { LEmpty(Icons.Filled.Alarm, "No alarms") }
        } else {
            items(sorted, key = { it.id }) { alarm ->
                LRow(
                    title = alarm.time.format(timeFmt),
                    sub = if (alarm.isEnabled) {
                        formatNextFire(
                            AlarmTriggerCalc.nextTriggerMillis(alarm.time, alarm.repeatDays, now, zone),
                            now,
                            zone,
                            timeFmt
                        )
                    } else {
                        "Off"
                    },
                    trailing = alarm.label,
                    onClick = { sheet = AlarmSheet(alarm.id) },
                    end = {
                        Switch(
                            checked = alarm.isEnabled,
                            onCheckedChange = { viewModel.setEnabled(alarm, it) },
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
        }
    }

    sheet?.let { target ->
        val existing = target.id?.let { id -> alarms.firstOrNull { it.id == id } }
        if (target.id != null && existing == null) {
            LaunchedEffect(target) { sheet = null }
        } else {
            AlarmEditSheet(
                existing = existing,
                onDismiss = { sheet = null },
                onSave = { label, hour, minute, toneUri, repeatDays, place ->
                    if (existing == null) viewModel.addAlarm(label, hour, minute, toneUri, repeatDays, place)
                    else viewModel.updateAlarm(existing, label, hour, minute, toneUri, repeatDays, place)
                    sheet = null
                },
                onCopy = existing?.let {
                    { label: String, hour: Int, minute: Int, toneUri: String?, repeatDays: Int, place: String ->
                        viewModel.addAlarm(label, hour, minute, toneUri, repeatDays, place)
                        sheet = null
                    }
                },
                onDelete = {
                    existing?.let { viewModel.deleteAlarm(it) }
                    sheet = null
                }
            )
        }
    }
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
        DISPLAY_DAYS.forEach { (letter, bit) ->
            val on = (mask and bit) != 0
            val base = Modifier.size(size).clip(CircleShape)
            Box(
                modifier = (if (onToggle != null) base.clickable { onToggle(bit) } else base)
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
private fun AlarmEditSheet(
    existing: AlarmItem?,
    onDismiss: () -> Unit,
    onSave: (label: String, hour: Int, minute: Int, toneUri: String?, repeatDays: Int, location: String) -> Unit,
    onCopy: ((label: String, hour: Int, minute: Int, toneUri: String?, repeatDays: Int, location: String) -> Unit)? = null,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val initialTime = remember { existing?.time ?: LocalTime.now().plusMinutes(1) }
    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false
    )
    val defaultTone = AlarmToneHelper.builtInTones.first().uriString
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var repeatDays by remember { mutableIntStateOf(existing?.repeatDays ?: 0) }
    var toneUri by remember { mutableStateOf(if (existing != null) existing.toneUri else defaultTone) }

    val customToneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        AlarmToneHelper.copyCustomTone(context, uri)
            .onSuccess { path -> toneUri = path }
            .onFailure { e ->
                Toast.makeText(context, e.message ?: "Import failed", Toast.LENGTH_LONG).show()
            }
    }

    val selectedTone = toneUri ?: defaultTone
    val isCustomTone = AlarmToneHelper.builtInTones.none { it.uriString == selectedTone }

    LSheet(
        title = if (existing == null) "New alarm" else "Alarm",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(label, timeState.hour, timeState.minute, toneUri, repeatDays, location) },
        secondary = if (onCopy != null) "Copy" else null,
        onSecondary = {
            onCopy?.invoke(label, timeState.hour, timeState.minute, toneUri, repeatDays, location)
        }
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState, colors = lTimeColors())
            }
            LField(label, { label = it }, "Label")
            DayLetters(
                mask = repeatDays,
                onBox = false,
                modifier = Modifier.fillMaxWidth(),
                onToggle = { bit -> repeatDays = repeatDays xor bit }
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = L.Box,
                    modifier = Modifier.size(18.dp)
                )
                if (isCustomTone) {
                    LChip(AlarmToneHelper.displayName(toneUri), selected = true, onClick = {})
                }
                AlarmToneHelper.builtInTones.forEach { tone ->
                    LChip(
                        tone.displayName,
                        selected = selectedTone == tone.uriString,
                        onClick = { toneUri = tone.uriString }
                    )
                }
                LChip("Custom", selected = false, onClick = {
                    customToneLauncher.launch(arrayOf("audio/*"))
                })
            }
            if (existing != null) {
                LGhostButton("Delete", onDelete)
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
