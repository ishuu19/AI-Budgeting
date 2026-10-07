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

data class DebtsUiState(
    val activeDebts: List<Debt> = emptyList(),
    val isLoading: Boolean = true,
    val totalOwedToMe: Double = 0.0,
    val totalIOwe: Double = 0.0,
    val snackbarMessage: String? = null
)

@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debtRepo: DebtRepository,
    private val reminderScheduler: DebtReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebtsUiState())
    val uiState: StateFlow<DebtsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            debtRepo.getActiveDebts().collectLatest { debts ->
                val owedToMe = debtRepo.getTotalOwedToMe()
                val iOwe = debtRepo.getTotalIOwe()
                _uiState.update {
                    it.copy(activeDebts = debts, isLoading = false, totalOwedToMe = owedToMe, totalIOwe = iOwe)
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

    fun markAsPaid(debt: Debt) {
        viewModelScope.launch {
            debtRepo.markAsPaid(debt.id)
            reminderScheduler.cancelReminders(debt.id)
            _uiState.update { it.copy(snackbarMessage = "Marked as paid!") }
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            debtRepo.delete(debt)
            reminderScheduler.cancelReminders(debt.id)
        }
    }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
}
