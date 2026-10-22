package com.ledgerai.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A row on the thread: time on the left, a thin vertical line with a dot, then the text.
 * [now] draws the dot as an accent ring. Used by Achievements and Trips (docs/life-os/screens.md section 1).
 */
@Composable
fun LThreadRow(
    time: String,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    now: Boolean = false,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    val line = L.Line
    val ink = if (now) L.Accent else L.InkMuted
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = L.InkMuted,
            modifier = Modifier.width(52.dp).padding(top = 14.dp),
            maxLines = 1,
        )
        Column(
            Modifier
                .width(24.dp)
                .heightIn(min = 64.dp)
                .drawBehind {
                    val x = size.width / 2f
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    val c = Offset(x, 22.dp.toPx())
                    if (now) drawCircle(ink, 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                    else drawCircle(ink, 3.5.dp.toPx(), c)
                }
        ) {}
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (muted) L.InkMuted else L.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        end?.invoke()
    }
}

/** Header action: the one add control on a list screen. */
@Composable
fun LAddAction(onClick: () -> Unit, label: String = "Add") {
    LToolButton(Icons.Filled.Add, label, onClick)
}

/** A form row showing a date. Tap opens the date picker. */
@Composable
fun LDateField(
    label: String,
    date: LocalDate,
    onChange: (LocalDate) -> Unit,
    pattern: String = "EEE d MMM yyyy",
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.heightIn(min = L.Target), verticalArrangement = Arrangement.Center) {
        Text(label.lowercase(), style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        Text(
            DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date),
            style = MaterialTheme.typography.bodyLarge,
            color = L.Ink,
        )
    }
    if (open) EventDatePickerDialog(date, onDismiss = { open = false }, onConfirm = onChange)
}
