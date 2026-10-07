package com.ledgerai.app.presentation.screens.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ChatMessage
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class AiChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(content = "Hi! I'm LedgerAI. Cloud chat arrives in Phase 7. Until then I can still help with local health scores and budget tips from your on-device data.", isFromUser = false)
    ),
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

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
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
                .dropLast(1) // exclude just-added user message from history param
                .takeLast(10)
                .map { (if (it.isFromUser) "user" else "assistant") to it.content }

            aiRepo.chat(message, history, context).fold(
                onSuccess = { response ->
                    val aiMsg = ChatMessage(content = response, isFromUser = false)
                    _uiState.update { it.copy(messages = it.messages + aiMsg, isTyping = false) }
                },
                onFailure = {
                    val errMsg = ChatMessage(content = "Sorry, I had trouble connecting. Please try again.", isFromUser = false)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(viewModel: AiAssistantViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Assistant") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }
                if (state.isTyping) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Card(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) {
                                        CircularProgressIndicator(modifier = Modifier.size(6.dp), strokeWidth = 1.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick suggestions
            if (state.messages.size <= 2) {
                val suggestions = listOf("How am I doing this month?", "Where am I spending the most?", "Give me a savings tip")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.take(2).forEach { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.updateInput(suggestion); viewModel.sendMessage() },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask LedgerAI anything…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = state.inputText.isNotBlank() && !state.isTyping,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send",
                        tint = if (state.inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = if (isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
                    else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
