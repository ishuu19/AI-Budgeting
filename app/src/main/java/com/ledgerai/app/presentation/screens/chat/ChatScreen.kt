package com.ledgerai.app.presentation.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.presentation.components.AsyncUriThumb
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.screens.capture.AnswerCards
import com.ledgerai.app.presentation.screens.capture.CaptureEvent
import com.ledgerai.app.presentation.screens.capture.CaptureViewModel
import com.ledgerai.app.presentation.screens.capture.ChatMsg
import com.ledgerai.app.presentation.screens.capture.InboxCard
import com.ledgerai.app.presentation.screens.capture.ManualAddSheet
import com.ledgerai.app.presentation.screens.today.TodayItem
import com.ledgerai.app.presentation.screens.today.TodayViewModel
import com.ledgerai.app.presentation.screens.voice.ConfirmCard
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderState
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Starters = listOf("What can I cook today?", "What should I wear today?", "I spent 12 on lunch", "Remind me to call mum at 6")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

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
    val tail = messages.size + voice.cards.size + (if (voice.notice != null) 1 else 0)
    LaunchedEffect(tail) {
        if (tail > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    Column(Modifier.fillMaxSize().background(L.Page)) {
        Header(links)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = L.Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "brief") { Briefing(day.items, day.overdueTasks, day.billsDue, day.guide, day.spentToday, links.today) }
            if (messages.isEmpty() && voice.cards.isEmpty()) {
                item(key = "hello") {
                    BotBubble("Hi. Tell me or show me anything and I'll sort it out. A receipt, your fridge, an outfit, a plan.")
                }
            }
            if (waiting.isNotEmpty()) {
                item(key = "waiting-title") { Text("Needs your OK", style = MaterialTheme.typography.titleSmall, color = L.InkMuted) }
                items(waiting.take(4), key = { "w-${it.id}" }) { row -> InboxCard(row, cvm, links.receipt, links.person) }
            }
            items(messages, key = { "m-${it.id}" }) { msg ->
                when (msg) {
                    is ChatMsg.User -> UserBubble(msg)
                    is ChatMsg.Bot -> BotBubble(msg.text)
                    is ChatMsg.Photos -> msg.assetIds.forEach { id ->
                        val row = inbox.firstOrNull { it.id == id }
                        if (row != null) InboxCard(row, cvm, links.receipt, links.person)
                        else BotBubble("Looking at your photo…")
                    }
                    is ChatMsg.Answer -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnswerCards(msg.answer, cvm)
                    }
                }
            }
            if (voice.recorderState == VoiceRecorderState.PARSING || voice.recorderState == VoiceRecorderState.TRANSCRIBING) {
                item(key = "parsing") { BotBubble("Reading that…") }
            }
            if (voice.recorderState == VoiceRecorderState.RESULT && voice.cards.isNotEmpty()) {
                item(key = "understood") { BotBubble("Here is what I understood. Check it and save.") }
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
                            Pill("Save all", filled = true, onClick = vvm::saveAll)
                            Pill("Discard", filled = false, onClick = vvm::discardAll)
                        }
                    }
                }
            }
            voice.errorMessage?.let { msg -> item(key = "voice-error") { BotBubble(msg) } }
            voice.notice?.let { notice ->
                item(key = "saved-${notice.nonce}") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(L.RadiusSm)).background(L.BoxDeep).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(notice.message, style = MaterialTheme.typography.bodyMedium, color = L.Ink, modifier = Modifier.weight(1f))
                        notice.open?.let { (kind, id) ->
                            Text("Open", style = MaterialTheme.typography.labelLarge, color = L.Primary,
                                modifier = Modifier.clickable { links.open(kind, id); vvm.dismissNotice() }.padding(8.dp))
                        }
                        Text("Undo", style = MaterialTheme.typography.labelLarge, color = L.Primary,
                            modifier = Modifier.clickable { vvm.undoNotice() }.padding(8.dp))
                    }
                }
            }
            if (ui.busy) item(key = "busy") { BotBubble("Thinking…") }
        }

        Composer(
            text = ui.text,
            images = ui.images,
            listening = ui.listening,
            notice = ui.notice,
            showStarters = messages.isEmpty() && ui.text.isBlank() && ui.images.isEmpty(),
            onText = cvm::setText,
            onMic = ::toggleMic,
            onCamera = ::openCamera,
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = cvm::removeImage,
            onSend = cvm::submit,
            onStarter = cvm::quickAsk,
            onManual = { manual = true },
        )
    }
    if (manual) ManualAddSheet(links, onDismiss = { manual = false })
}

@Composable
private fun Header(links: AppLinks) {
    val hour = LocalDateTime.now().hour
    val hello = when {
        hour in 5..11 -> "Good morning"
        hour in 12..17 -> "Good afternoon"
        hour in 18..21 -> "Good evening"
        else -> "Hello"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = L.Gutter, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hello, style = MaterialTheme.typography.headlineMedium, color = L.Ink, modifier = Modifier.weight(1f))
        HeaderIcon(Icons.Filled.Search, "Search", links.search)
        HeaderIcon(Icons.Filled.MoreHoriz, "More and settings", links.you)
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).semantics { role = Role.Button; contentDescription = label }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = L.Primary) }
}

/** A glance at the day, so the chat starts with what matters. Tap for the full day. */
@Composable
private fun Briefing(items: List<TodayItem>, overdue: Int, bills: Int, guide: Double?, spent: Double, onOpen: () -> Unit) {
    val now = LocalDateTime.now()
    val next = items.firstOrNull { it.end.isAfter(now) && !it.allDay }
    val lines = buildList {
        add(next?.let { "Next: ${it.title} at ${it.start.format(timeFmt)}" } ?: "Nothing else planned today")
        if (overdue > 0) add("$overdue overdue task${if (overdue > 1) "s" else ""}")
        if (bills > 0) add("$bills bill${if (bills > 1) "s" else ""} due")
        guide?.let { add("Safe to spend today: ${com.ledgerai.app.presentation.components.money(it)}") }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(L.Radius))
            .background(L.Box)
            .border(1.dp, L.Line, RoundedCornerShape(L.Radius))
            .clickable(onClickLabel = "Open the full day", onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("TODAY", style = MaterialTheme.typography.labelSmall, color = L.Gold)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.OnBox) }
    }
}

@Composable
private fun UserBubble(msg: ChatMsg.User) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (msg.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                msg.images.take(4).forEach { AsyncUriThumb(it, Modifier.size(84.dp).clip(RoundedCornerShape(L.RadiusSm))) }
            }
        }
        if (msg.text.isNotBlank()) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyLarge,
                color = L.OnPrimary,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(L.Primary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = L.OnBox,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(L.Box)
            .border(1.dp, L.Line, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (filled) L.OnPrimary else L.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) L.Primary else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, L.Primary, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun Composer(
    text: String,
    images: List<android.net.Uri>,
    listening: Boolean,
    notice: String?,
    showStarters: Boolean,
    onText: (String) -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemoveImage: (android.net.Uri) -> Unit,
    onSend: () -> Unit,
    onStarter: (String) -> Unit,
    onManual: () -> Unit,
) {
    val canSend = text.isNotBlank() || images.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().background(L.Page).imePadding().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showStarters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Starters.forEach { LChip(it, selected = false, onClick = { onStarter(it) }) }
            }
        }
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { uri ->
                    Box(Modifier.size(64.dp)) {
                        AsyncUriThumb(uri, Modifier.size(64.dp).clip(RoundedCornerShape(L.RadiusSm)))
                        Box(
                            Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(L.Ink.copy(alpha = 0.75f))
                                .semantics { role = Role.Button; contentDescription = "Remove photo" }
                                .clickable { onRemoveImage(uri) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, contentDescription = null, tint = L.Page, modifier = Modifier.size(12.dp)) }
                    }
                }
            }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.InkMuted) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(L.Box)
                    .border(1.dp, L.Line, RoundedCornerShape(28.dp))
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (text.isEmpty()) {
                        Text(if (listening) "Listening…" else "Say, type or snap anything", style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                        cursorBrush = SolidColor(L.Primary),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SmallIcon(Icons.Filled.PhotoCamera, "Take a photo", onCamera)
                SmallIcon(Icons.Filled.PhotoLibrary, "Choose photos", onGallery)
            }
            // One round button: the mic when there is nothing to send, send when there is.
            val sendMode = canSend && !listening
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (listening) L.Danger else L.Highlight)
                    .semantics { role = Role.Button; contentDescription = if (sendMode) "Send" else if (listening) "Stop listening" else "Speak" }
                    .clickable(onClick = if (sendMode) onSend else onMic),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (sendMode) Icons.Filled.Send else if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF0B1B45),
                )
            }
        }
        Text(
            "Add manually",
            style = MaterialTheme.typography.labelMedium,
            color = L.InkMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable(onClick = onManual).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).semantics { role = Role.Button; contentDescription = label }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = L.Primary, modifier = Modifier.size(22.dp)) }
}
