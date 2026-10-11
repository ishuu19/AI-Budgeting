package com.ledgerai.app.presentation.screens.money

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.screens.bills.BillSheet
import com.ledgerai.app.presentation.screens.bills.BillsViewModel
import com.ledgerai.app.presentation.screens.bills.billItems
import com.ledgerai.app.presentation.screens.debts.DebtFilter
import com.ledgerai.app.presentation.screens.debts.DebtSheet
import com.ledgerai.app.presentation.screens.debts.DebtsViewModel
import com.ledgerai.app.presentation.screens.debts.RepaySheet
import com.ledgerai.app.presentation.screens.debts.debtItems
import com.ledgerai.app.presentation.screens.debts.isOverdue

/** Money > Owed: one hero (due this month, overdue in red), then Bills and Debts, each with a one-tap Paid / Pay action. */
@Composable
fun MoneyOwedScreen(
    open: OpenItem?,
    onOpened: () -> Unit,
    billsVm: BillsViewModel = hiltViewModel(),
    debtsVm: DebtsViewModel = hiltViewModel()
) {
    val bills by billsVm.bills.collectAsState()
    val monthlyTotal by billsVm.monthlyTotal.collectAsState()
    val billMessage by billsVm.message.collectAsState()
    val debtState by debtsVm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val today = rememberToday()

    var addingBill by rememberSaveable { mutableStateOf(false) }
    var editingBill by rememberSaveable { mutableStateOf(NO_ID) }
    var addingDebt by rememberSaveable { mutableStateOf(false) }
    var editingDebt by rememberSaveable { mutableStateOf(NO_ID) }
    var repayingDebt by rememberSaveable { mutableStateOf(NO_ID) }
    var debtFilter by rememberSaveable { mutableStateOf(DebtFilter.ALL) }
    var showSettled by rememberSaveable { mutableStateOf(false) }

    SnackEffect(billMessage, snackbar) { billsVm.clearMessage() }
    SnackEffect(debtState.snackbarMessage, snackbar) { debtsVm.clearSnackbar() }

    val allDebts = debtState.activeDebts + debtState.settledDebts
    LaunchedEffect(open, bills, allDebts) {
        val request = open ?: return@LaunchedEffect
        when (request.kind) {
            OpenKind.Bill -> if (bills.any { it.id == request.id }) {
                editingBill = request.id
                onOpened()
            }
            OpenKind.Debt -> allDebts.firstOrNull { it.id == request.id }?.let { debt ->
                if (debt.isPaid) showSettled = true
                editingDebt = request.id
                onOpened()
            }
            else -> Unit
        }
    }
    OpenTimeout(open, onOpened)

    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val active = bills.filter { it.isActive }
    val overdueCount = active.count { it.nextDueDate.isBefore(today) } +
        debtState.activeDebts.count { it.isOverdue(today) }
    val dueThisMonth = active.filter { !it.nextDueDate.isAfter(monthEnd) }.sumOf { it.amount }

    LScreen(title = "Owed", snackbarHost = { SnackbarHost(snackbar) }) {
        item(key = "hero") {
            LHero(
                label = "Due this month",
                value = money(dueThisMonth),
                sub = "$overdueCount overdue · ${money(monthlyTotal)} per month",
                valueColor = if (overdueCount > 0) L.Danger else L.OnBox
            )
        }
        billItems(
            bills = bills,
            today = today,
            onAdd = { addingBill = true },
            onEdit = { editingBill = it.id },
            onPaid = billsVm::markPaid
        )
        debtItems(
            state = debtState,
            filter = debtFilter,
            onFilter = { debtFilter = it },
            showSettled = showSettled,
            onToggleSettled = { showSettled = !showSettled },
            today = today,
            onAdd = { addingDebt = true },
            onEdit = { editingDebt = it.id },
            onRepay = { repayingDebt = it.id }
        )
    }

    if (addingBill) {
        BillSheet(
            existing = null,
            onDismiss = { addingBill = false },
            onSave = { name, amount, freq, date, cat ->
                billsVm.addBill(name, amount, freq, date, cat)
                addingBill = false
            }
        )
    }
    bills.firstOrNull { it.id == editingBill }?.let { bill ->
        key(bill.id) {
            BillSheet(
                existing = bill,
                onDismiss = { editingBill = NO_ID },
                onSave = { name, amount, freq, date, cat ->
                    billsVm.updateBill(bill, name, amount, freq, date, cat)
                    editingBill = NO_ID
                },
                onPaid = {
                    billsVm.markPaid(bill)
                    editingBill = NO_ID
                },
                onSkip = {
                    billsVm.skip(bill)
                    editingBill = NO_ID
                },
                onToggleActive = {
                    billsVm.setActive(bill, !bill.isActive)
                    editingBill = NO_ID
                },
                onDelete = {
                    billsVm.deleteBill(bill)
                    editingBill = NO_ID
                }
            )
        }
    }

    if (addingDebt) {
        DebtSheet(
            existing = null,
            onDismiss = { addingDebt = false },
            onSave = { name, amount, direction, due, phone, email, note ->
                debtsVm.addDebt(name, amount, direction, due, phone, email, note)
                addingDebt = false
            }
        )
    }
    debtState.activeDebts.firstOrNull { it.id == repayingDebt }?.let { debt ->
        key(debt.id) {
            RepaySheet(
                debt = debt,
                onDismiss = { repayingDebt = NO_ID },
                onPay = { payment ->
                    debtsVm.recordPayment(debt, payment)
                    repayingDebt = NO_ID
                },
                onSettle = {
                    debtsVm.markAsPaid(debt)
                    repayingDebt = NO_ID
                }
            )
        }
    }
    allDebts.firstOrNull { it.id == editingDebt }?.let { debt ->
        key(debt.id) {
            DebtSheet(
                existing = debt,
                onDismiss = { editingDebt = NO_ID },
                onSave = { name, amount, direction, due, phone, email, note ->
                    debtsVm.updateDebt(debt, name, amount, direction, due, phone, email, note)
                    editingDebt = NO_ID
                },
                onPay = { payment ->
                    debtsVm.recordPayment(debt, payment)
                    editingDebt = NO_ID
                },
                onSettle = {
                    debtsVm.markAsPaid(debt)
                    editingDebt = NO_ID
                },
                onDelete = {
                    debtsVm.deleteDebt(debt)
                    editingDebt = NO_ID
                }
            )
        }
    }
}
