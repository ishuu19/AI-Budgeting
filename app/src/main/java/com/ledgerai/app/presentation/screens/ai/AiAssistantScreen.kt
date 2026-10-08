package com.ledgerai.app.presentation.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ChatMessage
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val inputText: String = ""
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiRepo: AiRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()
    private val now = LocalDate.now()
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
        val message = _uiState.value.inputText.trim()
        if (message.isBlank()) return

        val userMsg = ChatMessage(content = message, isFromUser = true)
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            inputText = "",
            isTyping = true
        ) }

        viewModelScope.launch {
            val context = buildFinancialContext()
            val history = _uiState.value.messages
                .dropLast(1)
                .takeLast(10)
                .map { (if (it.isFromUser) "user" else "assistant") to it.content }

            aiRepo.chat(message, history, context).fold(
                onSuccess = { response ->
                    val aiMsg = ChatMessage(content = response, isFromUser = false)
                    _uiState.update { it.copy(messages = it.messages + aiMsg, isTyping = false) }
                },
                onFailure = {
                    val errMsg = ChatMessage(content = "Couldn't connect. Try again.", isFromUser = false)
                    _uiState.update { it.copy(messages = it.messages + errMsg, isTyping = false) }
                }
            )
        }
    }

    private suspend fun buildFinancialContext(): String {
        return try {
            val transactions = transactionRepo.getRecentTransactions(20).first()
            val income = transactionRepo.getTotalIncomeForMonth(now.year, now.monthValue)
            val expenses = transactionRepo.getTotalExpensesForMonth(now.year, now.monthValue)
            val net = income - expenses
            """
                Current month: ${now.month} ${now.year}
                Monthly income: $${income}
                Monthly expenses: $${expenses}
                Net balance: $${net}
                Recent transactions: ${transactions.take(5).joinToString(", ") { 
                    "${it.category.displayName} $${it.amount}" 
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    initialInsight: String = "",
    onBack: () -> Unit = {},
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.isTyping) 1 else 0

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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = L.Gutter, end = L.Gutter, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = L.Ink)
                }
                Text(
                    "Ask",
                    style = MaterialTheme.typography.headlineMedium,
                    color = L.Ink,
                    modifier = Modifier.weight(1f)
                )
            }

            if (itemCount == 0) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LEmpty(Icons.Filled.AutoAwesome, "Ask anything")
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
                }
            }

            if (state.messages.isEmpty() && !state.isTyping) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = L.Gutter, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Suggestions.forEach { (label, prompt) ->
                        LChip(label, selected = false, onClick = {
                            viewModel.updateInput(prompt)
                            viewModel.sendMessage()
                        })
                    }
                }
            }

            InputBar(
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
        Text(
            message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) L.BoxDeep else L.OnBox,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    if (isUser) RoundedCornerShape(L.Radius, 4.dp, L.Radius, L.Radius)
                    else RoundedCornerShape(4.dp, L.Radius, L.Radius, L.Radius)
                )
                .background(if (isUser) L.Gold else L.Box)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp, L.Radius, L.Radius, L.Radius))
                .background(L.Box)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = L.Gold, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun InputBar(text: String, onTextChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
    HorizontalDivider(color = L.Line)
    Row(
        Modifier
            .fillMaxWidth()
            .background(L.Page)
            .padding(horizontal = L.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Ask", color = L.InkMuted) },
            maxLines = 4,
            shape = RoundedCornerShape(L.RadiusSm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = L.Box,
                unfocusedBorderColor = L.Line,
                cursorColor = L.Box,
                focusedTextColor = L.Ink,
                unfocusedTextColor = L.Ink
            ),
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) L.Gold else L.Gold.copy(alpha = 0.4f))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = L.BoxDeep,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
