package com.ledgerai.app.presentation.screens.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import com.ledgerai.app.data.chat.ChatMessageEntity
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.domain.notebook.NotebookCommand
import com.ledgerai.app.domain.notebook.NotebookEntry
import com.ledgerai.app.domain.notebook.NotebookJson
import com.ledgerai.app.domain.notebook.NotebookPrompts
import com.ledgerai.app.domain.notebook.NotebookVoice
import com.ledgerai.app.domain.notebook.NotebookWidth
import com.ledgerai.app.domain.notebook.ScreenBlock
import com.ledgerai.app.domain.notebook.ScreenBlocks
import com.ledgerai.app.presentation.components.L

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NotebookPanel(
    blocks: List<ScreenBlock>,
    source: String?,
    cantRead: Boolean,
    showSetup: Boolean,
    fromOcr: Boolean,
    clips: List<ClipEntity>,
    prompts: List<String>,
    messages: List<ChatMessageEntity>,
    status: String?,
    needsConfirm: Boolean,
    online: Boolean,
    utterance: String,
    onUtteranceConsumed: () -> Unit,
    onSave: (String, String, String?, String?) -> Unit,
    onAsk: (String, String, String, String?, String) -> Unit,
    onMic: () -> Unit,
    onLog: () -> Unit,
    onOpenBoard: () -> Unit,
    onUndo: () -> Unit,
    onPrompts: (List<String>) -> Unit,
    onConfirm: (ChatMessageEntity, Int) -> Unit,
    onRetry: (ChatMessageEntity) -> Unit,
    onPinAnswer: (ChatMessageEntity) -> Unit,
    onSaveAnswer: (ChatMessageEntity) -> Unit,
    onCopy: (String) -> Unit,
    onRead: (String) -> Unit,
    onPinLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var own by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var wordMode by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf(listOf<String>()) }
    var expanded by remember { mutableStateOf(false) }
    var whole by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draftPrompt by remember { mutableStateOf("") }

    val picks = buildList {
        blocks.forEachIndexed { index, block ->
            if (index == wordMode) return@forEachIndexed
            if (index in selected) add(block.text)
        }
        addAll(words)
    }
    val selection = if (whole) blocks.joinToString("\n") { it.text } else picks.joinToString("\n")
    val body = NotebookEntry.body(if (whole) blocks.map { it.text } else picks, own)
    val kind = NotebookEntry.sourceKind(picks.isNotEmpty() || whole, fromOcr, fromVoice = false, fromClipboard = false)

    fun runSave(extra: String, voice: Boolean) {
        val text = NotebookEntry.body(if (whole) blocks.map { it.text } else picks, listOf(own, extra).filter { it.isNotBlank() }.joinToString(" "))
        val sourceKind = if (voice && picks.isEmpty() && !whole) "voice" else kind
        onSave(text, sourceKind, prompt, source)
    }

    LaunchedEffect(utterance) {
        if (utterance.isBlank()) return@LaunchedEffect
        when (val command = NotebookVoice.parse(utterance)) {
            is NotebookCommand.Save -> runSave(command.rest, voice = true)
            is NotebookCommand.Prompt -> {
                prompt = command.name.takeIf { it != "Ask" }
                val note = listOf(own, command.rest).filter { it.isNotBlank() }.joinToString(" ")
                val text = NotebookEntry.body(if (whole) blocks.map { it.text } else picks, note)
                onAsk(text, selection, note, prompt ?: command.rest, if (command.rest.isNotBlank() && picks.isEmpty()) "voice" else kind)
            }
            NotebookCommand.Pin -> onPinLast()
            NotebookCommand.Read -> onRead(NotebookVoice.speak(selection, messages.lastOrNull { it.role == "assistant" }?.text))
            NotebookCommand.SelectAll -> selected = blocks.indices.toSet()
            is NotebookCommand.Text -> own = command.text
        }
        onUtteranceConsumed()
    }

    val width = NotebookEntry.widthOf(LocalConfiguration.current.screenWidthDp)
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showScreen = blocks.isNotEmpty() && !(width == NotebookWidth.Compact && imeOpen)
    val shape = RoundedCornerShape(if (width == NotebookWidth.Compact) 20.dp else 16.dp)

    Box(modifier.fillMaxSize().imePadding()) {
        val sheet = when (width) {
            NotebookWidth.Compact -> Modifier.fillMaxWidth().fillMaxHeight(if (expanded) 1f else 0.6f).align(Alignment.BottomCenter)
            NotebookWidth.Medium -> Modifier.widthIn(max = 640.dp).fillMaxWidth(0.92f).fillMaxHeight().align(Alignment.Center)
            NotebookWidth.Expanded -> Modifier.fillMaxSize().padding(L.Space.x16)
        }
        Column(
            sheet.clip(shape).background(L.Page).padding(L.Space.x16),
            verticalArrangement = Arrangement.spacedBy(L.Space.x12),
        ) {
            Header(source, expanded, onToggle = { expanded = !expanded }, onLog = onLog)
            if (showSetup) SetupCard()
            if (width == NotebookWidth.Expanded) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(L.Space.x16)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ScreenCard(blocks, selected, wordMode, words, showScreen, cantRead, expandedBlocks = true, onToggle = { index ->
                            selected = if (index in selected) selected - index else selected + index
                        }, onHold = { wordMode = it; words = emptyList() }, onWord = { word ->
                            words = if (word in words) words - word else words + word
                        }, onSelectAll = { selected = blocks.indices.toSet() })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(L.Space.x12)) {
                        ThreadList(messages, online, onConfirm, onRetry, onPinAnswer, onSaveAnswer, onCopy, Modifier.weight(1f))
                        ComposeBox(picks, own, prompt, prompts, whole, blocks.isNotEmpty(), listening = false, onOwn = { own = it }, onPrompt = { prompt = it }, onWhole = { whole = it }, onMic = onMic, onEditPrompt = { editingPrompt = it }, onAdd = { adding = true }, onRemovePick = { picks.indexOf(it).let { i -> if (i >= 0) { /* chips are derived */ } } })
                        Actions(status, needsConfirm, onUndo, onSave = { runSave("", false) }, onAsk = {
                            onAsk(body, selection, own, prompt, kind)
                        })
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(L.Space.x12)) {
                    ScreenCard(blocks, selected, wordMode, words, showScreen, cantRead, expanded, onToggle = { index ->
                        selected = if (index in selected) selected - index else selected + index
                    }, onHold = { wordMode = it; words = emptyList() }, onWord = { word ->
                        words = if (word in words) words - word else words + word
                    }, onSelectAll = { selected = blocks.indices.toSet() })
                    ChipRow(picks) { label ->
                        val index = blocks.indexOfFirst { it.text == label }
                        if (index >= 0) selected = selected - index else words = words - label
                    }
                    ComposeBox(picks, own, prompt, prompts, whole, blocks.isNotEmpty(), false, { own = it }, { prompt = it }, { whole = it }, onMic, { editingPrompt = it }, { adding = true }, {})
                    BoardStrip(clips, onInsert = { own = if (own.isBlank()) it else "$own $it" }, onOpenBoard)
                    ThreadList(messages, online, onConfirm, onRetry, onPinAnswer, onSaveAnswer, onCopy, Modifier.heightIn(max = 220.dp))
                    Actions(status, needsConfirm, onUndo, { runSave("", false) }, { onAsk(body, selection, own, prompt, kind) })
                }
            }
        }
    }

    if (editingPrompt != null || adding) {
        AlertDialog(
            onDismissRequest = { editingPrompt = null; adding = false },
            title = { Text(if (adding) "New prompt" else "Edit prompt") },
            text = {
                BasicTextField(
                    value = draftPrompt.ifEmpty { editingPrompt.orEmpty() },
                    onValueChange = { draftPrompt = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    cursorBrush = SolidColor(L.Accent),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = draftPrompt.ifBlank { editingPrompt.orEmpty() }.trim()
                    if (next.isNotEmpty()) {
                        val list = prompts.toMutableList()
                        val old = editingPrompt
                        if (old == null) list += next else list[list.indexOf(old)] = next
                        onPrompts(list)
                    }
                    draftPrompt = ""
                    editingPrompt = null
                    adding = false
                }) { Text("Save") }
            },
            dismissButton = {
                if (editingPrompt != null) TextButton(onClick = {
                    onPrompts(prompts.filter { it != editingPrompt })
                    editingPrompt = null
                }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun Header(source: String?, full: Boolean, onToggle: () -> Unit, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (full) "▾" else "━━", color = L.InkMuted, modifier = Modifier.clickable(onClick = onToggle).padding(8.dp))
        TextButton(onClick = onLog) { Text("Log it") }
        Text(source ?: "", color = L.InkMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SetupCard() {
    Column(Modifier.fillMaxWidth().clip(L.Radius.let { RoundedCornerShape(16.dp) }).background(L.Box).padding(L.Space.x12)) {
        Text("Set LedgerAI as the assistant", style = MaterialTheme.typography.titleSmall, color = L.Ink)
        Text("Settings → Apps → Default apps → Digital assistant. Turn on Use text from screen and Use screenshot.", color = L.InkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ScreenCard(
    blocks: List<ScreenBlock>,
    selected: Set<Int>,
    wordMode: Int?,
    words: List<String>,
    visible: Boolean,
    cantRead: Boolean,
    expandedBlocks: Boolean,
    onToggle: (Int) -> Unit,
    onHold: (Int) -> Unit,
    onWord: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    if (!visible && cantRead) {
        Text(NotebookEntry.CANT_READ, color = L.Ink, style = MaterialTheme.typography.titleMedium)
        return
    }
    if (!visible) return
    val shown = ScreenBlocks.visible(blocks, expandedBlocks)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(L.Box).padding(L.Space.x12), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Screen", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            Text("Select all", color = L.Accent, modifier = Modifier.clickable(onClick = onSelectAll))
        }
        shown.forEachIndexed { index, block ->
            val on = index in selected
            Column(
                Modifier.fillMaxWidth().heightIn(min = L.Target).clip(RoundedCornerShape(10.dp))
                    .background(if (on) L.GoldSoft else L.Page)
                    .combinedClickable(onClick = { onToggle(index) }, onLongClick = { onHold(index) })
                    .padding(L.Space.x8)
            ) {
                Text(block.text, color = L.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (wordMode == index) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                        ScreenBlocks.words(block.text).forEach { word ->
                            val picked = word in words
                            Text(
                                word,
                                color = if (picked) L.OnAccent else L.Ink,
                                modifier = Modifier.clip(L.RadiusPill).background(if (picked) L.Accent else L.Page).clickable { onWord(word) }.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(picks: List<String>, onRemove: (String) -> Unit) {
    if (picks.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(L.Space.x8), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        picks.forEach { pick ->
            Text(
                pick.take(28) + " ✕",
                color = L.Ink,
                modifier = Modifier.clip(L.RadiusPill).border(1.dp, L.Line, L.RadiusPill).clickable { onRemove(pick) }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposeBox(
    picks: List<String>,
    own: String,
    prompt: String?,
    prompts: List<String>,
    whole: Boolean,
    canWhole: Boolean,
    listening: Boolean,
    onOwn: (String) -> Unit,
    onPrompt: (String?) -> Unit,
    onWhole: (Boolean) -> Unit,
    onMic: () -> Unit,
    onEditPrompt: (String) -> Unit,
    onAdd: () -> Unit,
    onRemovePick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = L.Target).clip(RoundedCornerShape(12.dp)).border(1.dp, L.Line, RoundedCornerShape(12.dp)).padding(L.Space.x12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!prompt.isNullOrBlank()) Text(NotebookPrompts.prefix(prompt), color = L.InkMuted)
            BasicTextField(
                value = own,
                onValueChange = onOwn,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                cursorBrush = SolidColor(L.Accent),
                maxLines = 5,
                decorationBox = { inner ->
                    if (own.isBlank()) Text("Add a note or ask…", color = L.InkMuted)
                    inner()
                },
            )
            Text(if (listening) "●" else "🎤", color = L.Highlight, modifier = Modifier.clickable(onClick = onMic).padding(8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(L.Space.x8), verticalAlignment = Alignment.CenterVertically) {
            if (canWhole) {
                Text(
                    NotebookEntry.WHOLE,
                    color = if (whole) L.OnHighlight else L.Ink,
                    modifier = Modifier.clip(L.RadiusPill).background(if (whole) L.Highlight else L.Box).clickable { onWhole(!whole) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            prompts.forEach { name ->
                val on = prompt == name
                Text(
                    name,
                    color = if (on) L.OnHighlight else L.Ink,
                    modifier = Modifier.heightIn(min = L.Target).clip(L.RadiusPill).background(if (on) L.Highlight else L.Box)
                        .combinedClickable(onClick = { onPrompt(if (on) null else name) }, onLongClick = { onEditPrompt(name) })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text("+", color = L.Ink, modifier = Modifier.clickable(onClick = onAdd).padding(12.dp))
        }
    }
}

@Composable
private fun BoardStrip(clips: List<ClipEntity>, onInsert: (String) -> Unit, onOpen: () -> Unit) {
    val live = clips.filter { it.deletedAt == null }
    val pinned = live.filter { it.pinned }.sortedBy { it.pinOrder ?: Int.MAX_VALUE }
    val recent = live.filter { !it.pinned }.sortedByDescending { it.capturedAt }
    val strip = (pinned + recent).take(8)
    if (strip.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Text("Pinned", color = L.InkMuted, style = MaterialTheme.typography.bodySmall)
        strip.forEach { clip ->
            val label = NotebookEntry.shown(clip.text, clip.editedText).lineSequence().first().take(18)
            Text(label, color = L.Ink, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(L.Box).clickable { onInsert(NotebookEntry.shown(clip.text, clip.editedText)) }.padding(L.Space.x8))
        }
        Text("›", color = L.Accent, modifier = Modifier.clickable(onClick = onOpen).padding(8.dp))
    }
}

@Composable
private fun ThreadList(
    messages: List<ChatMessageEntity>,
    online: Boolean,
    onConfirm: (ChatMessageEntity, Int) -> Unit,
    onRetry: (ChatMessageEntity) -> Unit,
    onPin: (ChatMessageEntity) -> Unit,
    onSave: (ChatMessageEntity) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier,
) {
    if (messages.isEmpty()) return
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        messages.forEach { message ->
            val waiting = NotebookEntry.status(message.state, online)
            Text(message.text, color = if (message.role == "assistant") L.Ink else L.InkMuted, maxLines = 6, overflow = TextOverflow.Ellipsis)
            if (waiting != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(waiting, color = L.Highlight, style = MaterialTheme.typography.bodySmall)
                    if (message.state == "failed") TextButton(onClick = { onRetry(message) }) { Text("Retry") }
                }
            }
            if (message.role == "assistant") {
                Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    TextButton(onClick = { onPin(message) }) { Text("Pin") }
                    TextButton(onClick = { onCopy(message.text) }) { Text("Copy") }
                    TextButton(onClick = { onSave(message) }) { Text("Save") }
                }
                NotebookJson.actionsFrom(message.actionsJson).forEachIndexed { index, action ->
                    TextButton(onClick = { onConfirm(message, index) }) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
private fun Actions(
    status: String?,
    needsConfirm: Boolean,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    onAsk: () -> Unit,
) {
    if (status != null) {
        Text(status, color = L.Highlight, modifier = Modifier.clickable(onClick = onUndo))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(L.Space.x12)) {
        Text(
            if (needsConfirm) "Save anyway" else "Save",
            color = L.Ink,
            modifier = Modifier.weight(1f).heightIn(min = L.Target).clip(RoundedCornerShape(12.dp)).border(1.dp, L.Line, RoundedCornerShape(12.dp)).clickable(onClick = onSave).padding(vertical = 12.dp),
        )
        Text(
            "Ask AI",
            color = L.OnHighlight,
            modifier = Modifier.weight(1f).heightIn(min = L.Target).clip(RoundedCornerShape(12.dp)).background(L.Highlight).clickable(onClick = onAsk).padding(vertical = 12.dp),
        )
    }
}
