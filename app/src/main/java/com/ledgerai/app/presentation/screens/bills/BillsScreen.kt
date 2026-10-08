package com.ledgerai.app.presentation.screens.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.transactions.CategoryChipsRow
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.presentation.screens.transactions.amountInput
import com.ledgerai.app.presentation.screens.transactions.shortDate
import com.ledgerai.app.presentation.screens.transactions.spendIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    /** Unpaused bills first, soonest due first, then paused bills. */
    val bills: StateFlow<List<Bill>> = billRepo.getAllBills()
        .map { list -> list.sortedWith(compareByDescending<Bill> { it.isActive }.thenBy { it.nextDueDate }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Monthly cost of all active bills, whatever their frequency. Updates with the list. */
    val monthlyTotal: StateFlow<Double> = bills
        .map { list -> list.filter { it.isActive }.sumOf { monthlyCost(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** "id:dueDate" of cycles already being paid or paid. A cycle can be paid once, so a double tap is ignored. */
    private val payingCycles = mutableSetOf<String>()

    fun clearMessage() = _message.update { null }

    fun addBill(name: String, amount: Double, frequency: BillFrequency, nextDueDate: LocalDate, category: TransactionCategory) {
        viewModelScope.launch {
            billRepo.insert(
                Bill(name = name, amount = amount, frequency = frequency, nextDueDate = nextDueDate, category = category)
            )
            _message.update { "Bill added" }
        }
    }

    fun updateBill(
        bill: Bill,
        name: String,
        amount: Double,
        frequency: BillFrequency,
        nextDueDate: LocalDate,
        category: TransactionCategory
    ) {
        viewModelScope.launch {
            billRepo.update(
                bill.copy(name = name, amount = amount, frequency = frequency, nextDueDate = nextDueDate, category = category)
            )
            _message.update { "Bill updated" }
        }
    }

    /** Marks the current cycle paid: expense in Spend, then roll next due. Ignores repeat taps for the same cycle. */
    fun markPaid(bill: Bill) {
        if (!bill.isActive) return
        val current = bills.value.firstOrNull { it.id == bill.id }
        if (current != null && current.nextDueDate != bill.nextDueDate) return
        val cycle = "${bill.id}:${bill.nextDueDate}"
        if (!payingCycles.add(cycle)) return
        viewModelScope.launch {
            try {
                billRepo.update(bill.copy(nextDueDate = rollDueDate(bill.nextDueDate, bill.frequency)))
                transactionRepo.insert(
                    Transaction(
                        amount = bill.amount,
                        type = TransactionType.EXPENSE,
                        category = bill.category,
                        merchant = bill.name,
                        date = LocalDate.now()
                    )
                )
                _message.update { "${bill.name} paid" }
            } catch (e: Exception) {
                payingCycles.remove(cycle)
                _message.update { "Could not record payment" }
            }
        }
    }

    /** Advances one period without recording a payment. */
    fun skip(bill: Bill) {
        val cycle = "${bill.id}:${bill.nextDueDate}"
        if (!payingCycles.add(cycle)) return
        viewModelScope.launch {
            billRepo.update(bill.copy(nextDueDate = rollDueDate(bill.nextDueDate, bill.frequency)))
            _message.update { "Skipped" }
        }
    }

    fun setActive(bill: Bill, active: Boolean) {
        viewModelScope.launch {
            billRepo.update(bill.copy(isActive = active))
            _message.update { if (active) "Resumed" else "Paused" }
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            billRepo.delete(bill)
            _message.update { "Bill deleted" }
        }
    }
}

private fun monthlyCost(bill: Bill): Double = when (bill.frequency) {
    BillFrequency.WEEKLY -> bill.amount * 52.0 / 12.0
    BillFrequency.MONTHLY -> bill.amount
    BillFrequency.QUARTERLY -> bill.amount / 3.0
    BillFrequency.YEARLY -> bill.amount / 12.0
}

private fun rollDueDate(from: LocalDate, frequency: BillFrequency): LocalDate = when (frequency) {
    BillFrequency.WEEKLY -> from.plusWeeks(1)
    BillFrequency.MONTHLY -> from.plusMonths(1)
    BillFrequency.QUARTERLY -> from.plusMonths(3)
    BillFrequency.YEARLY -> from.plusYears(1)
}

/** Bills section for the Money Owed segment. */
fun LazyListScope.billItems(
    bills: List<Bill>,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Bill) -> Unit,
    onPaid: (Bill) -> Unit
) {
    item(key = "bills-header") { LSection("Bills", action = "Add", onAction = onAdd) }
    if (bills.isEmpty()) {
        item(key = "bills-empty") { LEmpty(Icons.Filled.EventRepeat, "No bills") }
    } else {
        item(key = "bills-group") {
            LimitedGroup(bills, id = { it.id }, expandKey = "bills") { bill ->
                BillRow(bill, today, onClick = { onEdit(bill) }, onPaid = { onPaid(bill) })
            }
        }
    }
}

@Composable
private fun BillRow(bill: Bill, today: LocalDate, onClick: () -> Unit, onPaid: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, bill.nextDueDate)
    val late = bill.isActive && days < 0
    LGroupRow(
        title = bill.name,
        sub = (if (bill.isActive) dueLabel(days, bill.nextDueDate) else "Paused") + " · " + bill.frequency.displayName,
        trailing = money(bill.amount),
        trailingColor = if (late) L.Danger else L.Gold,
        icon = spendIcon(bill.category),
        onClick = onClick,
        end = if (bill.isActive) {
            {
                IconButton(onClick = onPaid) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark ${bill.name} paid", tint = L.Gold)
                }
            }
        } else null
    )
}

private fun dueLabel(days: Long, date: LocalDate): String = when {
    days < 0 -> "Overdue ${-days}d"
    days == 0L -> "Today"
    days == 1L -> "Tomorrow"
    days <= 7 -> "In $days days"
    else -> shortDate(date)
}

@Composable
fun BillSheet(
    existing: Bill?,
    onDismiss: () -> Unit,
    onSave: (String, Double, BillFrequency, LocalDate, TransactionCategory) -> Unit,
    onPaid: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onToggleActive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var amountText by rememberSaveable { mutableStateOf(existing?.amount?.let(::amountInput) ?: "") }
    var frequency by rememberSaveable { mutableStateOf(existing?.frequency ?: BillFrequency.MONTHLY) }
    var dueDate by rememberSaveable { mutableStateOf(existing?.nextDueDate ?: LocalDate.now().plusMonths(1)) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: TransactionCategory.SUBSCRIPTIONS) }
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val canSave = name.isNotBlank() && amount != null
    val save = { amount?.let { onSave(name.trim(), it, frequency, dueDate, category) }; Unit }

    val body: @Composable ColumnScope.() -> Unit = {
        LField(name, { name = it }, label = "Name")
        DecimalField(amountText, { amountText = it }, "Amount")

        ChipsRow {
            BillFrequency.entries.forEach { f ->
                LChip(f.displayName, frequency == f, onClick = { frequency = f })
            }
        }

        CategoryChipsRow(selected = category, onSelect = { category = it })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Due", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            DatePickChip(dueDate, selected = true, onDate = { dueDate = it })
        }

        if (existing != null) {
            if (onPaid != null && existing.isActive) LGhostButton("Paid", onClick = onPaid)
            if (onSkip != null && existing.isActive) LGhostButton("Skip", onClick = onSkip)
            if (onToggleActive != null) {
                LGhostButton(if (existing.isActive) "Pause" else "Resume", onClick = onToggleActive)
            }
        }
    }

    if (existing != null) {
        LItemSheet(
            title = "Edit bill",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            onDelete = onDelete,
            content = body
        )
    } else {
        LSheet(
            title = "New bill",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            content = body
        )
    }
}
