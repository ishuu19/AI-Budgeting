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
import java.time.YearMonth
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val showAddSheet: Boolean = false,
    val editingTransaction: Transaction? = null,
    val parsedTransaction: ParsedTransaction? = null,
    val copyDraft: Transaction? = null,
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
        _uiState.update {
            it.copy(showAddSheet = true, parsedTransaction = parsed, copyDraft = null)
        }
    }

    fun hideAddSheet() {
        _uiState.update { it.copy(showAddSheet = false, parsedTransaction = null, copyDraft = null) }
    }

    fun showEditSheet(transaction: Transaction) {
        _uiState.update { it.copy(editingTransaction = transaction) }
    }

    fun hideEditSheet() {
        _uiState.update { it.copy(editingTransaction = null) }
    }

    fun copyToNew(
        amount: Double,
        type: TransactionType,
        category: TransactionCategory,
        merchant: String,
        note: String,
        location: String = "",
        isRecurring: Boolean = false
    ) {
        val draft = SpendQuery.duplicateToday(
            Transaction(
                amount = amount,
                type = type,
                category = category,
                merchant = merchant,
                note = note,
                location = location,
                isRecurring = isRecurring
            )
        )
        _uiState.update {
            it.copy(
                editingTransaction = null,
                showAddSheet = true,
                copyDraft = draft,
                parsedTransaction = null
            )
        }
    }

    fun parseNaturalLanguage(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsingVoice = true) }
            aiRepo.parseVoiceTransaction(input).fold(
                onSuccess = { parsed ->
                    _uiState.update {
                        it.copy(
                            isParsingVoice = false,
                            parsedTransaction = parsed,
                            showAddSheet = true,
                            copyDraft = null,
                            editingTransaction = null
                        )
                    }
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
        date: LocalDate,
        location: String = "",
        isRecurring: Boolean = false
    ) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                type = type,
                category = category,
                merchant = merchant,
                note = note,
                date = date,
                location = location,
                isRecurring = isRecurring,
                createdAt = LocalDateTime.now()
            )
            transactionRepo.insert(transaction)
            _uiState.update {
                it.copy(showAddSheet = false, copyDraft = null, parsedTransaction = null, snackbarMessage = "Transaction added")
            }
        }
    }

    fun updateTransaction(
        existing: Transaction,
        amount: Double,
        type: TransactionType,
        category: TransactionCategory,
        merchant: String,
        note: String,
        date: LocalDate,
        location: String = "",
        isRecurring: Boolean = false
    ) {
        viewModelScope.launch {
            transactionRepo.update(
                existing.copy(
                    amount = amount,
                    type = type,
                    category = category,
                    merchant = merchant,
                    note = note,
                    date = date,
                    location = location,
                    isRecurring = isRecurring
                )
            )
            _uiState.update {
                it.copy(editingTransaction = null, snackbarMessage = "Transaction updated")
            }
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

object SpendQuery {
    fun inMonth(transactions: List<Transaction>, month: YearMonth): List<Transaction> =
        transactions.filter { YearMonth.from(it.date) == month }

    fun monthNet(transactions: List<Transaction>): Double = transactions.fold(0.0) { acc, tx ->
        acc + if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
    }

    fun matchesSearch(tx: Transaction, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOf(tx.merchant, tx.note, tx.category.displayName)
            .any { it.contains(q, ignoreCase = true) }
    }

    fun filter(
        transactions: List<Transaction>,
        month: YearMonth,
        type: TransactionType?,
        category: TransactionCategory?,
        query: String
    ): List<Transaction> = inMonth(transactions, month).filter { tx ->
        val typeOk = type == null || tx.type == type
        val categoryOk = category == null || tx.category == category
        typeOk && categoryOk && matchesSearch(tx, query)
    }

    fun categoriesIn(transactions: List<Transaction>): List<TransactionCategory> =
        transactions.map { it.category }.distinct()

    fun duplicateToday(source: Transaction, today: LocalDate = LocalDate.now()): Transaction =
        source.copy(id = 0, remoteId = null, date = today)
}
