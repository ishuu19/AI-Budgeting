package com.ledgerai.app.presentation.screens.notebook

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.data.chat.ChatThreadEntity
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.domain.notebook.NotebookEntry
import com.ledgerai.app.domain.notebook.NotebookWidth
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.screens.clips.AddToNotesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotebookScreen(
    onBack: (() -> Unit)? = null,
    open: com.ledgerai.app.presentation.navigation.OpenItem? = null,
    onOpened: () -> Unit = {},
    query: String = "",
    viewModel: NotebookViewModel = hiltViewModel(),
) {
    var capture by remember { mutableStateOf(false) }
    if (capture) {
        AddToNotesScreen(onBack = { capture = false })
        return
    }
    val clips by viewModel.clipsFlow.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val status by viewModel.status.collectAsState()
    val keep by viewModel.chatKeep.collectAsState()
    // Opening a saved note from elsewhere lands on the Notes tab, where the classic notes live.
    var tab by remember { mutableStateOf(if (open != null) "Notes" else "Board") }
    LaunchedEffect(open?.nonce) { if (open != null) tab = "Notes" }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var openId by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<ClipEntity?>(null) }
    var askKeep by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val width = NotebookEntry.widthOf(LocalConfiguration.current.screenWidthDp)
    val cols = NotebookEntry.columns(width)

    val open = clips.firstOrNull { it.id == openId }
    if (open != null) {
        val screens by viewModel.screens(open.id).collectAsState(emptyList())
        EntryEditor(open, screens.map { viewModel.thumb(it.id) to viewModel.full(it.id) }, onBack = { openId = null }, onChange = { viewModel.edit(open.id, it) }, onPin = { viewModel.pin(open.id, !open.pinned) }, onCopy = {
            copy(context, NotebookEntry.shown(open.text, open.editedText))
        }, onAsk = {
            viewModel.ask(open.text, open.text, "", null, open.sourceKind, open.sourceApp, open.windowTitle, false, open.threadId)
        })
        return
    }

    if (tab == "Notes") {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = L.Gutter).padding(top = L.Space.x8),
                horizontalArrangement = Arrangement.spacedBy(L.Space.x8),
            ) {
                listOf("Board", "Chats", "Notes").forEach { name -> LChip(name, tab == name, { tab = name }) }
            }
            Box(Modifier.weight(1f)) {
                com.ledgerai.app.presentation.screens.notes.NotesScreen(open = open, onOpened = onOpened, query = query)
            }
        }
        return
    }

    LScreen(
        title = "Notebook",
        onBack = onBack,
        action = {
            IconButton(onClick = { search = if (search.isEmpty()) " " else "" }) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = L.Ink)
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = L.Ink)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Capture") }, onClick = { menuOpen = false; capture = true })
            }
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                LChip("Board", tab == "Board", { tab = "Board" })
                LChip("Chats", tab == "Chats", { tab = "Chats" })
                LChip("Notes", false, { tab = "Notes" })
                if (tab == "Board") TextButton(onClick = { editing = !editing; picked = emptySet() }) { Text(if (editing) "Done" else "Edit") }
            }
        }
        if (search.isNotEmpty()) {
            item {
                BasicTextField(
                    value = search.trim(),
                    onValueChange = { search = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = L.Target),
                )
            }
        }
        if (status != null) {
            item { Text(status!!, color = L.Highlight, modifier = Modifier.combinedClickable(onClick = viewModel::undoLast, onLongClick = {})) }
        }
        if (tab == "Board") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    listOf("All", "Screen", "Typed", "Voice", "AI").forEach { name ->
                        LChip(name, filter == name, { filter = name })
                    }
                }
            }
            val live = clips.filter { it.deletedAt == null && matches(it, filter, search.trim()) }
            val pinned = live.filter { it.pinned }.sortedBy { it.pinOrder ?: Int.MAX_VALUE }
            val recent = live.filter { !it.pinned }.sortedByDescending { it.capturedAt }
            if (editing && picked.isNotEmpty()) {
                item {
                    Row {
                        TextButton(onClick = { picked.forEach { viewModel.pin(it, true) }; picked = emptySet() }) { Text("Pin") }
                        TextButton(onClick = { viewModel.combine(picked.toList()); picked = emptySet() }) { Text("Combine") }
                        TextButton(onClick = { picked.forEach { viewModel.deleteClip(it) }; picked = emptySet() }) { Text("Delete") }
                    }
                }
            }
            item { Text("Pinned", style = MaterialTheme.typography.titleSmall, color = L.Ink) }
            pinned.chunked(cols).forEach { row ->
                item {
                    TileRow(row, editing, picked, onOpen = { openId = it.id }, onHold = { menuFor = it }, onPick = { id ->
                        picked = if (id in picked) picked - id else picked + id
                    }, onDrag = { id, steps ->
                        viewModel.reorder(NotebookEntry.shift(pinned.map { it.id }, id, steps))
                    })
                }
            }
            item { Text("Recent", style = MaterialTheme.typography.titleSmall, color = L.Ink) }
            recent.chunked(cols).forEach { row ->
                item { TileRow(row, editing, picked, { openId = it.id }, { menuFor = it }, { id ->
                    picked = if (id in picked) picked - id else picked + id
                }, onDrag = { _, _ -> }) }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    listOf("30" to "30 days", "365" to "1 year", "forever" to "Forever").forEach { (value, label) ->
                        LChip(label, keep == value, { viewModel.setChatKeep(value) })
                    }
                }
            }
            threads.forEach { thread ->
                item { ThreadRow(thread) { viewModel.showThread(thread.id) } }
            }
        }
    }

    menuFor?.let { clip ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(NotebookEntry.shown(clip.text, clip.editedText).take(40)) },
            text = {
                Column {
                    listOf("Pin", "Edit", "Copy", "Add to note", "Ask AI", "Delete").forEach { label ->
                        TextButton(onClick = {
                            when (label) {
                                "Pin" -> viewModel.pin(clip.id, !clip.pinned)
                                "Edit" -> openId = clip.id
                                "Copy" -> copy(context, NotebookEntry.shown(clip.text, clip.editedText))
                                "Add to note" -> scope.launch {
                                    val choice = viewModel.noteChoices().firstOrNull { it.id.startsWith("note:") }
                                    val id = choice?.id?.removePrefix("note:")?.toLongOrNull()
                                    if (id != null) viewModel.addToNote(clip.id, id)
                                }
                                "Ask AI" -> viewModel.ask(clip.text, clip.text, "", null, clip.sourceKind, clip.sourceApp, clip.windowTitle, false, null)
                                "Delete" -> viewModel.deleteClip(clip.id)
                            }
                            menuFor = null
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Close") } },
        )
    }

    val threadId = viewModel.openThread.collectAsState().value
    if (threadId != null && tab == "Chats") {
        val messages by viewModel.messages(threadId).collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = viewModel::closeThread,
            title = { Text(threads.firstOrNull { it.id == threadId }?.title ?: "Chat") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    messages.forEach { message ->
                        Text(message.text, color = L.Ink, modifier = Modifier.padding(bottom = 8.dp))
                        if (message.role == "assistant") {
                            Row {
                                TextButton(onClick = { viewModel.pinAnswer(message) }) { Text("Pin") }
                                TextButton(onClick = { copy(context, message.text) }) { Text("Copy") }
                                TextButton(onClick = { viewModel.saveAnswer(message) }) { Text("Save") }
                            }
                            com.ledgerai.app.domain.notebook.NotebookJson.actionsFrom(message.actionsJson).forEachIndexed { index, _ ->
                                TextButton(onClick = { viewModel.confirmAction(message, index) }) { Text("Confirm") }
                            }
                        }
                        val line = NotebookEntry.status(message.state, online(context))
                        if (line != null) {
                            Text(line, color = L.Highlight)
                            if (message.state == "failed") TextButton(onClick = { viewModel.retry(message) }) { Text("Retry") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val pinned = viewModel.threadHasPin(threadId)
                        if (pinned) askKeep = threadId else viewModel.deleteThread(threadId, alsoPinned = false)
                        if (!pinned) viewModel.closeThread()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::closeThread) { Text("Close") } },
        )
    }
    if (askKeep != null) {
        AlertDialog(
            onDismissRequest = { askKeep = null },
            title = { Text("Keep the pinned entry?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteThread(askKeep!!, alsoPinned = false); askKeep = null }) { Text("Keep") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteThread(askKeep!!, alsoPinned = true); askKeep = null }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun EntryEditor(
    clip: ClipEntity,
    screens: List<Pair<java.io.File, java.io.File>>,
    onBack: () -> Unit,
    onChange: (String) -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onAsk: () -> Unit,
) {
    var text by remember(clip.id) { mutableStateOf(NotebookEntry.shown(clip.text, clip.editedText)) }
    var showOriginal by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        delay(500)
        if (text != NotebookEntry.shown(clip.text, clip.editedText)) onChange(text)
    }
    LScreen(title = "Entry", onBack = onBack, action = {
        TextButton(onClick = onPin) { Text(if (clip.pinned) "Unpin" else "Pin") }
    }) {
        item {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).background(L.Box, RoundedCornerShape(12.dp)).padding(L.Space.x12),
            )
        }
        if (screens.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    screens.forEach { (thumb, full) ->
                        com.ledgerai.app.presentation.components.LocalPhoto(
                            thumb,
                            Modifier.heightIn(max = 160.dp).widthIn(max = 160.dp).clip(RoundedCornerShape(12.dp)),
                            fallback = full,
                        )
                    }
                }
            }
        }
        item {
            Text(
                listOfNotNull(clip.windowTitle, clip.sourceApp).joinToString(" · ").ifBlank { clip.sourceKind },
                color = L.InkMuted,
            )
            TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "Hide" else "Original") }
            if (showOriginal) Text(clip.text, color = L.Ink)
            if (clip.filedType != null) Text("Filed as ${clip.filedType}", color = L.Highlight)
        }
        item {
            Row {
                TextButton(onClick = onAsk) { Text("Ask AI") }
                TextButton(onClick = onCopy) { Text("Copy") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileRow(
    row: List<ClipEntity>,
    editing: Boolean,
    picked: Set<String>,
    onOpen: (ClipEntity) -> Unit,
    onHold: (ClipEntity) -> Unit,
    onPick: (String) -> Unit,
    onDrag: (String, Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        row.forEach { clip ->
            var delta by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier.weight(1f).heightIn(min = L.Target).background(L.Box, RoundedCornerShape(12.dp)).combinedClickable(
                    onClick = { if (editing) onPick(clip.id) else onOpen(clip) },
                    onLongClick = { onHold(clip) },
                ).then(
                    if (clip.pinned) Modifier.pointerInput(clip.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val steps = (delta / 48f).toInt()
                                if (steps != 0) onDrag(clip.id, steps)
                                delta = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                delta += amount.x
                            },
                        )
                    } else Modifier
                ).padding(L.Space.x12)
            ) {
                Text(
                    NotebookEntry.shown(clip.text, clip.editedText),
                    color = if (clip.id in picked) L.Highlight else L.Ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(clip.windowTitle ?: clip.sourceKind, color = L.InkMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(thread: ChatThreadEntity, onOpen: () -> Unit) {
    val day = Instant.ofEpochMilli(thread.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(Modifier.fillMaxWidth().heightIn(min = L.Target).combinedClickable(onClick = onOpen, onLongClick = {}).padding(vertical = 8.dp)) {
        Text(thread.title, color = L.Ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(thread.sourceApp ?: day.format(DateTimeFormatter.ISO_LOCAL_DATE), color = L.InkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun matches(clip: ClipEntity, filter: String, query: String): Boolean {
    val shown = NotebookEntry.shown(clip.text, clip.editedText)
    if (query.isNotBlank() && !shown.contains(query, ignoreCase = true)) return false
    return when (filter) {
        "Screen" -> clip.sourceKind == "screen" || clip.sourceKind == "ocr"
        "Typed" -> clip.sourceKind == "typed"
        "Voice" -> clip.sourceKind == "voice"
        "AI" -> clip.sourceKind == "ai"
        else -> true
    }
}

private fun copy(context: android.content.Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager?.setPrimaryClip(ClipData.newPlainText("notebook", text))
}

private fun online(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
    return cm?.activeNetwork != null
}
