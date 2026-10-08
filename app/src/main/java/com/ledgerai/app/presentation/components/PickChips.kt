package com.ledgerai.app.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEventKind
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Chip that shows a clock time and opens a time picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickChip(
    time: LocalTime,
    onTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    label: String? = null
) {
    var open by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    LChip(label ?: time.format(fmt), selected, onClick = { open = true }, modifier = modifier)
    if (open) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = L.Page,
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(
                        state = state,
                        colors = TimePickerDefaults.colors(
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
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("Done", color = L.Box) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel", color = L.InkMuted) }
            }
        )
    }
}

fun CalendarEventKind.label(): String = when (this) {
    CalendarEventKind.EVENT, CalendarEventKind.PERSONAL -> "Event"
    CalendarEventKind.TASK -> "Task"
    CalendarEventKind.EXAM -> "Exam"
    CalendarEventKind.CLASS -> "Class"
    CalendarEventKind.ROUTINE -> "Routine"
    CalendarEventKind.ALARM -> "Alarm"
}

/** Horizontally scrolling row for chip groups. */
@Composable
fun ChipsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}
