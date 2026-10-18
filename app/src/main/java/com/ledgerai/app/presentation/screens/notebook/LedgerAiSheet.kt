package com.ledgerai.app.presentation.screens.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.chat.ChatMessageEntity
import com.ledgerai.app.domain.notebook.NotebookEntry
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LToolButton

/** One tap asks about the selection with this instruction. */
private val QuickAsks = listOf("Summarize", "Explain", "Translate", "Reply")

/**
 * "Ledger AI" in the text-selection menu. The selection sits on top, then a place to say what you want,
 * and two buttons: Ask AI and Save. The answer and any follow-ups stay in this same sheet.
 *
 * - Tap a quick chip to ask straight away, or type your own question.
 * - Add screen attaches a picture of the screen (its words go to the AI, the picture stays on the phone).
 * - Save keeps the selection, your note and the picture in the Notebook, without asking the AI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerAiSheet(
    selected: String,
    source: String?,
    screenThumb: Bitmap?,
    messages: List<ChatMessageEntity>,
    online: Boolean,
    status: String?,
    needsConfirm: Boolean,
    utterance: String,
    onUtteranceConsumed: () -> Unit,
    onAsk: (prompt: String?, text: String) -> Unit,
    onSave: (note: String) -> Unit,
    onAddScreen: () -> Unit,
    onRemoveScreen: () -> Unit,
    onMic: () -> Unit,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRetry: (ChatMessageEntity) -> Unit,
    onCopy: (String) -> Unit,
    onSaveAnswer: (ChatMessageEntity) -> Unit,
    onPinAnswer: (ChatMessageEntity) -> Unit,
) {
    var own by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val chatting = messages.isNotEmpty()
    val canAsk = own.isNotBlank() || !chatting

    LaunchedEffect(utterance) {
        if (utterance.isBlank()) return@LaunchedEffect
        own = listOf(own, utterance).filter { it.isNotBlank() }.joinToString(" ")
        onUtteranceConsumed()
    }
    LaunchedEffect(Unit) { if (!chatting) runCatching { focus.requestFocus() } }
    LaunchedEffect(messages.size, messages.lastOrNull()?.state) { scroll.animateScrollTo(scroll.maxValue) }

    fun ask(prompt: String?) {
        onAsk(prompt, own.trim())
        own = ""
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.88f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(L.Page)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = L.Gutter)
                .padding(top = L.Space.x12, bottom = L.Space.x12),
            verticalArrangement = Arrangement.spacedBy(L.Space.x12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ledger AI", style = MaterialTheme.typography.titleLarge, color = L.Ink)
                    if (!source.isNullOrBlank()) {
                        Text("from $source", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                    }
                }
                LToolButton(Icons.Filled.Close, "Close", onClose)
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(L.Space.x12),
            ) {
                SelectionCard(selected, showAll || !chatting, onToggle = { showAll = !showAll }, collapsible = chatting)
                messages.forEach { message ->
                    if (message.role == "assistant") {
                        AnswerBubble(message, onCopy, onSaveAnswer, onPinAnswer)
                    } else {
                        AskedBubble(message)
                        val line = NotebookEntry.status(message.state, online)
                        if (line != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(line, color = L.Highlight, style = MaterialTheme.typography.bodySmall)
                                if (message.state == "failed") TextButton(onClick = { onRetry(message) }) { Text("Retry") }
                            }
                        } else if (message.state == "queued" && message == messages.last()) {
                            Text("Thinking…", color = L.InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (!chatting) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(L.Space.x8), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    QuickAsks.forEach { name ->
                        Text(
                            name,
                            color = L.Ink,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .heightIn(min = L.Target)
                                .clip(L.RadiusPill)
                                .background(L.Box)
                                .clickable { ask(name) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (screenThumb != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(L.Space.x12)) {
                    Image(
                        bitmap = screenThumb.asImageBitmap(),
                        contentDescription = "Screen picture added",
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Text("Screen added", color = L.Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRemoveScreen) { Text("Remove", color = L.Danger) }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, L.Line, RoundedCornerShape(16.dp))
                    .padding(start = L.Space.x12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = own,
                    onValueChange = { own = it },
                    modifier = Modifier.weight(1f).padding(vertical = L.Space.x12).focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    cursorBrush = SolidColor(L.Accent),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (own.isEmpty()) {
                            Text(
                                if (chatting) "Ask a follow-up…" else "Ask about this, or add a note…",
                                color = L.InkMuted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    },
                )
                if (screenThumb == null) LToolButton(Icons.Filled.PhotoCamera, "Add screen", onAddScreen)
                LToolButton(Icons.Filled.Mic, "Speak", onMic)
            }

            if (status != null) {
                Text(
                    if (needsConfirm) "This looks sensitive. Tap Save again to keep it on this phone." else status,
                    color = L.Highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onUndo),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x12), verticalAlignment = Alignment.CenterVertically) {
                LGhostButton("Save", { onSave(own.trim()) }, Modifier.weight(1f))
                LButton("Ask AI", { ask(null) }, Modifier.weight(1f), enabled = canAsk)
            }
        }
    }
}

@Composable
private fun SelectionCard(text: String, expanded: Boolean, onToggle: () -> Unit, collapsible: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(L.Box)
            .padding(L.Space.x12),
        verticalArrangement = Arrangement.spacedBy(L.Space.x8),
    ) {
        Text(
            text,
            color = L.OnBox,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) 12 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsible && text.length > 120) {
            Text(
                if (expanded) "Show less" else "Show all",
                color = L.Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onToggle),
            )
        }
    }
}

/** What you asked, without the selection that rides along with it. */
@Composable
private fun AskedBubble(message: ChatMessageEntity) {
    val text = askedText(message.text)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = L.OnPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(L.Primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AnswerBubble(
    message: ChatMessageEntity,
    onCopy: (String) -> Unit,
    onSave: (ChatMessageEntity) -> Unit,
    onPin: (ChatMessageEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Text(
            message.text,
            color = L.OnBox,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .background(L.Box)
                .border(1.dp, L.Line, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
            TextButton(onClick = { onCopy(message.text) }) { Text("Copy") }
            TextButton(onClick = { onSave(message) }) { Text("Save answer") }
            TextButton(onClick = { onPin(message) }) { Text("Pin") }
        }
    }
}

/** The stored question is "Prompt / Selected / Note / history". Show only the prompt and the note. */
internal fun askedText(payload: String): String {
    val prompt = payload.lineSequence().firstOrNull { it.startsWith("Prompt:") }?.removePrefix("Prompt:")?.trim().orEmpty()
    val afterNote = payload.substringAfter("Note:\n", "")
    val note = afterNote.lineSequence()
        .takeWhile { !it.startsWith("user: ") && !it.startsWith("assistant: ") }
        .joinToString("\n")
        .trim()
    val shown = listOf(prompt, note).filter { it.isNotBlank() }.joinToString(" · ")
    return shown.ifBlank { if (payload.contains("Selected:")) "About the selection" else payload.take(160) }
}
