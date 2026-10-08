package com.ledgerai.app.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventEditSheet(
    existing: CalendarEvent?,
    defaultDate: LocalDate,
    initialLeaveBy: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent, leaveByEnabled: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var links by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(existing?.startAt?.toLocalDate() ?: defaultDate) }
    var frequency by remember {
        mutableStateOf(existing?.recurrence?.frequency ?: RecurrenceFrequency.NONE)
    }
    var interval by remember { mutableStateOf(existing?.recurrence?.interval?.toString() ?: "1") }
    var weekDays by remember {
        mutableStateOf(
            existing?.recurrence?.weekDays?.ifEmpty {
                setOf((existing?.startAt ?: defaultDate.atTime(9, 0)).dayOfWeek.value)
            } ?: setOf(defaultDate.dayOfWeek.value)
        )
    }
    var specificDates by remember {
        mutableStateOf(existing?.recurrence?.specificDates ?: emptySet())
    }
    var untilDate by remember { mutableStateOf(existing?.recurrence?.until) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var pickExtraDate by remember { mutableStateOf(false) }
    var leaveByEnabled by remember(existing?.id, initialLeaveBy) { mutableStateOf(initialLeaveBy) }
    val timeState = rememberTimePickerState(
        initialHour = existing?.startAt?.hour ?: 9,
        initialMinute = existing?.startAt?.minute ?: 0,
        is24Hour = false
    )
    val endTimeState = rememberTimePickerState(
        initialHour = existing?.endAt?.hour ?: 10,
        initialMinute = existing?.endAt?.minute ?: 0,
        is24Hour = false
    )
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    LSheet(
        title = if (existing == null) "New event" else "Edit event",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            val start = LocalDateTime.of(eventDate, LocalTime.of(timeState.hour, timeState.minute))
            var end = LocalDateTime.of(eventDate, LocalTime.of(endTimeState.hour, endTimeState.minute))
            if (!end.isAfter(start)) end = end.plusHours(1)
            val recurrence = when (frequency) {
                RecurrenceFrequency.NONE -> EventRecurrence(frequency = RecurrenceFrequency.NONE)
                RecurrenceFrequency.SPECIFIC_DATES -> EventRecurrence(
                    frequency = frequency,
                    specificDates = specificDates.ifEmpty { setOf(eventDate) },
                    until = untilDate
                )
                else -> EventRecurrence(
                    frequency = frequency,
                    interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    weekDays = if (frequency == RecurrenceFrequency.WEEKLY) weekDays else emptySet(),
                    until = untilDate
                )
            }
            onSave(
                CalendarEvent(
                    id = existing?.id ?: 0L,
                    title = title.trim(),
                    location = location.trim(),
                    startAt = start,
                    endAt = end,
                    kind = existing?.kind ?: CalendarEventKind.PERSONAL,
                    recurrence = recurrence
                ),
                leaveByEnabled
            )
        },
        primaryEnabled = title.isNotBlank()
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LField(title, { title = it }, "Title")
            LPlaceField(location, { location = it }, links, { links = it }, "Place")
            val previewStart = LocalDateTime.of(
                eventDate,
                LocalTime.of(timeState.hour, timeState.minute)
            )
            LeaveByToggle(
                enabled = leaveByEnabled,
                onEnabledChange = { leaveByEnabled = it },
                eventStart = previewStart
            )
            LChip(eventDate.format(dateFmt), selected = true, onClick = { showDatePicker = true })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Start", color = L.InkMuted)
                TimePicker(state = timeState)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("End", color = L.InkMuted)
                TimePicker(state = endTimeState)
            }
            Text("Repeats", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { f ->
                    val label = when (f) {
                        RecurrenceFrequency.NONE -> "Once"
                        RecurrenceFrequency.DAILY -> "Daily"
                        RecurrenceFrequency.WEEKLY -> "Weekly"
                        RecurrenceFrequency.MONTHLY -> "Monthly"
                        RecurrenceFrequency.YEARLY -> "Yearly"
                        RecurrenceFrequency.SPECIFIC_DATES -> "Pick dates"
                    }
                    LChip(label, selected = frequency == f, onClick = { frequency = f })
                }
            }
            if (frequency != RecurrenceFrequency.NONE && frequency != RecurrenceFrequency.SPECIFIC_DATES) {
                LField(interval, { interval = it.filter { c -> c.isDigit() }.take(2) }, "Every (interval)")
            }
            if (frequency == RecurrenceFrequency.WEEKLY) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { dow ->
                        LChip(
                            dayLabels[dow - 1],
                            selected = dow in weekDays,
                            onClick = {
                                weekDays = if (dow in weekDays) weekDays - dow else weekDays + dow
                            }
                        )
                    }
                }
            }
            if (frequency == RecurrenceFrequency.SPECIFIC_DATES) {
                LGhostButton("Add date", onClick = { pickExtraDate = true })
                specificDates.sorted().forEach { d ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.format(dateFmt), color = L.Ink)
                        TextButton(onClick = { specificDates = specificDates - d }) {
                            Text("Remove", color = L.Box)
                        }
                    }
                }
            }
            LChip(
                untilDate?.format(dateFmt) ?: "No end date",
                selected = untilDate != null,
                onClick = { showUntilPicker = true }
            )
            if (untilDate != null) {
                LGhostButton("Clear end date", onClick = { untilDate = null })
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        eventDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
    if (showUntilPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showUntilPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        untilDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showUntilPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showUntilPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
    if (pickExtraDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickExtraDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        specificDates = specificDates + Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickExtraDate = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { pickExtraDate = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}
