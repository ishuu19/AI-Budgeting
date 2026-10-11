package com.ledgerai.app.presentation.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.AskService
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ChatMessage
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LHeader
import com.ledgerai.app.presentation.components.LMicButton
import com.ledgerai.app.presentation.components.LocalEmbedded
import com.ledgerai.app.presentation.components.rememberVoiceInput
import com.ledgerai.app.presentation.components.money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = "",
    /** The last request failed. The failure is shown, never stored as a message or sent back to the model. */
    val failed: Boolean = false
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val askService: AskService,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()
    private var seededInsight = false

    fun seedInsight(insight: String) {
        if (seededInsight || insight.isBlank()) return
        seededInsight = true
        _uiState.update {
            it.copy(
                inputText = "About this insight: $insight",
                messages = it.messages + ChatMessage(
                    content = "You tapped a dashboard insight:\n\n$insight\n\nAsk me anything about it.",
                    isFromUser = false
                )
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun seedFromInsight(insight: String) {
        val trimmed = insight.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.messages.any { it.isFromUser && it.content == trimmed }) return
        _uiState.update { it.copy(inputText = trimmed) }
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val message = state.inputText.trim()
        if (message.isBlank() || state.isTyping) return

        val userMsg = ChatMessage(content = message, isFromUser = true)
        _uiState.update {
            // A message that got no answer is dropped, so it never reaches the model as history.
            val kept = if (it.failed) it.messages.dropLast(1) else it.messages
            it.copy(messages = kept + userMsg, inputText = "", isTyping = true, failed = false)
        }
        request(message)
    }

    /** Sends the unanswered message again. */
    fun retry() {
        val state = _uiState.value
        if (!state.failed || state.isTyping) return
        val last = state.messages.lastOrNull { it.isFromUser } ?: return
        _uiState.update { it.copy(isTyping = true, failed = false) }
        request(last.content)
    }

    private fun request(message: String) {
        viewModelScope.launch {
            val context = buildFinancialContext()
            val history = _uiState.value.messages
                .dropLast(1)
                .takeLast(10)
                .map { (if (it.isFromUser) "user" else "assistant") to it.content }

            askService.ask(message, history, context).fold(
                onSuccess = { response ->
                    val aiMsg = ChatMessage(
                        content = response.text,
                        isFromUser = false,
                        source = response.source,
                        link = response.link
                    )
                    _uiState.update { it.copy(messages = it.messages + aiMsg, isTyping = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(isTyping = false, failed = true) }
                }
            )
        }
    }

    private suspend fun buildFinancialContext(): String {
        return try {
            val now = LocalDate.now()
            val transactions = transactionRepo.getRecentTransactions(20).first()
            val income = transactionRepo.getTotalIncomeForMonth(now.year, now.monthValue)
            val expenses = transactionRepo.getTotalExpensesForMonth(now.year, now.monthValue)
            val net = income - expenses
            """
                Current month: ${now.month} ${now.year}
                Monthly income: ${money(income)}
                Monthly expenses: ${money(expenses)}
                Net balance: ${money(net)}
                Recent transactions: ${transactions.take(5).joinToString(", ") {
                    "${it.category.displayName} ${money(it.amount)}"
                }}
            """.trimIndent()
        } catch (e: Exception) { "" }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val Suggestions = listOf(
    "This month" to "How am I doing this month?",
    "Top spend" to "Where is my money going?",
    "Save more" to "How can I save more?"
)

@Composable
fun AiAssistantScreen(
    initialInsight: String = "",
    onBack: () -> Unit = {},
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val embedded = LocalEmbedded.current
    val itemCount = state.messages.size + (if (state.isTyping) 1 else 0) + (if (state.failed) 1 else 0)

    LaunchedEffect(initialInsight) {
        if (initialInsight.isNotBlank()) viewModel.seedFromInsight(initialInsight)
    }

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(containerColor = L.Page) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            // Inside the Voice tab the tab header already names this screen.
            if (!embedded) {
                LHeader(
                    title = "Chat",
                    modifier = Modifier.padding(horizontal = L.Gutter, vertical = 8.dp),
                    onBack = onBack,
                )
            }

            if (itemCount == 0) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = L.Gutter),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SuggestionChips(onPick = { prompt ->
                        viewModel.updateInput(prompt)
                        viewModel.sendMessage()
                    })
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = L.Gutter, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.messages) { _, message -> Bubble(message) }
                    if (state.isTyping) item { TypingBubble() }
                    if (state.failed) item { LError("Could not connect", onRetry = viewModel::retry) }
                }
            }

            AskComposer(
                text = state.inputText,
                onTextChange = viewModel::updateInput,
                canSend = state.inputText.isNotBlank() && !state.isTyping,
                onSend = viewModel::sendMessage
            )
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val isUser = message.isFromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    if (isUser) RoundedCornerShape(L.Radius, 4.dp, L.Radius, L.Radius)
                    else RoundedCornerShape(4.dp, L.Radius, L.Radius, L.Radius)
                )
                .background(if (isUser) L.Gold else L.Box)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) L.OnAccent else L.OnBox
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp, L.Radius, L.Radius, L.Radius))
                .background(L.Box)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).semantics { contentDescription = "Thinking" },
                color = L.Primary,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun SuggestionChips(onPick: (String) -> Unit) {
    ChipsRow {
        Suggestions.forEach { (label, prompt) ->
            LChip(label, selected = false, onClick = { onPick(prompt) })
        }
    }
}

/** Pill composer: mic fills the field, send when there is text. */
@Composable
private fun AskComposer(text: String, onTextChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
    val voice = rememberVoiceInput(onTextChange)
    val listening = voice.listening
    val sendMode = canSend && !listening
    val shape = RoundedCornerShape(L.Radius)
    Row(
        Modifier
            .fillMaxWidth()
            .background(L.Page)
            .padding(horizontal = L.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(L.Box)
                .border(1.dp, L.Line, shape)
                .padding(start = 16.dp, end = 4.dp),
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
                    onValueChange = onTextChange,
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
            sendMode -> ComposerAction(Icons.AutoMirrored.Filled.Send, "Send", L.Accent, L.OnAccent, onSend)
            listening -> ComposerAction(Icons.Filled.Stop, "Stop listening", L.Danger, L.OnAccent, voice.toggle)
            else -> LMicButton(onClick = voice.toggle, label = "Speak")
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
