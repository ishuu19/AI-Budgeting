package com.ledgerai.app.presentation.screens.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.worker.DebtReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class DebtFilter(val label: String) {
    ALL("All"),
    I_OWE("I owe"),
    THEY_OWE("They owe"),
    OVERDUE("Overdue")
}

data class DebtsUiState(
    val activeDebts: List<Debt> = emptyList(),
    val settledDebts: List<Debt> = emptyList(),
    val isLoading: Boolean = true,
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val snackbarMessage: String? = null
) {
    val netPosition: Double get() = totalOwedToMe - totalIOwe
}

fun Debt.isOverdue(today: LocalDate = LocalDate.now()): Boolean =
    !isPaid && dueDate?.isBefore(today) == true

fun Debt.matches(filter: DebtFilter, today: LocalDate = LocalDate.now()): Boolean {
    if (isPaid) return false
    return when (filter) {
        DebtFilter.ALL -> true
        DebtFilter.I_OWE -> direction == DebtDirection.I_OWE
        DebtFilter.THEY_OWE -> direction == DebtDirection.THEY_OWE
        DebtFilter.OVERDUE -> isOverdue(today)
    }
}

/** Remaining principal after a payment. <= 0 means the debt is settled. */
fun remainingAfterPayment(amount: Double, payment: Double): Double = amount - payment

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepo: DebtRepository,
    private val reminderScheduler: DebtReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebtsUiState())
    val uiState: StateFlow<DebtsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            debtRepo.getAllDebts().collectLatest { debts ->
                val active = debts.filter { !it.isPaid }
                val settled = debts.filter { it.isPaid }
                val owedToMe = active.filter { it.direction == DebtDirection.THEY_OWE }.sumOf { it.amount }
                val iOwe = active.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.amount }
                _uiState.update {
                    it.copy(
                        activeDebts = active,
                        settledDebts = settled,
                        isLoading = false,
                        totalOwedToMe = owedToMe,
                        totalIOwe = iOwe
                    )
                }
            }
        }
    }

    fun addDebt(
        friendName: String,
        amount: Double,
        direction: DebtDirection,
        dueDate: LocalDate?,
        phone: String,
        email: String,
        note: String
    ) {
        viewModelScope.launch {
            val debt = Debt(
                friendName = friendName,
                amount = amount,
                direction = direction,
                dueDate = dueDate,
                phone = phone,
                email = email,
                note = note
            )
            val id = debtRepo.insert(debt)

            if (dueDate != null) {
                reminderScheduler.scheduleReminders(id, friendName, amount, dueDate)
            }

            _uiState.update { it.copy(snackbarMessage = "Debt added for $friendName") }
        }
    }

    fun updateDebt(
        debt: Debt,
        friendName: String,
        amount: Double,
        direction: DebtDirection,
        dueDate: LocalDate?,
        phone: String,
        email: String,
        note: String
    ) {
        viewModelScope.launch {
            debtRepo.update(
                debt.copy(
                    friendName = friendName,
                    amount = amount,
                    direction = direction,
                    dueDate = dueDate,
                    phone = phone,
                    email = email,
                    note = note
                )
            )
            reminderScheduler.cancelReminders(debt.id)
            if (dueDate != null && !debt.isPaid) {
                reminderScheduler.scheduleReminders(debt.id, friendName, amount, dueDate)
            }
            _uiState.update { it.copy(snackbarMessage = "Debt updated") }
        }
    }

    fun recordPayment(debt: Debt, payment: Double) {
        if (debt.isPaid || payment <= 0.0) return
        val remaining = remainingAfterPayment(debt.amount, payment)
        if (remaining <= 0.0) {
            markAsPaid(debt)
            return
        }
        viewModelScope.launch {
            debtRepo.update(debt.copy(amount = remaining))
            reminderScheduler.cancelReminders(debt.id)
            if (debt.dueDate != null) {
                reminderScheduler.scheduleReminders(debt.id, debt.friendName, remaining, debt.dueDate)
            }
            _uiState.update { it.copy(snackbarMessage = "Payment recorded") }
        }
    }

    fun markAsPaid(debt: Debt) {
        viewModelScope.launch {
            debtRepo.markAsPaid(debt.id)
            reminderScheduler.cancelReminders(debt.id)
            _uiState.update { it.copy(snackbarMessage = "Settled") }
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.delete(debt)
            reminderScheduler.cancelReminders(debt.id)
            _uiState.update { it.copy(snackbarMessage = "Debt deleted") }
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
