package com.ledgerai.app.presentation.screens.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.ai.VoiceResultKind
import com.ledgerai.app.data.repository.VoiceHistoryItem
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.navigation.OpenKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val COLLAPSED_ROWS = 5
private val ClockFormat = DateTimeFormatter.ofPattern("h:mm a")
private val DayFormat = DateTimeFormatter.ofPattern("MMM d")

internal fun historyTime(at: LocalDateTime, today: LocalDate = LocalDate.now()): String = when (at.toLocalDate()) {
    today -> at.format(ClockFormat)
    today.minusDays(1) -> "Yesterday"
    else -> at.format(DayFormat)
}

/** Editable list of past captures: transcript, result chip, time. Five rows, then More. */
@Composable
internal fun HistoryGroup(
    history: List<VoiceHistoryItem>?,
    onTap: (VoiceHistoryItem) -> Unit
) {
    var all by rememberSaveable { mutableStateOf(false) }
    when {
        history == null -> LLoading()
        history.isEmpty() -> LEmpty(Icons.Filled.Mic, "No captures")
        else -> {
            val shown = if (all) history else history.take(COLLAPSED_ROWS)
            LGroup {
                shown.forEachIndexed { index, item ->
                    if (index > 0) LGroupDivider()
                    LGroupRow(
                        title = item.transcript,
                        sub = historyTime(item.createdAt),
                        onClick = { onTap(item) },
                        end = { KindPill(item.kind) }
                    )
                }
                if (history.size > COLLAPSED_ROWS) {
                    LGroupDivider()
                    LGroupRow(
                        title = if (all) "Less" else "More",
                        trailing = if (all) null else "${history.size - COLLAPSED_ROWS}",
                        onClick = { all = !all }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(selected: VoiceResultKind?, auto: Boolean, onPick: (VoiceResultKind?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (auto) LChip("Auto", selected = selected == null, onClick = { onPick(null) })
        VoiceResultKind.pickable.forEach { k ->
            LChip(k.label, selected = selected == k, onClick = { onPick(k) })
        }
    }
}

/**
 * Item sheet for one past capture. Save relabels (transcript, result kind), Redo reads the edited words
 * again and shows confirm cards, Delete asks first. [onOpen] is null when no item is linked.
 */
@Composable
internal fun HistoryItemSheet(
    item: VoiceHistoryItem,
    onDismiss: () -> Unit,
    onSave: (transcript: String, kind: VoiceResultKind) -> Unit,
    onDelete: (alsoItem: Boolean) -> Unit,
    onRedo: (transcript: String, kind: VoiceResultKind?, replaceItem: Boolean) -> Unit,
    onVoice: () -> Unit,
    onOpen: ((OpenKind, Long) -> Unit)?
) {
    var transcript by rememberSaveable(item.id) { mutableStateOf(item.transcript) }
    var kind by rememberSaveable(item.id) { mutableStateOf<VoiceResultKind?>(item.kind.takeIf { it != VoiceResultKind.Unsorted }) }
    var withItem by rememberSaveable(item.id) { mutableStateOf(false) }
    val linked = item.linkedItemId != null && item.kind != VoiceResultKind.Unsorted
    val openKind = item.kind.openKind()

    LItemSheet(
        title = historyTime(item.createdAt),
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(transcript, kind ?: VoiceResultKind.Unsorted) },
        primaryEnabled = transcript.isNotBlank(),
        onDelete = { onDelete(withItem && linked) }
    ) {
        LField(transcript, { transcript = it }, "Words", singleLine = false, minLines = 2)
        KindChips(kind, auto = false) { kind = it }
        if (linked) {
            LChip("Also remove saved item", selected = withItem, onClick = { withItem = !withItem })
            if (onOpen != null && openKind != null) {
                LGhostButton("Open", onClick = { onOpen(openKind, item.linkedItemId!!) })
            }
        }
        LGhostButton("Voice", onClick = onVoice)
        LGhostButton(
            "Redo",
            onClick = { onRedo(transcript, kind, withItem && linked) },
            enabled = transcript.isNotBlank()
        )
    }
}

/** Edit the words of a card nothing could be read from, optionally choose what it should become. */
@Composable
internal fun RedoSheet(
    initialTranscript: String,
    initialKind: VoiceResultKind?,
    onDismiss: () -> Unit,
    onApply: (transcript: String, kind: VoiceResultKind?) -> Unit
) {
    var transcript by rememberSaveable { mutableStateOf(initialTranscript) }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Read again",
        onPrimary = { onApply(transcript.trim(), kind) },
        primaryEnabled = transcript.isNotBlank()
    ) {
        LField(transcript, { transcript = it }, "Words", singleLine = false, minLines = 2)
        KindChips(kind, auto = true) { kind = it }
    }
}
