package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.util.PlaceLinks
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventDetailSheet(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onChangeDate: ((LocalDate) -> Unit)? = null,
    onRemove: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val currentDate = event.instanceDate ?: event.startAt.toLocalDate()
    val context = LocalContext.current
    val timeFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm") }
    val kindLabel = when (event.kind) {
        CalendarEventKind.TASK -> "Task"
        CalendarEventKind.EXAM -> "Exam"
        CalendarEventKind.CLASS -> "Class"
        CalendarEventKind.PERSONAL -> "Event"
    }
    val mapsUrl = event.location.takeIf { it.isNotBlank() }?.let { PlaceLinks.googleMapsSearchUrl(it) }

    LSheet(
        title = event.title,
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = onDismiss,
        secondary = when (event.kind) {
            CalendarEventKind.TASK -> "Delete task"
            else -> "Remove"
        },
        onSecondary = onRemove
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(kindLabel, style = MaterialTheme.typography.labelMedium, color = L.Gold)
            Text(
                "${event.startAt.format(timeFmt)} – ${event.endAt.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                style = MaterialTheme.typography.bodyMedium,
                color = L.Ink
            )
            event.recurrence?.takeIf { it.repeats }?.let { r ->
                val repeatLabel = when (r.frequency.name) {
                    "DAILY" -> "Repeats daily"
                    "WEEKLY" -> "Repeats weekly"
                    "MONTHLY" -> "Repeats monthly"
                    "YEARLY" -> "Repeats yearly"
                    "SPECIFIC_DATES" -> "Repeats on selected dates"
                    else -> null
                }
                repeatLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Gold) }
            }
            if (onChangeDate != null) {
                LGhostButton("Change date", onClick = { showDatePicker = true })
            }
            if (onEdit != null && event.kind != CalendarEventKind.TASK) {
                LGhostButton("Edit event / recurrence", onClick = onEdit)
            }
            if (event.location.isNotBlank()) {
                Text(event.location, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
                mapsUrl?.let { url ->
                    LGhostButton("Open in Google Maps", onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                        )
                    })
                }
            }
        }
    }
    if (showDatePicker && onChangeDate != null) {
        EventDatePickerDialog(
            initialDate = currentDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { onChangeDate(it) }
        )
    }
}
