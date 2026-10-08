package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_ROUTINE_SLOT
import com.ledgerai.app.domain.model.RoutineSlotReminder
import com.ledgerai.app.domain.model.ScheduleSlot
import com.ledgerai.app.domain.schedule.allBeforeEventOptions
import com.ledgerai.app.domain.schedule.labelForMinutesBefore
import com.ledgerai.app.data.schedule.nextOccurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSlotEditSheet(
    slot: ScheduleSlot,
    occurrenceDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (location: String, reminders: List<RoutineSlotReminder>, dayOfWeek: Int) -> Unit,
    onMoveOccurrenceDate: (LocalDate) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    var dayOfWeek by remember(slot.id) { mutableStateOf(slot.dayOfWeek) }
    var showMoveDate by remember { mutableStateOf(false) }
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var location by remember(slot.id) { mutableStateOf(slot.location) }
    var placeLinks by remember(slot.id) { mutableStateOf("") }
    var reminders by remember(slot.id) { mutableStateOf(slot.reminders) }
    var showAdd by remember { mutableStateOf(false) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("EEE HH:mm") }

    LSheet(
        title = slot.title,
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(location.trim(), reminders, dayOfWeek) }
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${slot.startTime} – ${slot.endTime}",
                style = MaterialTheme.typography.bodySmall,
                color = L.InkMuted
            )
            Text("Weekly on", style = MaterialTheme.typography.labelMedium, color = L.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayNames.forEachIndexed { index, label ->
                    val dow = index + 1
                    LChip(label, selected = dayOfWeek == dow, onClick = { dayOfWeek = dow })
                }
            }
            LGhostButton("Move this class to another date", onClick = { showMoveDate = true })
            LPlaceField(
                location = location,
                onLocationChange = { location = it },
                links = placeLinks,
                onLinksChange = { placeLinks = it },
                label = "Room / location"
            )
            Text("Reminders (before class)", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            reminders.forEach { r ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${r.label} · ${r.remindAt.format(timeFmt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { reminders = reminders.filterNot { it.id == r.id && it.remindAt == r.remindAt } }) {
                        Text("Remove", color = L.Box)
                    }
                }
            }
            onRemove?.let { remove ->
                LGhostButton("Remove from calendar", onClick = remove)
            }
            if (reminders.size < MAX_REMINDERS_PER_ROUTINE_SLOT) {
                Box {
                    LGhostButton("Add reminder", onClick = { showAdd = true })
                    DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                        allBeforeEventOptions().forEach { (_, minutes) ->
                            DropdownMenuItem(
                                text = { Text(labelForMinutesBefore(minutes)) },
                                onClick = {
                                    showAdd = false
                                    val at = nextOccurrence(slot.dayOfWeek, slot.startTime)
                                        .minusMinutes(minutes)
                                    reminders = reminders + RoutineSlotReminder(
                                        label = labelForMinutesBefore(minutes),
                                        remindAt = at,
                                        offsetMinutes = minutes.toInt().takeIf { minutes > 0 }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showMoveDate) {
        EventDatePickerDialog(
            initialDate = occurrenceDate,
            onDismiss = { showMoveDate = false },
            onConfirm = {
                onMoveOccurrenceDate(it)
                showMoveDate = false
            }
        )
    }
}
