package com.ledgerai.app.presentation.screens.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LMicButton
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSnackbar
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.rememberVoiceInput
import com.ledgerai.app.presentation.navigation.AppLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MicSize = 128.dp
private val MicArea = 192.dp
private const val NOTICE_MS = 6000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun VoiceRecorderScreen(
    onNavigateBack: () -> Unit = {},
    embedded: Boolean = false,
    links: AppLinks = AppLinks(),
    holdMic: Boolean = false,
    seed: String? = null,
    onSeedConsumed: () -> Unit = {},
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sheetItemId by rememberSaveable { mutableStateOf<Long?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            // After a refusal, no rationale left means "don't ask again": only settings can fix it.
            val permanent = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            viewModel.onPermissionDenied(permanent)
        }
    }

    fun startMic() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(holdMic) {
        if (holdMic) startMic() else viewModel.stopRecording()
    }

    LaunchedEffect(seed) {
        val text = seed?.trim().orEmpty()
        if (text.isNotEmpty()) {
            viewModel.offerText(text)
            onSeedConsumed()
        }
    }

    // The mic never keeps running behind another screen, another segment or a backgrounded app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isChangingConfigurations != true) viewModel.onLeave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) viewModel.onLeave()
        }
    }

    val recording = state.recorderState == VoiceRecorderState.RECORDING
    val busy = state.recorderState == VoiceRecorderState.TRANSCRIBING ||
        state.recorderState == VoiceRecorderState.PARSING
    val notice = state.notice

    val back: (() -> Unit)? = if (embedded) null else ({
        viewModel.cancelRecording()
        onNavigateBack()
    })

    LaunchedEffect(notice?.nonce) {
        if (notice != null) {
            delay(NOTICE_MS)
            viewModel.dismissNotice()
        }
    }

    LScreen(
        title = "Voice",
        onBack = back,
        listState = listState,
        snackbarHost = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notice != null) {
                    SavedBar(
                        message = notice.message,
                        canOpen = notice.open != null,
                        onUndo = viewModel::undoNotice,
                        onOpen = {
                            notice.open?.let { (kind, id) -> links.open(kind, id) }
                            viewModel.dismissNotice()
                        }
                    )
                }
                if (state.recorderState == VoiceRecorderState.RESULT && state.cards.size > 1) {
                    Box(Modifier.padding(horizontal = L.Gutter, vertical = 8.dp)) {
                        LButton("Save all", onClick = viewModel::saveAll)
                    }
                }
            }
        }
    ) {
        item(key = "mic") {
            MicButton(
                recording = recording,
                busy = busy,
                amplitude = state.amplitudeLevel,
                onClick = {
                    when {
                        recording -> viewModel.stopRecording()
                        busy -> Unit
                        else -> startMic()
                    }
                }
            )
        }
        item(key = "lang") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip("English", selected = state.speechLang != "bn", onClick = { viewModel.setSpeechLanguage("en") })
                LChip("বাংলা", selected = state.speechLang == "bn", onClick = { viewModel.setSpeechLanguage("bn") })
            }
        }

        if (state.recorderState != VoiceRecorderState.IDLE) {
            item(key = "status") { StatusLine(statusWord(state), live = true) }
        }

        state.modelDownloadProgress?.let { progress ->
            item(key = "download") { DownloadProgress(progress) }
        }

        if (state.transcript.isNotBlank() && state.recorderState != VoiceRecorderState.IDLE &&
            state.recorderState != VoiceRecorderState.ERROR
        ) {
            item(key = "transcript") { TranscriptCard(state.transcript) }
        }

        if (state.recorderState == VoiceRecorderState.ERROR) {
            item(key = "error") {
                ErrorBlock(
                    type = state.errorType,
                    message = state.errorMessage ?: "Try again",
                    onRetry = {
                        viewModel.retryRecording()
                        startMic()
                    },
                    onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
        }

        if (state.recorderState == VoiceRecorderState.RESULT) {
            items(state.cards, key = { "card-${it.key}" }) { card ->
                ConfirmCard(
                    card = card,
                    onChange = { intent, edited -> viewModel.updateCard(card.key, intent, edited) },
                    onSave = { viewModel.saveCard(card.key) },
                    onDiscard = { viewModel.discardCard(card.key) }
                )
            }
        }

        if (state.recorderState == VoiceRecorderState.IDLE || state.recorderState == VoiceRecorderState.ERROR) {
            item(key = "type") {
                TypeComposer(
                    text = state.typedInput,
                    onText = viewModel::updateTypedInput,
                    onSend = viewModel::parseTypedInput,
                )
            }
        }

        item(key = "history-title") {
            LSection("History")
        }
        item(key = "history") {
            HistoryGroup(history, onTap = { sheetItemId = it.id })
        }
    }

    val sheetItem = history?.firstOrNull { it.id == sheetItemId }
    if (sheetItem != null) {
        HistoryItemSheet(
            item = sheetItem,
            onDismiss = { sheetItemId = null },
            onSave = { text, kind ->
                viewModel.updateHistory(sheetItem, text, kind)
                sheetItemId = null
            },
            onDelete = { alsoItem ->
                viewModel.deleteHistory(sheetItem, alsoItem)
                sheetItemId = null
            },
            onRedo = { text, kind, replace ->
                viewModel.redo(sheetItem, text, kind, replace)
                sheetItemId = null
                scope.launch { listState.animateScrollToItem(0) }
            },
            onVoice = {
                viewModel.recordOver(sheetItem)
                sheetItemId = null
            },
            onOpen = { kind, id -> links.open(kind, id); sheetItemId = null }
        )
    }
}

private fun statusWord(state: VoiceUiState): String = when (state.recorderState) {
    VoiceRecorderState.IDLE -> "Speak"
    VoiceRecorderState.RECORDING -> {
        val left = (state.maxSeconds - state.recordingSeconds).coerceAtLeast(0)
        "Listening  %d:%02d".format(left / 60, left % 60)
    }
    VoiceRecorderState.TRANSCRIBING ->
        if (state.modelDownloadProgress != null) "Downloading" else "Thinking"
    VoiceRecorderState.PARSING -> "Thinking"
    VoiceRecorderState.RESULT -> if (state.cards.size > 1) "${state.cards.size} items" else "Review"
    VoiceRecorderState.ERROR -> "Stopped"
}

@Composable
private fun MicButton(recording: Boolean, busy: Boolean, amplitude: Float, onClick: () -> Unit) {
    val description = if (recording) "Stop recording" else "Start recording"
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(MicArea), contentAlignment = Alignment.Center) {
            if (recording) PulseRings(amplitude)
            Box(
                Modifier
                    .size(MicSize)
                    .clip(CircleShape)
                    .background(L.Highlight)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                        if (busy) stateDescription = "Working"
                    }
                    .clickable(enabled = !busy, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = L.OnHighlight,
                    modifier = Modifier.size(48.dp).alpha(if (busy) 0.4f else 1f)
                )
            }
        }
    }
}

@Composable
private fun PulseRings(amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val level by animateFloatAsState(amplitude, tween(120), label = "level")
    listOf(0f, 0.5f).forEach { offset ->
        val f = (phase + offset) % 1f
        val scale = 1f + f * (0.5f + level * 0.15f)
        Box(
            Modifier
                .size(MicSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - f) * 0.45f
                }
                .clip(CircleShape)
                .background(L.Box)
        )
    }
}

/** Pill field under the mic. Mic fills it; send parses the words. */
@Composable
private fun TypeComposer(text: String, onText: (String) -> Unit, onSend: () -> Unit) {
    val voice = rememberVoiceInput(onText)
    val listening = voice.listening
    val canSend = text.isNotBlank()
    val sendMode = canSend && !listening
    val shape = RoundedCornerShape(L.Radius)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(L.Box)
                .border(1.dp, L.Line, shape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                if (text.isEmpty()) {
                    Text(
                        if (listening) "Listening…" else "Ask or say…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = L.InkMuted,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    cursorBrush = SolidColor(L.Accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when {
            sendMode -> ComposerAction(Icons.Filled.Send, "Send", L.Accent, L.OnAccent, onSend)
            listening -> ComposerAction(Icons.Filled.Stop, "Stop listening", L.Danger, L.OnAccent, voice.toggle)
            else -> LMicButton(onClick = voice.toggle, label = "Speak")
        }
    }
}

@Composable
private fun StatusLine(text: String, live: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = L.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                heading()
                if (live) liveRegion = LiveRegionMode.Polite
            }
    )
}

@Composable
private fun DownloadProgress(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LProgress(p, Modifier.weight(1f))
        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = L.Ink)
    }
}

@Composable
private fun TranscriptCard(text: String) {
    LCard {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = L.OnBox,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** Error text plus the one action that can fix it: Allow, Open settings or Retry. */
@Composable
private fun ErrorBlock(
    type: ErrorType,
    message: String,
    onRetry: () -> Unit,
    onAllow: () -> Unit,
    onSettings: () -> Unit
) {
    when (type) {
        ErrorType.PERMISSION -> {
            LError(message)
            LButton("Allow", onClick = onAllow)
        }
        ErrorType.PERMISSION_BLOCKED -> {
            LError(message)
            LButton("Open settings", onClick = onSettings)
        }
        else -> LError(message, onRetry = onRetry)
    }
}

/** Undo fits [LSnackbar]. Open plus Undo does not: that bar has one action slot. */
@Composable
private fun SavedBar(message: String, canOpen: Boolean, onUndo: () -> Unit, onOpen: () -> Unit) {
    val pad = Modifier
        .padding(horizontal = L.Gutter, vertical = 12.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }
    if (!canOpen) {
        LSnackbar(message, pad, action = "Undo", onAction = onUndo)
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
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = L.Ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LTextLink("Undo", onUndo)
            LTextLink("Open", onOpen)
        }
    }
}

@Composable
private fun ComposerAction(
    icon: ImageVector,
    label: String,
    fill: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(L.Mic)
            .clip(CircleShape)
            .background(fill)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
