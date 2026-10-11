package com.ledgerai.app.presentation.screens.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.schedule.FreeSlotOption
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LSheet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Proposed study times on a week strip, beside what is already on those days.
 * Nothing is saved until the user adds them. Dismiss closes the sheet and drops the proposals.
 */
@Composable
fun StudyTimesSheet(
    study: StudyState,
    onDismiss: () -> Unit,
    onConfirm: (List<FreeSlotOption>) -> Unit,
    busy: List<AgendaItem> = emptyList(),
) {
    val timeFmt = DateTimeFormatter.ofPattern("h:mm")
    var off by rememberSaveable { mutableStateOf("") }
    val offSet = off.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val chosen = study.slots.filterIndexed { i, _ -> i !in offSet }
    val addLabel = when (chosen.size) {
        1 -> "Add 1 session"
        else -> "Add ${chosen.size} sessions"
    }

    LSheet(
        title = study.topic.ifBlank { "Study" },
        onDismiss = onDismiss,
        primary = if (study.loading || study.error || study.slots.isEmpty()) "Close" else addLabel,
        onPrimary = {
            if (study.loading || study.error || study.slots.isEmpty()) onDismiss() else onConfirm(chosen)
        },
        primaryEnabled = study.loading || study.error || study.slots.isEmpty() || chosen.isNotEmpty()
    ) {
        when {
            study.loading -> LLoading()
            study.error -> LError("Could not find time")
            study.slots.isEmpty() -> LEmpty(Icons.Default.Timer, "No free time")
            else -> {
                val first = study.slots.minOf { it.start.toLocalDate() }
                val last = study.slots.maxOf { it.start.toLocalDate() }
                val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
                val scroll = days.size > 7
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (day in days) {
                        DayColumn(
                            day = day,
                            busy = busy.filter { it.date == day }.sortedBy { it.start },
                            slots = study.slots.mapIndexedNotNull { index, slot ->
                                if (slot.start.toLocalDate() == day) index to slot else null
                            },
                            off = offSet,
                            timeFmt = timeFmt,
                            onToggle = { index, on ->
                                off = (if (on) offSet + index else offSet - index).joinToString(",")
                            },
                            modifier = if (scroll) Modifier.width(48.dp) else Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    busy: List<AgendaItem>,
    slots: List<Pair<Int, FreeSlotOption>>,
    off: Set<Int>,
    timeFmt: DateTimeFormatter,
    onToggle: (index: Int, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = L.InkMuted,
            maxLines = 1
        )
        busy.forEach { item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(L.RadiusSm))
                    .background(L.BoxDeep)
                    .padding(4.dp)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = L.OnBoxMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        slots.forEach { (index, slot) ->
            val on = index !in off
            val label = slot.start.format(timeFmt)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(L.RadiusSm))
                    .background(if (on) L.Accent else L.Box)
                    .border(1.dp, if (on) L.Accent else L.Line, RoundedCornerShape(L.RadiusSm))
                    .clickable { onToggle(index, on) }
                    .padding(4.dp)
                    .semantics { contentDescription = (if (on) "Selected " else "Not selected ") + label },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) L.OnAccent else L.Ink,
                    maxLines = 1
                )
            }
        }
    }
}
