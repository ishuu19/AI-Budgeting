package com.ledgerai.app.presentation.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val showAddSheet: Boolean = false,
    val parsedTransaction: ParsedTransaction? = null,
    val isParsingVoice: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepo.getAllTransactions().collectLatest { transactions ->
                _uiState.update { it.copy(transactions = transactions, isLoading = false) }
            }
        }
    }

    fun showAddSheet(parsed: ParsedTransaction? = null) {
        _uiState.update { it.copy(showAddSheet = true, parsedTransaction = parsed) }
    }

    fun hideAddSheet() {
        _uiState.update { it.copy(showAddSheet = false, parsedTransaction = null) }
    }

    fun parseNaturalLanguage(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsingVoice = true) }
            aiRepo.parseVoiceTransaction(input).fold(
                onSuccess = { parsed ->
                    _uiState.update { it.copy(isParsingVoice = false, parsedTransaction = parsed, showAddSheet = true) }
                },
                onFailure = {
                    _uiState.update { it.copy(isParsingVoice = false, snackbarMessage = "Could not parse transaction") }
                }
            )
        }
    }

    fun addTransaction(
        amount: Double,
        type: TransactionType,
        category: TransactionCategory,
        merchant: String,
        note: String,
        date: LocalDate
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                type = type,
                category = category,
                merchant = merchant,
                note = note,
                date = date,
                createdAt = LocalDateTime.now()
            )
            transactionRepo.insert(transaction)
            _uiState.update { it.copy(showAddSheet = false, snackbarMessage = "Transaction added") }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepo.delete(transaction)
            _uiState.update { it.copy(snackbarMessage = "Transaction deleted") }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
