package com.ledgerai.app.presentation.screens.lifelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.local.room.ActivityEntryEntity
import com.ledgerai.app.data.local.room.CheckinWindowEntity
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.domain.model.ActivityEntrySource
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
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.rememberVoiceInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LifeLogState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val windows: List<CheckinWindowEntity> = emptyList(),
    val entries: List<ActivityEntryEntity> = emptyList(),
    val streak: Int = 0,
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
                .collect { (w, e) ->
                    _state.update { it.copy(loading = false, windows = w, entries = e, streak = streakEnding(today)) }
                }
        }
    }

    fun answer(window: CheckinWindowEntity, text: String, entryId: Long?, voice: Boolean) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.answerWindow(window.id, text.trim(), window.startAt, window.endAt, entryId, voice)
        }
    }

    /** Consecutive days, ending today or yesterday, where every window was answered. */
    private suspend fun streakEnding(today: LocalDate): Int {
        suspend fun logged(day: LocalDate): Boolean {
            val windows = repo.observeDay(day).first()
            return windows.isNotEmpty() && windows.all { it.state == CheckinWindowState.ANSWERED }
        }
        var day = if (logged(today)) today else today.minusDays(1)
        var n = 0
        repeat(7) {
            if (!logged(day)) return n
            n++
            day = day.minusDays(1)
        }
        return n
    }
}

@Composable
fun LifeLogScreen(viewModel: LifeLogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var answerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var fromVoice by rememberSaveable { mutableStateOf(false) }

    LScreen(title = "Log") {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = viewModel::load) }
            state.windows.isEmpty() -> item { LEmpty(Icons.Default.Timeline, "Nothing logged") }
            else -> {
                val answered = state.windows.count { it.state == CheckinWindowState.ANSWERED }
                item(key = "hero") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LHero(
                            label = "Today",
                            value = "$answered of ${state.windows.size}",
                            sub = if (state.streak > 0) "${state.streak}-day streak" else null
                        )
                        DayRibbon(state.windows, answered, state.windows.size)
                    }
                }
                item(key = "timeline") {
                    LGroup {
                        state.windows.forEachIndexed { i, w ->
                            if (i > 0) LGroupDivider()
                            val entry = state.entries.firstOrNull {
                                !it.startAt.isBefore(w.startAt) && it.startAt.isBefore(w.endAt)
                            }
                            LGroupRow(
                                title = "${w.startAt.format(fmt)} – ${w.endAt.format(fmt)}",
                                sub = entry?.text?.takeIf { it.isNotBlank() },
                                onClick = {
                                    answerId = w.id
                                    entryId = entry?.id?.takeIf { it > 0L }
                                    text = entry?.text.orEmpty()
                                    fromVoice = entry?.source == ActivityEntrySource.VOICE
                                },
                                end = { StatusDot(w.state) }
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
            val voice = rememberVoiceInput { spoken ->
                text = spoken
                fromVoice = true
            }
            LaunchedEffect(window.id) { voice.toggle() }
            LSheet(
                title = "${window.startAt.format(fmt)} – ${window.endAt.format(fmt)}",
                onDismiss = { answerId = null },
                primary = "Save",
                onPrimary = {
                    viewModel.answer(window, text, entryId, fromVoice)
                    answerId = null
                },
                primaryEnabled = text.isNotBlank()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LToolButton(
                        Icons.Filled.Mic,
                        if (voice.listening) "Stop" else "Speak",
                        voice.toggle,
                        active = voice.listening
                    )
                    LField(
                        text,
                        { text = it; fromVoice = false },
                        "What happened",
                        Modifier.weight(1f),
                        singleLine = false,
                        minLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun DayRibbon(windows: List<CheckinWindowEntity>, answered: Int, total: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(L.Radius))
            .semantics { contentDescription = "$answered of $total answered" }
    ) {
        for (hour in 0..23) {
            val state = windows.firstOrNull { hour >= it.startAt.hour && hour < it.endAt.hour }?.state
            Box(Modifier.weight(1f).fillMaxHeight().background(ribbonColor(state)))
        }
    }
}

private fun ribbonColor(state: CheckinWindowState?): Color = when (state) {
    CheckinWindowState.ANSWERED -> L.Gold
    CheckinWindowState.PENDING -> L.Highlight
    CheckinWindowState.GAP -> L.Danger
    null -> L.BoxDeep
}

@Composable
private fun StatusDot(state: CheckinWindowState) {
    val label = when (state) {
        CheckinWindowState.ANSWERED -> "Answered"
        CheckinWindowState.PENDING -> "Pending"
        CheckinWindowState.GAP -> "Gap"
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(ribbonColor(state))
            .semantics { contentDescription = label }
    )
}
