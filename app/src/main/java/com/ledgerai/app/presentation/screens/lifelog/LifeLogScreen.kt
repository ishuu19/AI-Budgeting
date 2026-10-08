package com.ledgerai.app.presentation.screens.lifelog

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.service.VoiceRecordingService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.local.room.ActivityEntryEntity
import com.ledgerai.app.data.local.room.CheckinWindowEntity
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LifeLogState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val windows: List<CheckinWindowEntity> = emptyList(),
    val entries: List<ActivityEntryEntity> = emptyList()
)

@HiltViewModel
class LifeLogViewModel @Inject constructor(
    private val repo: LifeLogRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LifeLogState())
    val state: StateFlow<LifeLogState> = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = false) }
        job = viewModelScope.launch {
            val today = LocalDate.now()
            runCatching {
                repo.ensureWindowsForDay(today)
                repo.markExpiredGaps()
            }.onFailure { _state.update { s -> s.copy(loading = false, error = true) }; return@launch }
            combine(repo.observeDay(today), repo.observeEntries(today)) { w, e -> w to e }
                .catch { _state.update { s -> s.copy(loading = false, error = true) } }
                .collect { (w, e) -> _state.update { it.copy(loading = false, windows = w, entries = e) } }
        }
    }

    fun answer(window: CheckinWindowEntity, text: String, entryId: Long?, voice: Boolean) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.answerWindow(window.id, text.trim(), window.startAt, window.endAt, entryId, voice)
        }
    }
}

@Composable
fun LifeLogScreen(viewModel: LifeLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var answerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var fromVoice by rememberSaveable { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    fun startVoice() {
        listening = true
        ContextCompat.startForegroundService(
            context,
            Intent(context, VoiceRecordingService::class.java).apply {
                action = VoiceRecordingService.ACTION_START_RECORDING
                putExtra(VoiceRecordingService.EXTRA_PROMPT, "Say what happened")
            }
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startVoice() }

    DisposableEffect(listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val spoken = intent?.getStringExtra(VoiceRecordingService.EXTRA_TRANSCRIPTION)?.trim().orEmpty()
                if (spoken.isNotEmpty()) {
                    text = spoken
                    fromVoice = true
                }
                listening = false
            }
        }
        val filter = IntentFilter(VoiceRecordingService.ACTION_TRANSCRIPTION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LScreen(title = "Log") {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = viewModel::load) }
            state.windows.isEmpty() -> item { LEmpty(Icons.Default.Timeline, "Nothing logged") }
            else -> {
                val answered = state.windows.count { it.state == CheckinWindowState.ANSWERED }
                val gaps = state.windows.count { it.state == CheckinWindowState.GAP }
                item(key = "hero") {
                    LHero(
                        label = "Today",
                        value = "$answered of ${state.windows.size}",
                        sub = if (gaps > 0) "$gaps gaps" else "No gaps"
                    )
                }
                item(key = "timeline") {
                    LGroup {
                        state.windows.forEachIndexed { i, w ->
                            if (i > 0) LGroupDivider()
                            val entry = state.entries.firstOrNull {
                                !it.startAt.isBefore(w.startAt) && it.startAt.isBefore(w.endAt)
                            }
                            val gap = w.state == CheckinWindowState.GAP
                            LGroupRow(
                                title = "${w.startAt.format(fmt)} – ${w.endAt.format(fmt)}",
                                sub = when {
                                    entry != null -> entry.text
                                    gap -> "Gap"
                                    w.state == CheckinWindowState.PENDING -> "Pending"
                                    else -> "Answered"
                                },
                                trailing = if (gap) "Gap" else null,
                                trailingColor = L.Danger,
                                onClick = {
                                    answerId = w.id
                                    entryId = entry?.id?.takeIf { it > 0L }
                                    text = entry?.text.orEmpty()
                                    fromVoice = entry?.source == com.ledgerai.app.domain.model.ActivityEntrySource.VOICE
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    answerId?.let { id ->
        val window = state.windows.firstOrNull { it.id == id }
        if (window == null) {
            if (!state.loading) LaunchedEffect(id) { answerId = null }
        } else {
            LSheet(
                title = "${window.startAt.format(fmt)} – ${window.endAt.format(fmt)}",
                onDismiss = { answerId = null; listening = false },
                primary = "Save",
                onPrimary = {
                    viewModel.answer(window, text, entryId, fromVoice)
                    answerId = null
                    listening = false
                },
                primaryEnabled = text.isNotBlank(),
                secondary = if (listening) "Listening…" else "Voice",
                onSecondary = {
                    if (listening) return@LSheet
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                LField(text, { text = it; fromVoice = false }, "What happened", singleLine = false, minLines = 2)
            }
        }
    }
}
