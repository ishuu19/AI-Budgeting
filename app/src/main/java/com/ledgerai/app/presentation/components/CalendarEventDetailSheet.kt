package com.ledgerai.app.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.domain.util.PlaceLinks
import com.ledgerai.app.service.AlarmToneHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val LinkRegex = Regex("https?://\\S+")

private fun Intent.viewUrl(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

/** "Once", "Daily", "Weekdays" or the day names for an alarm repeat mask (Sun=1 ... Sat=64). */
fun alarmRepeatSummary(mask: Int): String = when (mask) {
    0 -> "Once"
    127 -> "Daily"
    62 -> "Weekdays"
    65 -> "Weekends"
    else -> listOf(
        DayOfWeek.MONDAY to 2, DayOfWeek.TUESDAY to 4, DayOfWeek.WEDNESDAY to 8, DayOfWeek.THURSDAY to 16,
        DayOfWeek.FRIDAY to 32, DayOfWeek.SATURDAY to 64, DayOfWeek.SUNDAY to 1
    ).filter { (_, bit) -> mask and bit != 0 }
        .joinToString(" ") { it.first.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

fun habitDaysSummary(mask: Int): String = if (mask == 0) "Every day" else alarmRepeatSummary(mask)

/**
 * Item sheet for a calendar event occurrence: complete (tasks), edit, change date, delete.
 * Alarms also expose on/off, repeat, tone and a preview.
 */
@Composable
fun CalendarEventDetailSheet(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onChangeDate: (LocalDate) -> Unit,
    onToggleDone: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: (RecurrenceDeleteScope) -> Unit
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var scopeSheet by rememberSaveable { mutableStateOf(false) }
    val currentDate = event.instanceDate ?: event.startAt.toLocalDate()
    val context = LocalContext.current
    val dateTimeFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val isTask = event.kind == CalendarEventKind.TASK
    val isAlarm = event.kind == CalendarEventKind.ALARM
    val point = isTask || isAlarm
    val preview = if (isAlarm) rememberTonePreview() else null
    val mapsUrl = event.location.takeIf { it.isNotBlank() }?.let { PlaceLinks.googleMapsSearchUrl(it) }
    val urls = remember(event.links) { LinkRegex.findAll(event.links).map { it.value }.distinct().toList() }

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Text(event.kind.label(), style = MaterialTheme.typography.labelLarge, color = L.Primary)
        Text(
            when {
                !event.hasDate -> "No date"
                event.allDay && isTask -> event.startAt.format(dateFmt)
                event.allDay -> event.startAt.format(dateFmt)
                point -> event.startAt.format(dateTimeFmt)
                else -> "${event.startAt.format(dateTimeFmt)} – ${event.endAt.format(timeFmt)}"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = L.Ink
        )
        if (isAlarm) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${alarmRepeatSummary(event.alarmRepeatDays)} · ${AlarmToneHelper.displayName(event.alarmToneUri)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = L.Ink,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = event.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.semantics {
                        contentDescription = (if (event.isEnabled) "Turn off " else "Turn on ") + event.title
                    }
                )
            }
            if (preview != null) ChipsRow { TonePreviewButton(event.alarmToneUri, preview) }
        } else {
            event.recurrence?.takeIf { it.repeats }?.let { r ->
                val label = when (r.frequency.name) {
                    "DAILY" -> "Daily"
                    "WEEKLY" -> "Weekly"
                    "MONTHLY" -> "Monthly"
                    "YEARLY" -> "Yearly"
                    "SPECIFIC_DATES" -> "Selected dates"
                    else -> null
                }
                label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
            }
        }
        if (event.notes.isNotBlank()) {
            Text(event.notes, style = MaterialTheme.typography.bodyMedium, color = L.Ink)
        }
        if (event.reminders.isNotEmpty()) {
            Text(
                event.reminders.joinToString(" · ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = L.InkMuted
            )
        }
        if (event.location.isNotBlank()) {
            Text(event.location, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            mapsUrl?.let { url ->
                LGhostButton("Open map", onClick = { context.startActivity(Intent().viewUrl(url)) })
            }
        }
        urls.take(3).forEach { url ->
            LGhostButton("Open link", onClick = { runCatching { context.startActivity(Intent().viewUrl(url)) } })
        }
        if (!isAlarm) LGhostButton("Change date", onClick = { showDatePicker = true })
        if (isTask) LGhostButton("Edit", onClick = onEdit)
    }

    val primary = when {
        isTask && event.isCompleted -> "Reopen"
        isTask -> "Complete"
        else -> "Edit"
    }
    val onPrimary = if (isTask) onToggleDone else onEdit

    if (event.isRecurring) {
        LSheet(
            title = event.title,
            onDismiss = onDismiss,
            primary = primary,
            onPrimary = onPrimary,
            secondary = "Delete",
            onSecondary = { scopeSheet = true },
            content = body
        )
        if (scopeSheet) {
            RecurrenceDeleteSheet(
                title = event.title,
                isRecurring = true,
                onDismiss = { scopeSheet = false },
                onThisOnly = { onDelete(RecurrenceDeleteScope.THIS) },
                onThisAndFuture = { onDelete(RecurrenceDeleteScope.THIS_AND_FUTURE) },
                onAll = { onDelete(RecurrenceDeleteScope.ALL) }
            )
        }
    } else {
        LItemSheet(
            title = event.title,
            onDismiss = onDismiss,
            primary = primary,
            onPrimary = onPrimary,
            onDelete = { onDelete(RecurrenceDeleteScope.ALL) },
            content = body
        )
    }
    if (showDatePicker) {
        EventDatePickerDialog(
            initialDate = currentDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { showDatePicker = false; onChangeDate(it) }
        )
    }
}

/** Item sheet for a study block or a habit block saved in the plan. */
@Composable
fun PlanBlockSheet(
    block: PlanBlock,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val done = block.status == PlanBlockStatus.DONE
    LItemSheet(
        title = block.title,
        onDismiss = onDismiss,
        primary = "Start",
        onPrimary = onStart,
        primaryEnabled = !done,
        onDelete = onDelete
    ) {
        Text(
            if (block.kind == PlanBlockKind.STUDY) "Study" else "Habit",
            style = MaterialTheme.typography.labelLarge,
            color = L.Primary
        )
        Text(
            "${block.startAt.format(dayFmt)} – ${block.endAt.format(timeFmt)}",
            style = MaterialTheme.typography.bodyLarge,
            color = L.Ink
        )
        if (block.topic.isNotBlank() && block.topic != block.title) {
            Text(block.topic, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        }
        LGhostButton(if (done) "Reopen" else "Complete", onClick = onToggleDone)
    }
}

/** Item sheet for one session of a habit. */
@Composable
fun HabitSessionSheet(
    habit: Habit,
    date: LocalDate,
    done: Boolean,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a") }
    LItemSheet(
        title = habit.title,
        onDismiss = onDismiss,
        primary = "Start",
        onPrimary = onStart,
        primaryEnabled = !done,
        onDelete = onDelete
    ) {
        Text("Habit", style = MaterialTheme.typography.labelLarge, color = L.Primary)
        Text(
            "${date.atTime(habit.startTime).format(dayFmt)} · ${habit.durationMinutes} min",
            style = MaterialTheme.typography.bodyLarge,
            color = L.Ink
        )
        Text(
            habitDaysSummary(habit.daysMask) + if (habit.nudgeEnabled) " · Nudges" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = L.InkMuted
        )
        if (!done && date == LocalDate.now()) LGhostButton("Complete", onClick = onComplete)
        if (done) Text("Done", style = MaterialTheme.typography.bodyMedium, color = L.Primary)
        LGhostButton("Edit", onClick = onEdit)
    }
}
