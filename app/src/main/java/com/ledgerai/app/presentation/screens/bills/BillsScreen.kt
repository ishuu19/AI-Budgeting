package com.ledgerai.app.presentation.screens.bills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.screens.transactions.CategoryChipsRow
import com.ledgerai.app.presentation.screens.transactions.ConfirmDelete
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.presentation.screens.transactions.amountInput
import com.ledgerai.app.presentation.screens.transactions.shortDate
import com.ledgerai.app.presentation.screens.transactions.spendIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val transactionRepo: TransactionRepository
) : ViewModel() {

    val bills: StateFlow<List<Bill>> = billRepo.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monthlyTotal = MutableStateFlow(0.0)
    val monthlyTotal: StateFlow<Double> = _monthlyTotal.asStateFlow()

    init {
        viewModelScope.launch {
            _monthlyTotal.value = billRepo.getTotalMonthlyBills()
        }
    }

    fun addBill(name: String, amount: Double, frequency: BillFrequency, nextDueDate: LocalDate, category: TransactionCategory) {
        viewModelScope.launch {
            billRepo.insert(Bill(name = name, amount = amount, frequency = frequency,
                nextDueDate = nextDueDate, category = category))
            refreshMonthlyTotal()
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
                bill.copy(
                    name = name,
                    amount = amount,
                    frequency = frequency,
                    nextDueDate = nextDueDate,
                    category = category
                )
            )
            refreshMonthlyTotal()
        }
    }

    /** Marks the current cycle paid: expense in Spend, then roll next due. */
    fun markPaid(bill: Bill) {
        viewModelScope.launch {
            transactionRepo.insert(
                Transaction(
                    amount = bill.amount,
                    type = TransactionType.EXPENSE,
                    category = bill.category,
                    merchant = bill.name,
                    date = LocalDate.now()
                )
            )
            billRepo.update(bill.copy(nextDueDate = rollDueDate(bill.nextDueDate, bill.frequency)))
            refreshMonthlyTotal()
        }
    }

    /** Advances one period without recording a payment. */
    fun skip(bill: Bill) {
        viewModelScope.launch {
            billRepo.update(bill.copy(nextDueDate = rollDueDate(bill.nextDueDate, bill.frequency)))
            refreshMonthlyTotal()
        }
    }

    fun setActive(bill: Bill, active: Boolean) {
        viewModelScope.launch {
            billRepo.update(bill.copy(isActive = active))
            refreshMonthlyTotal()
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            billRepo.delete(bill)
            refreshMonthlyTotal()
        }
    }

    private suspend fun refreshMonthlyTotal() {
        _monthlyTotal.value = billRepo.getTotalMonthlyBills()
    }
}

private fun rollDueDate(from: LocalDate, frequency: BillFrequency): LocalDate = when (frequency) {
    BillFrequency.WEEKLY -> from.plusWeeks(1)
    BillFrequency.MONTHLY -> from.plusMonths(1)
    BillFrequency.QUARTERLY -> from.plusMonths(3)
    BillFrequency.YEARLY -> from.plusYears(1)
}

@Composable
fun BillsScreen(onBack: () -> Unit = {}, viewModel: BillsViewModel = hiltViewModel()) {
    val bills by viewModel.bills.collectAsState()
    val monthlyTotal by viewModel.monthlyTotal.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bill?>(null) }

    val today = LocalDate.now()
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val soonEnd = today.plusDays(7)
    val active = remember(bills) { bills.filter { it.isActive }.sortedBy { it.nextDueDate } }
    val paused = remember(bills) { bills.filter { !it.isActive }.sortedBy { it.nextDueDate } }
    val overdue = remember(active, today) { active.filter { it.nextDueDate.isBefore(today) } }
    val dueSoon = remember(active, today, soonEnd) {
        active.filter { !it.nextDueDate.isBefore(today) && !it.nextDueDate.isAfter(soonEnd) }
    }
    val later = remember(active, soonEnd) { active.filter { it.nextDueDate.isAfter(soonEnd) } }
    val dueThisMonth = active.filter { !it.nextDueDate.isAfter(monthEnd) }.sumOf { it.amount }

    LScreen(
        title = "Bills",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { adding = true }) }
    ) {
        item(key = "hero") {
            LHero(label = "Due", value = money(dueThisMonth), sub = "${overdue.size} overdue · ${money(monthlyTotal)} / mo")
        }

        if (bills.isEmpty()) {
            item(key = "empty") { LEmpty(Icons.Filled.EventRepeat, "No bills") }
        }

        if (overdue.isNotEmpty()) {
            item(key = "sec-overdue") { LSection("Overdue") }
            items(overdue, key = { "overdue-${it.id}" }) { bill ->
                BillRow(bill, today, onClick = { editing = bill }, onPaid = { viewModel.markPaid(bill) })
            }
        }

        if (dueSoon.isNotEmpty()) {
            item(key = "sec-soon") { LSection("Due soon") }
            items(dueSoon, key = { "soon-${it.id}" }) { bill ->
                BillRow(bill, today, onClick = { editing = bill }, onPaid = { viewModel.markPaid(bill) })
            }
        }

        if (later.isNotEmpty()) {
            item(key = "sec-later") { LSection("Later") }
            items(later, key = { "later-${it.id}" }) { bill ->
                BillRow(bill, today, onClick = { editing = bill }, onPaid = { viewModel.markPaid(bill) })
            }
        }

        if (paused.isNotEmpty()) {
            item(key = "sec-paused") { LSection("Paused") }
            items(paused, key = { "paused-${it.id}" }) { bill ->
                BillRow(bill, today, onClick = { editing = bill })
            }
        }
    }

    if (adding) {
        BillSheet(
            existing = null,
            onDismiss = { adding = false },
            onSave = { name, amount, freq, date, cat ->
                viewModel.addBill(name, amount, freq, date, cat)
                adding = false
            }
        )
    }

    editing?.let { bill ->
        key(bill.id) {
            BillSheet(
                existing = bill,
                onDismiss = { editing = null },
                onSave = { name, amount, freq, date, cat ->
                    viewModel.updateBill(bill, name, amount, freq, date, cat)
                    editing = null
                },
                onPaid = {
                    viewModel.markPaid(bill)
                    editing = null
                },
                onSkip = {
                    viewModel.skip(bill)
                    editing = null
                },
                onToggleActive = {
                    viewModel.setActive(bill, !bill.isActive)
                    editing = null
                },
                onDelete = {
                    viewModel.deleteBill(bill)
                    editing = null
                }
            )
        }
    }
}

@Composable
private fun BillRow(
    bill: Bill,
    today: LocalDate,
    onClick: () -> Unit,
    onPaid: (() -> Unit)? = null
) {
    val days = ChronoUnit.DAYS.between(today, bill.nextDueDate)
    val paidEnd: (@Composable () -> Unit)? = if (onPaid == null) null else {
        {
            IconButton(onClick = onPaid, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Paid", tint = L.OnBoxMuted)
            }
        }
    }
    LRow(
        title = bill.name,
        sub = "${dueLabel(days, bill.nextDueDate)} · ${bill.frequency.displayName}",
        trailing = money(bill.amount),
        trailingColor = if (days < 0) L.Danger else L.Gold,
        icon = spendIcon(bill.category),
        onClick = onClick,
        end = paidEnd
    )
}

private fun dueLabel(days: Long, date: LocalDate): String = when {
    days < 0 -> "Overdue"
    days == 0L -> "Today"
    days == 1L -> "Tomorrow"
    days <= 7 -> "In $days days"
    else -> shortDate(date)
}

@Composable
private fun BillSheet(
    existing: Bill?,
    onDismiss: () -> Unit,
    onSave: (String, Double, BillFrequency, LocalDate, TransactionCategory) -> Unit,
    onPaid: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onToggleActive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amountText by remember { mutableStateOf(existing?.amount?.let(::amountInput) ?: "") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: BillFrequency.MONTHLY) }
    var dueDate by remember { mutableStateOf(existing?.nextDueDate ?: LocalDate.now().plusMonths(1)) }
    var category by remember { mutableStateOf(existing?.category ?: TransactionCategory.SUBSCRIPTIONS) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }

    LSheet(
        title = if (existing != null) "Edit" else "New",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { amount?.let { onSave(name.trim(), it, frequency, dueDate, category) } },
        primaryEnabled = name.isNotBlank() && amount != null,
        secondary = if (existing != null && onSkip != null) "Skip" else null,
        onSecondary = { onSkip?.invoke() }
    ) {
        LField(name, { name = it }, label = "Name")
        LField(
            value = amountText,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1) amountText = cleaned
            },
            label = "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BillFrequency.entries.forEach { f ->
                LChip(f.displayName, frequency == f, onClick = { frequency = f })
            }
        }

        CategoryChipsRow(selected = category, onSelect = { category = it })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Due", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            DatePickChip(dueDate, selected = true, onDate = { dueDate = it })
        }

        if (onPaid != null) LGhostButton("Paid", onClick = onPaid)
        if (onToggleActive != null) {
            LGhostButton(if (existing?.isActive == true) "Pause" else "Resume", onClick = onToggleActive)
        }
        if (onDelete != null) LGhostButton("Delete", onClick = { confirmDelete = true })
    }

    if (confirmDelete) {
        ConfirmDelete(
            onConfirm = { confirmDelete = false; onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
