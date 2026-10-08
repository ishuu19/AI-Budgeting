package com.ledgerai.app.presentation.screens.plan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.schedule.FreeSlotOption
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LSheet
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter

/**
 * Proposed study times. Nothing is saved until Confirm. Dismiss closes the sheet and drops the proposals.
 */
@Composable
fun StudyTimesSheet(
    study: StudyState,
    onDismiss: () -> Unit,
    onConfirm: (List<FreeSlotOption>) -> Unit
) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEE MMM d · h:mm a") }
    val endFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    // Indexes the user switched off; everything starts selected.
    var off by rememberSaveable { mutableStateOf("") }
    val offSet = off.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val chosen = study.slots.filterIndexed { i, _ -> i !in offSet }

    LSheet(
        title = study.topic.ifBlank { "Study" },
        onDismiss = onDismiss,
        primary = if (study.loading || study.error || study.slots.isEmpty()) "Close" else "Confirm (${chosen.size})",
        onPrimary = {
            if (study.loading || study.error || study.slots.isEmpty()) onDismiss() else onConfirm(chosen)
        },
        primaryEnabled = study.loading || study.error || study.slots.isEmpty() || chosen.isNotEmpty()
    ) {
        when {
            study.loading -> LLoading()
            study.error -> LError("Could not find time")
            study.slots.isEmpty() -> LEmpty(Icons.Default.Timer, "No free time")
            else -> LGroup {
                study.slots.forEachIndexed { i, slot ->
                    if (i > 0) LGroupDivider()
                    val on = i !in offSet
                    LGroupRow(
                        title = "${slot.start.format(fmt)} – ${slot.end.format(endFmt)}",
                        sub = slot.reason,
                        onClick = {
                            off = (if (on) offSet + i else offSet - i).joinToString(",")
                        },
                        end = {
                            Icon(
                                if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = (if (on) "Selected " else "Not selected ") + slot.start.format(fmt),
                                tint = if (on) L.Gold else L.OnBoxMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
