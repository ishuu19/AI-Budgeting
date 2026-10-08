package com.ledgerai.app.presentation.screens.routines

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.RoutineSlotReminder
import com.ledgerai.app.domain.model.ScheduleSlot
import com.ledgerai.app.presentation.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val slotTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineTimetableSheet(
    routineId: Long,
    routineTitle: String,
    slots: List<ScheduleSlot>,
    importMessage: String?,
    onDismiss: () -> Unit,
    onImportText: suspend (String, Boolean) -> Result<Int>,
    onImportCsv: suspend (android.net.Uri, Boolean) -> Result<Int>,
    onImportImage: suspend (android.net.Uri, Boolean) -> Result<Int>,
    onLoadSlot: suspend (Long) -> ScheduleSlot?,
    onSaveSlot: (Long, String, List<RoutineSlotReminder>, Int) -> Unit
) {
    var showImport by remember { mutableStateOf(false) }
    var editSlot by remember { mutableStateOf<ScheduleSlot?>(null) }
    var pendingSlotId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pendingSlotId) {
        val id = pendingSlotId ?: return@LaunchedEffect
        pendingSlotId = null
        editSlot = onLoadSlot(id)
    }

    if (showImport) {
        ScheduleImportSheet(
            title = "Import · $routineTitle",
            onDismiss = { showImport = false },
            onImportText = onImportText,
            onImportCsv = onImportCsv,
            onImportImage = onImportImage
        )
    }

    editSlot?.let { slot ->
        ScheduleSlotEditSheet(
            slot = slot,
            occurrenceDate = LocalDate.now(),
            onDismiss = { editSlot = null },
            onSave = { loc, reminders, dow ->
                onSaveSlot(slot.id, loc, reminders, dow)
                editSlot = null
            },
            onMoveOccurrenceDate = { /* calendar screen handles moves */ }
        )
    }

    LSheet(
        title = "Timetable · $routineTitle",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = onDismiss
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LButton("Import schedule", onClick = { showImport = true })
            importMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = L.Gold)
            }
            if (slots.isEmpty()) {
                Text(
                    "No blocks yet. Import a photo, CSV, or pasted list — they appear on Calendar too.",
                    color = L.InkMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                slots.forEach { slot ->
                    LCard(Modifier.fillMaxWidth(), onClick = { pendingSlotId = slot.id }) {
                        Text(slot.title, style = MaterialTheme.typography.titleSmall, color = L.OnBox)
                        val day = dayNames.getOrElse(slot.dayOfWeek - 1) { "?" }
                        Text(
                            "$day ${slot.startTime.format(slotTimeFmt)}–${slot.endTime.format(slotTimeFmt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = L.OnBoxMuted
                        )
                        if (slot.location.isNotBlank()) {
                            Text(slot.location, style = MaterialTheme.typography.bodySmall, color = L.Gold)
                        }
                        Text(
                            "${slot.reminderCount} reminders · tap to edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = L.OnBoxMuted
                        )
                    }
                }
            }
        }
    }
}
