package com.ledgerai.app.presentation.screens.clips

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewTreeObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.domain.clips.ClipFiling
import com.ledgerai.app.domain.clips.ClipJson
import com.ledgerai.app.domain.clips.ClipRules
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LSnackbar
import com.ledgerai.app.presentation.components.LSwitchRow
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.ProfileButton
import com.ledgerai.app.presentation.components.rememberVoiceInput
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddToNotesScreen(
    onBack: () -> Unit,
    viewModel: AddToNotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clips by viewModel.all.collectAsState()
    val notes by viewModel.notesList.collectAsState()
    val message by viewModel.message.collectAsState()
    val pending by viewModel.pendingText.collectAsState()
    val confirmId by viewModel.confirmClipId.collectAsState()
    val offer by viewModel.offerCopied.collectAsState()
    val stamp by viewModel.stamp.collectAsState()
    val collectingId by viewModel.collectingId.collectAsState()
    val menuOn by viewModel.menuOn.collectAsState()
    val fast by viewModel.fastMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val pinned by viewModel.pinned.collectAsState()
    val retention by viewModel.retention.collectAsState()
    val bubble by viewModel.bubble.collectAsState()
    val askOverlay by viewModel.askOverlay.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var segment by rememberSaveable { mutableStateOf("new") }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editText by rememberSaveable { mutableStateOf("") }
    var pickingNote by rememberSaveable { mutableStateOf(false) }
    var newCollectName by rememberSaveable { mutableStateOf("") }
    var howTo by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val voice = rememberVoiceInput { spoken -> if (spoken.isNotBlank()) query = spoken }
    val visible = clips.filter { clip ->
        clip.deletedAt == null &&
            (query.isBlank() || clip.text.contains(query, ignoreCase = true)) &&
            when (segment) {
                "new" -> clip.status == "inbox"
                "notes" -> clip.status == "in_note"
                "filed" -> clip.status == "filed"
                else -> true
            }
    }
    val collecting = notes.firstOrNull { it.id == collectingId }
    val inboxCount = clips.count { it.deletedAt == null && it.status == "inbox" }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(askOverlay) {
        if (askOverlay) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            viewModel.consumeOverlay()
        }
    }

    LScreen(
        title = "Add to Notes",
        onBack = onBack,
        action = { ProfileButton() },
        snackbarHost = { message?.let { LSnackbar(it) } },
    ) {
        item {
            LGroup {
                if (collecting != null) {
                    LGroupRow(
                        title = "Collecting into",
                        sub = "${collecting.title} · ${clips.count { it.noteId == collecting.id && it.deletedAt == null }} clips",
                        trailing = "Stop",
                        onClick = { viewModel.stopCollecting() },
                        end = {
                            Text(
                                "Change",
                                color = L.Primary,
                                modifier = Modifier.semantics { contentDescription = "Change collecting note" },
                            )
                        },
                    )
                } else {
                    LGroupRow(
                        title = "Collecting into",
                        sub = "Off",
                        trailing = "Start",
                        trailingColor = L.Primary,
                        onClick = { pickingNote = true },
                    )
                }
            }
        }
        item {
            CopiedTextChip(enabled = offer, lastStamp = stamp, onStamp = viewModel::rememberStamp, onText = viewModel::onCopied)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LField(query, { query = it }, "Search clips…", modifier = Modifier.weight(1f))
                LToolButton(Icons.Filled.Mic, if (voice.listening) "Stop" else "Search by voice", voice.toggle, active = voice.listening)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("new" to "New ($inboxCount)", "notes" to "In notes", "filed" to "Filed", "all" to "All").forEach { (id, label) ->
                    LChip(label, selected = segment == id, onClick = { segment = id; selected = emptySet() })
                }
            }
        }
        if (selected.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LGhostButton("Combine into one note", { viewModel.combine(selected.toList(), null, null); selected = emptySet() }, fill = false)
                    LGhostButton("Add to note…", { pickingNote = true }, fill = false)
                    LGhostButton("Ask AI", { viewModel.ask(selected.toList()); selected = emptySet() }, fill = false)
                    LGhostButton("Delete", {
                        selected.forEach { id -> clips.firstOrNull { it.id == id }?.let { viewModel.delete(it, false) } }
                        selected = emptySet()
                    }, fill = false)
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyClips() }
        } else {
            groupClips(visible).forEach { (label, rows) ->
                item(key = label) { LSection(label) }
                item(key = "$label-rows") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { clip ->
                            ClipCard(
                                clip = clip,
                                noteTitle = notes.firstOrNull { it.id == clip.noteId }?.title,
                                source = sourceLabel(context, clip.sourceApp),
                                selected = clip.id in selected,
                                onClick = { if (selected.isEmpty()) openId = clip.id else selected = selected.toggle(clip.id) },
                                onLongClick = { selected = selected.toggle(clip.id) },
                                onAdd = { viewModel.accept(clip) },
                                onAsk = { viewModel.ask(listOf(clip.id)) },
                                onRetry = { viewModel.retry(clip.id) },
                                onSplit = { viewModel.split(clip) },
                                onMore = { openId = clip.id },
                                onSwipeNote = { viewModel.swipeToNote(clip) },
                                onSwipeDismiss = { viewModel.dismiss(clip) },
                            )
                        }
                    }
                }
            }
        }
        item { LSection("Settings") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LSwitchRow("Show \"Add to Notes\" in the text menu", menuOn, viewModel::setMenu)
                LSwitchRow("Skip the sheet (fast mode)", fast, viewModel::setFast)
                LSwitchRow("Offer to add what I copied", offer, viewModel::setOffer)
                Text("AI filing", color = L.InkMuted, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(ClipFiling.OFF to "Off", ClipFiling.SUGGEST to "Suggest", ClipFiling.AUTO to "Auto for simple things").forEach { (id, label) ->
                        LChip(label, selected = aiMode == id, onClick = { viewModel.setAi(id) })
                    }
                }
                LGroupRow(title = "Quick Settings tile", sub = "How to add", onClick = { howTo = true })
                LSwitchRow("Pinned notification", pinned, viewModel::setPin)
                Text("Keep clips for", color = L.InkMuted, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("30" to "30 days", "365" to "1 year", "forever" to "Forever").forEach { (id, label) ->
                        LChip(label, selected = retention == id, onClick = { viewModel.setRetention(id) })
                    }
                }
                LSwitchRow("Experimental floating button", bubble, viewModel::setBubble)
            }
        }
    }

    val open = clips.firstOrNull { it.id == openId }
    if (open != null && !editing) {
        ClipDetail(open, sourceLabel(context, open.sourceApp), notes.firstOrNull { it.id == open.noteId }?.title, onDismiss = { openId = null },
            onCopy = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("clip", open.text))
            },
            onEdit = { editText = open.text; editing = true },
            onOpenLink = {
                val url = open.url ?: ClipRules.firstUrl(open.text)
                if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onDelete = { deleteTarget = open.id },
        )
    }
    if (editing && open != null) {
        LSheet(title = "Edit text", onDismiss = { editing = false }, primary = "Save", onPrimary = {
            viewModel.edit(open.id, editText)
            editing = false
        }) { LField(editText, { editText = it }, "Text") }
    }
    deleteTarget?.let { id ->
        val clip = clips.firstOrNull { it.id == id }
        if (clip?.filedId != null) {
            LSheet(
                title = "This clip is the source of 1 ${filedWord(clip.filedType)}. Delete the clip only, or both?",
                onDismiss = { deleteTarget = null },
                primary = "Delete both",
                onPrimary = { viewModel.delete(clip, true); deleteTarget = null; openId = null },
                secondary = "Clip only",
                onSecondary = { viewModel.delete(clip, false); deleteTarget = null; openId = null },
            ) {}
        } else if (clip != null) {
            LaunchedEffect(clip.id) {
                viewModel.delete(clip, false)
                deleteTarget = null
                openId = null
            }
        }
    }
    pending?.let { text ->
        val choices by produceState(initialValue = emptyList<com.ledgerai.app.data.clips.ClipChoice>(), text) { value = viewModel.choices() }
        var selectedChoice by remember(text) { mutableStateOf(choices.firstOrNull()?.id ?: "inbox") }
        LaunchedEffect(choices) { if (selectedChoice.isBlank()) selectedChoice = choices.firstOrNull()?.id ?: "inbox" }
        val read = remember(text) { ClipRules.read(text) }
        ClipSheet(
            text = text,
            sourceLabel = "Clipboard",
            read = read,
            choices = choices,
            selectedId = selectedChoice.ifBlank { choices.firstOrNull()?.id ?: "inbox" },
            sensitive = ClipRules.isSensitiveText(text),
            onSelect = { selectedChoice = it },
            onSave = { viewModel.savePending(selectedChoice, askAi = false, saveAnyway = ClipRules.isSensitiveText(text)) },
            onAsk = { viewModel.savePending(selectedChoice, askAi = true, saveAnyway = false) },
            onTrackJob = { viewModel.savePending(selectedChoice, askAi = false, saveAnyway = false) },
            onDismiss = { viewModel.dismissPending() },
        )
    }
    confirmId?.let { id ->
        val clip = clips.firstOrNull { it.id == id }
        val suggestion = clip?.aiJson?.let(ClipJson::fromStored)
        if (clip != null && suggestion != null) {
            LSheet(
                title = "Confirm",
                onDismiss = { viewModel.dismissConfirm() },
                primary = "Confirm",
                onPrimary = { viewModel.confirmHigh() },
                secondary = "Cancel",
                onSecondary = { viewModel.dismissConfirm() },
            ) {
                Text(suggestion.title, color = L.Ink, fontWeight = FontWeight.SemiBold)
                suggestion.amount?.let { Text(it.toString(), color = L.Ink) }
                Text("This writes money only because you confirm it.", color = L.InkMuted)
            }
        }
    }
    if (pickingNote) {
        LSheet(title = "Collecting note", onDismiss = { pickingNote = false }, primary = "Start", onPrimary = {
            if (selected.isNotEmpty()) {
                viewModel.combine(selected.toList(), null, newCollectName)
                selected = emptySet()
            } else {
                viewModel.startCollecting(null, newCollectName)
            }
            pickingNote = false
        }) {
            LField(newCollectName, { newCollectName = it }, "New note name")
            notes.take(8).forEach { note ->
                LGroupRow(title = note.title, onClick = {
                    if (selected.isNotEmpty()) viewModel.addSelectionTo(selected.toList(), note.id) else viewModel.startCollecting(note.id, null)
                    selected = emptySet()
                    pickingNote = false
                })
            }
        }
    }
    if (howTo) {
        LSheet(title = "Quick Settings tile", onDismiss = { howTo = false }, primary = "Done", onPrimary = { howTo = false }) {
            Text("Copy text, pull down the shade, and tap Add copied text.", color = L.Ink)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ClipCard(
    clip: ClipEntity,
    noteTitle: String?,
    source: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAdd: () -> Unit,
    onAsk: () -> Unit,
    onRetry: () -> Unit,
    onSplit: () -> Unit,
    onMore: () -> Unit,
    onSwipeNote: () -> Unit,
    onSwipeDismiss: () -> Unit,
) {
    val time = DateTimeFormatter.ofPattern("HH:mm").format(
        Instant.ofEpochMilli(clip.capturedAt).atZone(ZoneId.systemDefault()).toLocalTime()
    )
    val where = noteTitle ?: if (clip.status == "inbox") "inbox" else clip.status
    val suggestion = ClipJson.fromStored(clip.aiJson)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onSwipeNote(); false }
                SwipeToDismissBoxValue.EndToStart -> { onSwipeDismiss(); true }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(Modifier.fillMaxWidth().background(L.BoxDeep).padding(16.dp)) {
                Text("Add to note or dismiss", color = L.InkMuted)
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (selected) L.BoxDeep else L.Box, RoundedCornerShape(L.Radius))
                .border(1.dp, L.Line, RoundedCornerShape(L.Radius))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp)
                .semantics { contentDescription = "Clip from $source" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(clip.text, maxLines = 3, overflow = TextOverflow.Ellipsis, color = L.OnBox)
            if (clip.text.length >= ClipRules.MAX_TEXT) Text("trimmed", color = L.InkMuted, style = MaterialTheme.typography.bodySmall)
            Text("$source · $time · $where", color = L.OnBoxMuted, style = MaterialTheme.typography.bodySmall)
            when {
                clip.aiState == "queued" -> Text("Waiting for network", color = L.Highlight)
                clip.aiState == "failed" -> LGhostButton("Retry", onRetry, fill = false)
                suggestion?.capped == true -> Text("AI limit reached today. This clip stays a note.", color = L.InkMuted)
                suggestion != null && clip.filedId == null && suggestion.kind != "other" -> {
                    Text(
                        ClipFiling.suggestionLine(suggestion.kind, suggestion.title) + if (suggestion.inferred) " · Inferred" else "",
                        color = L.Highlight,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LButton(if (ClipFiling.risk(suggestion.kind) == com.ledgerai.app.domain.assistant.Risk.HIGH) "Review" else "Add", onAdd, fill = false)
                        if (suggestion.split.size >= 2) LGhostButton("Split into ${suggestion.split.size} notes", onSplit, fill = false)
                    }
                }
                clip.filedId != null -> Text("Filed as ${filedWord(clip.filedType)}", color = L.InkMuted)
                clip.status == "inbox" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LGhostButton("Add to note", onSwipeNote, fill = false)
                    LGhostButton("Ask AI", onAsk, fill = false)
                    LGhostButton("More", onMore, fill = false)
                }
            }
        }
    }
}

@Composable
private fun ClipDetail(
    clip: ClipEntity,
    source: String,
    noteTitle: String?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onOpenLink: () -> Unit,
    onDelete: () -> Unit,
) {
    LSheet(title = "Clip", onDismiss = onDismiss, primary = "Close", onPrimary = onDismiss) {
        Text(clip.text, color = L.Ink)
        Text(source, color = L.InkMuted)
        noteTitle?.let { Text(it, color = L.InkMuted) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LGhostButton("Copy", onCopy, fill = false)
            LGhostButton("Edit text", onEdit, fill = false)
            if (clip.url != null || ClipRules.firstUrl(clip.text) != null) LGhostButton("Open link", onOpenLink, fill = false)
            LGhostButton("Delete", onDelete, fill = false)
        }
        clip.filedType?.let { Text("Filed as ${filedWord(it)} ›", color = L.Primary) }
    }
}

@Composable
private fun EmptyClips() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LEmpty(Icons.Filled.ContentPaste, "Select text in any app and tap Add to Notes.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Copy", "Add to Notes", "Select all").forEach { label ->
                Box(
                    Modifier.width(96.dp).height(64.dp).border(1.dp, L.Line, RoundedCornerShape(8.dp)).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = L.Ink, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
fun CopiedTextChip(
    enabled: Boolean,
    lastStamp: Long,
    onStamp: (Long) -> Unit,
    onText: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var show by remember { mutableStateOf(false) }
    var stamp by remember { mutableStateOf(0L) }
    DisposableEffect(view, enabled, lastStamp) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused || !enabled) return@OnWindowFocusChangeListener
            val description = context.getSystemService(ClipboardManager::class.java)?.primaryClipDescription
            val text = description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
            val time = description?.timestamp ?: 0L
            if (text && time > lastStamp && time > 0L) {
                stamp = time
                show = true
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        if (view.hasWindowFocus()) listener.onWindowFocusChanged(true)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    if (!show || !enabled) return
    LChip("Add what you copied", selected = true, onClick = {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        onStamp(stamp)
        show = false
        if (text.isNotBlank()) onText(text)
    })
}

private fun groupClips(clips: List<ClipEntity>): List<Pair<String, List<ClipEntity>>> {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val groups = linkedMapOf<String, MutableList<ClipEntity>>()
    clips.forEach { clip ->
        val day = Instant.ofEpochMilli(clip.capturedAt).atZone(zone).toLocalDate()
        val label = when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        }
        groups.getOrPut(label) { mutableListOf() } += clip
    }
    return groups.map { it.key to it.value }
}

private fun sourceLabel(context: Context, pkg: String?): String {
    if (pkg.isNullOrBlank()) return "Unknown app"
    return runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("Unknown app")
}

private fun filedWord(type: String?): String = when (type) {
    "calendar_event" -> "event"
    "task" -> "task"
    "job" -> "job"
    "item" -> "shopping item"
    "memory" -> "memory"
    "bill" -> "bill"
    "transaction" -> "transaction"
    "debt" -> "debt"
    else -> "note"
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
