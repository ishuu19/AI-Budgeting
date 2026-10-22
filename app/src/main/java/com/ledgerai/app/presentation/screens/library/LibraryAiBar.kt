package com.ledgerai.app.presentation.screens.library

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.assistant.LibraryAssistant
import com.ledgerai.app.domain.assistant.AssistantArea
import com.ledgerai.app.domain.assistant.AssistantTurn
import com.ledgerai.app.presentation.components.AssistantTurnCard
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LTextLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryAiUi(
    val busy: Boolean = false,
    val turn: AssistantTurn? = null,
    val error: String? = null,
    /** Goes up after every send, confirm or undo, so a screen that loads once can reload. */
    val changes: Int = 0,
)

@HiltViewModel
class LibraryAiViewModel @Inject constructor(
    private val assistant: LibraryAssistant,
) : ViewModel() {
    private val _ui = MutableStateFlow(LibraryAiUi())
    val ui: StateFlow<LibraryAiUi> = _ui.asStateFlow()

    fun send(text: String, areas: Set<AssistantArea>) {
        if (_ui.value.busy || text.isBlank()) return
        _ui.update { it.copy(busy = true, error = null, turn = null) }
        viewModelScope.launch {
            assistant.ask(text, areas).fold(
                onSuccess = { turn -> _ui.update { it.copy(busy = false, turn = turn, changes = it.changes + 1) } },
                onFailure = { e ->
                    val message = (e as? com.ledgerai.app.data.assistant.AssistantUserError)?.message
                    _ui.update { it.copy(busy = false, error = message ?: "I couldn't reach the AI. Try again when you're online.") }
                },
            )
        }
    }

    fun approve(index: Int) = change { assistant.approve(it, index) }
    fun skip(index: Int) = change { assistant.skip(it, index) }
    fun undo(index: Int) = change { assistant.undo(it, index) }
    fun dismiss() = _ui.update { it.copy(turn = null, error = null) }

    private fun change(next: suspend (AssistantTurn) -> AssistantTurn) {
        val turn = _ui.value.turn ?: return
        viewModelScope.launch {
            val updated = next(turn)
            _ui.update { it.copy(turn = updated, changes = it.changes + 1) }
        }
    }
}

/**
 * Type a sentence and the AI files it in this section: add, change or remove records, or answer a
 * question about what is here. Small changes apply at once with Undo; edits, removals and anything
 * about money wait for a tap. [areas] limits what the AI can read and change on this screen.
 */
@Composable
fun LibraryAiBar(
    areas: Set<AssistantArea>,
    hint: String,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit = {},
    vm: LibraryAiViewModel = hiltViewModel(key = areas.joinToString(",") { it.key }),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && !ui.busy
    val send = { if (canSend) { vm.send(text.trim(), areas); text = "" } }
    LaunchedEffect(ui.changes) { if (ui.changes > 0) onChanged() }

    val shape = RoundedCornerShape(L.Radius)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(L.Box)
                .border(1.dp, L.Line, shape)
                .padding(start = L.Space.x16, end = L.Space.x8, top = L.Space.x8, bottom = L.Space.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = L.Space.x8)) {
                if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted, maxLines = 1)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    cursorBrush = SolidColor(L.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .size(L.Target)
                    .clip(CircleShape)
                    .background(if (canSend) L.Accent else L.Line)
                    .semantics { role = Role.Button; contentDescription = "Send to AI" }
                    .clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = L.OnAccent, modifier = Modifier.size(24.dp))
            }
        }
        if (ui.busy) Text("Working on it...", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        ui.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
        ui.turn?.let { turn ->
            AssistantTurnCard(turn, onApprove = vm::approve, onSkip = vm::skip, onUndo = vm::undo)
            LTextLink("Dismiss", vm::dismiss)
        }
    }
}

/** The bar for one Library section: [area] sets what it can change, and the hint shows an example. */
@Composable
fun LibraryAiBar(area: AssistantArea, onChanged: () -> Unit = {}, extra: Set<AssistantArea> = emptySet()) =
    LibraryAiBar(extra + area, libraryHint(area), onChanged = onChanged)

/** Example line shown in each section's AI bar. */
fun libraryHint(area: AssistantArea): String = when (area) {
    AssistantArea.PERSON -> "Tell AI: Ana is my dentist, she likes jazz"
    AssistantArea.JOB -> "Tell AI: applied to Acme as designer, follow up Friday"
    AssistantArea.PANTRY -> "Tell AI: 2 liters of milk in the fridge"
    AssistantArea.GARMENT -> "Tell AI: blue denim jacket, spring and autumn"
    AssistantArea.BIRTHDAY -> "Tell AI: Sam's birthday is 14 March"
    AssistantArea.TRIP -> "Tell AI: Lisbon from 3 to 9 June"
    AssistantArea.ACHIEVEMENT -> "Tell AI: finished my first half marathon today"
    AssistantArea.WISH -> "Tell AI: I want a mechanical keyboard, around 120"
    AssistantArea.NOTE -> "Tell AI: note: gift ideas for mom"
    else -> "Tell AI what to add, change or find"
}
