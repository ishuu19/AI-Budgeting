package com.ledgerai.app.presentation.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.presentation.components.AsyncUriThumb
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LHeader
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSnackbar
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.screens.capture.AnswerCards
import com.ledgerai.app.presentation.screens.capture.AskComposer
import com.ledgerai.app.presentation.screens.capture.CaptureEvent
import com.ledgerai.app.presentation.screens.capture.CaptureViewModel
import com.ledgerai.app.presentation.screens.capture.ChatMsg
import com.ledgerai.app.presentation.screens.capture.InboxCard
import com.ledgerai.app.presentation.screens.capture.ManualAddSheet
import com.ledgerai.app.presentation.screens.capture.TypingBubble
import com.ledgerai.app.presentation.screens.today.TodayItem
import com.ledgerai.app.presentation.screens.today.TodayViewModel
import com.ledgerai.app.presentation.screens.voice.ConfirmCard
import com.ledgerai.app.presentation.screens.voice.SavedNotice
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderState
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/**
 * Home. One conversation: say it, type it, or send a photo, and the AI does the work and shows what it did.
 * Anything that needs an answer or a tap appears right in the thread. Manual entry is one tap away.
 */
@Composable
fun ChatScreen(
    links: AppLinks,
    micRequest: Boolean,
    onMicConsumed: () -> Unit,
    holdMic: Boolean,
    seed: String?,
    onSeedConsumed: () -> Unit,
    cvm: CaptureViewModel = hiltViewModel(),
    vvm: VoiceRecorderViewModel = hiltViewModel(),
    today: TodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by cvm.ui.collectAsStateWithLifecycle()
    val messages by cvm.messages.collectAsStateWithLifecycle()
    val inbox by cvm.inbox.collectAsStateWithLifecycle()
    val voice by vvm.uiState.collectAsStateWithLifecycle()
    val day by today.state.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val history by cvm.history.collectAsStateWithLifecycle()

    // Going out of the app files this chat under History and opens a fresh one. Camera and gallery
    // cover the app too, but that is not leaving.
    val activity = context as? androidx.activity.ComponentActivity
    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> cvm.onAppStopped(activity?.isChangingConfigurations == true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> cvm.onAppStarted()
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { cvm.onCameraResult(it) }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CaptureViewModel.MAX_IMAGES)
    ) { cvm.addImages(it) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cvm.startListening(autoSend = true)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(cvm.newCameraUri())
    }

    fun toggleMic() {
        if (ui.listening) {
            cvm.stopListening()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            cvm.startListening(autoSend = true)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openCamera() {
        cvm.holdArchive()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(cvm.newCameraUri())
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cvm) {
        cvm.events.collect { if (it is CaptureEvent.HandOff) vvm.offerText(it.text) }
    }
    // A tap on the big mic is a one-time request. Coming back to Home never reopens the mic.
    LaunchedEffect(micRequest) { if (micRequest) { onMicConsumed(); toggleMic() } }
    LaunchedEffect(holdMic) {
        if (holdMic) {
            if (!ui.listening) toggleMic()
        } else if (ui.listening) {
            cvm.stopListening()
        }
    }
    LaunchedEffect(seed) {
        seed?.let { vvm.offerText(it); onSeedConsumed() }
    }

    // Photos already shown as a message are not repeated in the "needs you" list.
    val shown = remember(messages) { messages.filterIsInstance<ChatMsg.Photos>().flatMap { it.assetIds }.toSet() }
    val waiting = inbox.filter {
        it.id !in shown && (it.analysisState == AnalysisState.NEEDS_INPUT || it.analysisState == AnalysisState.FAILED)
    }

    val listState = rememberLazyListState()
    val parsing = voice.recorderState == VoiceRecorderState.PARSING || voice.recorderState == VoiceRecorderState.TRANSCRIBING
    val tail = messages.size + voice.cards.size + (if (ui.busy || parsing) 1 else 0)
    LaunchedEffect(tail) {
        if (tail > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    Column(Modifier.fillMaxSize().background(L.Page)) {
        Header(links, onHistory = { showHistory = true }, onNew = cvm::newChat, hasMessages = messages.isNotEmpty())
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = L.Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "brief") {
                Briefing(day.items, day.overdueTasks, day.billsDue, day.guide, day.spentToday, day.guideStatus, links.today)
            }
            if (waiting.isNotEmpty()) {
                item(key = "waiting-title") { LSection("Needs you") }
                items(waiting.take(4), key = { "w-${it.id}" }) { row -> InboxCard(row, cvm, links.receipt, links.person) }
            }
            items(messages, key = { "m-${it.id}" }) { msg ->
                when (msg) {
                    is ChatMsg.User -> UserBubble(msg)
                    is ChatMsg.Bot -> BotBubble(msg.text)
                    is ChatMsg.Photos -> msg.assetIds.forEach { id ->
                        val row = inbox.firstOrNull { it.id == id }
                        if (row != null) InboxCard(row, cvm, links.receipt, links.person)
                        else TypingBubble()
                    }
                    is ChatMsg.Answer -> AnswerCards(msg.answer, cvm)
                }
            }
            if (parsing) item(key = "parsing") { TypingBubble() }
            if (voice.recorderState == VoiceRecorderState.RESULT && voice.cards.isNotEmpty()) {
                items(voice.cards, key = { "c-${it.key}" }) { card ->
                    ConfirmCard(
                        card = card,
                        onChange = { intent, edited -> vvm.updateCard(card.key, intent, edited) },
                        onSave = { vvm.saveCard(card.key) },
                        onDiscard = { vvm.discardCard(card.key) },
                    )
                }
                if (voice.cards.size > 1) {
                    item(key = "save-all") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LButton("Save all", vvm::saveAll, Modifier.weight(1f))
                            LGhostButton("Discard", vvm::discardAll, Modifier.weight(1f))
                        }
                    }
                }
            }
            voice.record?.let { r -> item(key = "record") { com.ledgerai.app.presentation.screens.voice.RecordCard(r, vvm::saveRecord, vvm::dismissRecord, links.passwords) } }
            voice.errorMessage?.let { msg -> item(key = "voice-error") { BotBubble(msg) } }
            if (ui.busy) item(key = "busy") { TypingBubble() }
        }
        voice.notice?.let { notice ->
            SavedNoticeBar(
                notice,
                onOpen = {
                    notice.open?.let { (kind, id) -> links.open(kind, id) }
                    vvm.dismissNotice()
                },
                onUndo = vvm::undoNotice,
            )
        }
        AskComposer(
            text = ui.text,
            onText = cvm::setText,
            images = ui.images,
            listening = ui.listening,
            canSend = ui.text.isNotBlank() || ui.images.isNotEmpty(),
            notice = ui.notice,
            showStarters = messages.isEmpty() && ui.text.isBlank() && ui.images.isEmpty(),
            onMic = ::toggleMic,
            onSend = cvm::submit,
            onCamera = ::openCamera,
            onGallery = { cvm.holdArchive(); gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = cvm::removeImage,
            onStarter = cvm::quickAsk,
            onManual = { manual = true },
            showMic = false,
            modifier = Modifier
                .fillMaxWidth()
                .background(L.Page)
                .imePadding()
                .padding(horizontal = L.Gutter)
                .padding(top = 8.dp, bottom = 8.dp),
        )
    }
    if (manual) ManualAddSheet(links, onDismiss = { manual = false })
    if (showHistory) {
        ChatHistorySheet(
            threads = history,
            onOpen = { cvm.continueChat(it); showHistory = false },
            onDelete = cvm::deleteChat,
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun Header(links: AppLinks, onHistory: () -> Unit, onNew: () -> Unit, hasMessages: Boolean) {
    LHeader(
        title = LocalDate.now().format(dayFmt),
        modifier = Modifier.padding(horizontal = L.Gutter, vertical = 8.dp),
        actions = {
            if (hasMessages) LToolButton(Icons.Filled.Add, "New chat", onNew)
            LToolButton(Icons.Filled.History, "Chat history", onHistory)
            LToolButton(Icons.Filled.Search, "Search", links.search)
        },
    )
}

/** Past home chats, newest first. Tap one to continue it; delete removes it. */
@Composable
private fun ChatHistorySheet(
    threads: List<com.ledgerai.app.data.chat.ChatThreadEntity>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<String?>(null) }
    val stamp = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.US)
    com.ledgerai.app.presentation.components.LSheet(title = "Chat history", onDismiss = onDismiss) {
        if (threads.isEmpty()) {
            Text("Past chats show up here after you leave the app.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        } else {
            com.ledgerai.app.presentation.components.LGroup {
                threads.forEachIndexed { index, thread ->
                    if (index > 0) com.ledgerai.app.presentation.components.LGroupDivider()
                    com.ledgerai.app.presentation.components.LGroupRow(
                        title = thread.title,
                        sub = java.time.Instant.ofEpochMilli(thread.updatedAt)
                            .atZone(java.time.ZoneId.systemDefault()).format(stamp),
                        onClick = { onOpen(thread.id) },
                        end = { LToolButton(Icons.Filled.Delete, "Delete chat", { deleting = thread.id }) },
                    )
                }
            }
            Text("Tap a chat to continue it.", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
        }
    }
    deleting?.let { id ->
        com.ledgerai.app.presentation.components.LConfirm(
            title = "Delete this chat?",
            onConfirm = { onDelete(id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

/** A glance at the day. Time and title, safe to spend, then late and bill counts. Tap for the full day. */
@Composable
private fun Briefing(
    items: List<TodayItem>,
    overdue: Int,
    bills: Int,
    guide: Double?,
    spent: Double,
    status: SpendGuideStatus?,
    onOpen: () -> Unit,
) {
    val now = LocalDateTime.now()
    val next = items.firstOrNull { it.end.isAfter(now) && !it.allDay }
    LCard(onClick = onOpen) {
        if (next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(next.start.format(timeFmt), style = MaterialTheme.typography.titleMedium, color = L.OnBox)
                Text(
                    next.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = L.OnBox,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text("Nothing planned", style = MaterialTheme.typography.bodyLarge, color = L.OnBoxMuted)
        }
        if (guide != null) {
            val over = status == SpendGuideStatus.OVER || (guide > 0 && spent > guide)
            val near = !over && (status == SpendGuideStatus.NEAR || (guide > 0 && spent >= guide * 0.8))
            Text("Safe to spend", style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
            Text(
                money(guide),
                style = MaterialTheme.typography.titleLarge,
                color = if (over) L.Danger else L.OnBox,
                maxLines = 1,
            )
            if (guide > 0) {
                LProgress((spent / guide).toFloat().coerceIn(0f, 1f), color = if (near) L.Highlight else L.Gold)
            }
        }
        if (overdue > 0 || bills > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overdue > 0) CountBadge("$overdue late")
                if (bills > 0) CountBadge(if (bills == 1) "1 bill" else "$bills bills")
            }
        }
    }
}

@Composable
private fun CountBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = L.OnBox,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(L.BoxDeep)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun UserBubble(msg: ChatMsg.User) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val max = maxWidth * 0.85f
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msg.images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    msg.images.take(4).forEach { AsyncUriThumb(it, Modifier.size(84.dp).clip(RoundedCornerShape(L.RadiusSm))) }
                }
            }
            if (msg.text.isNotBlank()) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = L.OnPrimary,
                    modifier = Modifier
                        .widthIn(max = max)
                        .clip(RoundedCornerShape(topStart = L.Radius, topEnd = L.Radius, bottomStart = L.Radius, bottomEnd = 8.dp))
                        .background(L.Primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = L.OnBox,
            modifier = Modifier
                .widthIn(max = maxWidth * 0.85f)
                .clip(RoundedCornerShape(topStart = L.Radius, topEnd = L.Radius, bottomStart = 8.dp, bottomEnd = L.Radius))
                .background(L.Box)
                .border(1.dp, L.Line, RoundedCornerShape(topStart = L.Radius, topEnd = L.Radius, bottomStart = 8.dp, bottomEnd = L.Radius))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Undo fits [LSnackbar]. Open plus Undo does not: that bar has one action slot. */
@Composable
private fun SavedNoticeBar(notice: SavedNotice, onOpen: () -> Unit, onUndo: () -> Unit) {
    val pad = Modifier.padding(horizontal = L.Gutter).padding(bottom = 8.dp)
    if (notice.open == null) {
        LSnackbar(notice.message, pad, action = "Undo", onAction = onUndo)
    } else {
        Row(
            pad
                .fillMaxWidth()
                .clip(RoundedCornerShape(L.Radius))
                .background(L.Box)
                .border(1.dp, L.Line, RoundedCornerShape(L.Radius))
                .padding(horizontal = L.Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                notice.message,
                style = MaterialTheme.typography.bodyMedium,
                color = L.Ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LTextLink("Open", onOpen)
            LTextLink("Undo", onUndo)
        }
    }
}
