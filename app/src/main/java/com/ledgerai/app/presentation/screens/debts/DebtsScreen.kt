package com.ledgerai.app.presentation.screens.debts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.presentation.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DueFormat = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun DebtsScreen(
    onBack: () -> Unit = {},
    viewModel: DebtsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(DebtFilter.ALL) }
    var showSettled by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    val today = LocalDate.now()
    val visible = remember(state.activeDebts, filter, today) {
        state.activeDebts.filter { it.matches(filter, today) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LScreen(
            title = "Debts",
            onBack = onBack,
            fab = { LFab(Icons.Filled.Add, onClick = { adding = true }) }
        ) {
            item(key = "hero") {
                val net = state.netPosition
                LHero(
                    label = "Net",
                    value = money(net),
                    sub = "Owed ${money(state.totalOwedToMe)} · Owe ${money(state.totalIOwe)}",
                    valueColor = if (net < 0) L.Danger else L.OnBox
                )
            }

            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebtFilter.entries.forEach { f ->
                        LChip(f.label, filter == f, onClick = { filter = f })
                    }
                }
            }

            if (visible.isEmpty() && !state.isLoading) {
                item(key = "empty") { LEmpty(Icons.Filled.Handshake, "No debts") }
            } else {
                items(visible, key = { it.id }) { debt ->
                    DebtRow(debt, today, onClick = { editing = debt })
                }
            }

            if (filter == DebtFilter.ALL && state.settledDebts.isNotEmpty()) {
                item(key = "settled-header") {
                    LSection(
                        "Settled",
                        action = if (showSettled) "Hide" else "Show",
                        onAction = { showSettled = !showSettled }
                    )
                }
                if (showSettled) {
                    items(state.settledDebts, key = { "settled-${it.id}" }) { debt ->
                        LRow(
                            title = debt.friendName,
                            sub = "Settled",
                            trailing = money(debt.amount),
                            trailingColor = L.OnBoxMuted,
                            onClick = { editing = debt }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        )
    }

    if (adding) {
        DebtSheet(
            existing = null,
            onDismiss = { adding = false },
            onSave = { name, amount, direction, due, note ->
                viewModel.addDebt(name, amount, direction, due, "", "", note)
                adding = false
            }
        )
    }

    editing?.let { debt ->
        key(debt.id) {
            DebtSheet(
                existing = debt,
                onDismiss = { editing = null },
                onSave = { name, amount, direction, due, note ->
                    viewModel.updateDebt(debt, name, amount, direction, due, debt.phone, debt.email, note)
                    editing = null
                },
                onPay = { payment ->
                    viewModel.recordPayment(debt, payment)
                    editing = null
                },
                onSettle = {
                    viewModel.markAsPaid(debt)
                    editing = null
                },
                onDelete = {
                    viewModel.deleteDebt(debt)
                    editing = null
                }
            )
        }
    }
}

@Composable
private fun DebtRow(debt: Debt, today: LocalDate, onClick: () -> Unit) {
    val overdue = debt.isOverdue(today)
    val owedToMe = debt.direction == DebtDirection.THEY_OWE
    LRow(
        title = debt.friendName,
        sub = dueLabel(debt.dueDate),
        trailing = money(debt.amount),
        trailingColor = if (overdue || !owedToMe) L.Danger else L.Gold,
        onClick = onClick
    )
}

private fun dueLabel(due: LocalDate?): String? {
    if (due == null) return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), due)
    return when {
        days < 0 -> "Overdue"
        days == 0L -> "Today"
        else -> due.format(DueFormat)
    }
}

private fun parseDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()

@Composable
private fun DebtSheet(
    existing: Debt?,
    onDismiss: () -> Unit,
    onSave: (String, Double, DebtDirection, LocalDate?, String) -> Unit,
    onPay: (Double) -> Unit = {},
    onSettle: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(existing?.friendName ?: "") }
    var amountText by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var direction by remember { mutableStateOf(existing?.direction ?: DebtDirection.THEY_OWE) }
    var dueText by remember { mutableStateOf(existing?.dueDate?.toString() ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var payText by remember { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull()
    val dueValid = dueText.isBlank() || parseDate(dueText) != null
    val canSave = name.isNotBlank() && amount != null && amount > 0 && dueValid
    val payment = payText.toDoubleOrNull()
    val canPay = payment != null && payment > 0
    val open = existing != null && !existing.isPaid

    LSheet(
        title = if (existing == null) "New debt" else "Edit debt",
        onDismiss = onDismiss,
        primary = if (paying) "Pay" else "Save",
        onPrimary = {
            if (paying) {
                if (canPay) onPay(payment!!)
            } else if (canSave) {
                onSave(name.trim(), amount!!, direction, parseDate(dueText), note.trim())
            }
        },
        primaryEnabled = if (paying) canPay else canSave
    ) {
        if (!paying) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip("They owe", direction == DebtDirection.THEY_OWE, { direction = DebtDirection.THEY_OWE })
                LChip("I owe", direction == DebtDirection.I_OWE, { direction = DebtDirection.I_OWE })
            }
            LField(name, { name = it }, "Name")
            LField(
                amountText,
                { amountText = it },
                "Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            LField(dueText, { dueText = it }, "Due (YYYY-MM-DD)")
            LField(note, { note = it }, "Note")

            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LGhostButton("Pay", { paying = true }, Modifier.weight(1f))
                    LGhostButton("Settle", onSettle, Modifier.weight(1f))
                }
            }
            if (existing != null) {
                LGhostButton("Delete", { confirmDelete = true })
            }
        } else {
            LField(
                payText,
                { payText = it },
                "Pay",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            LGhostButton("Back", { paying = false; payText = "" })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = L.Page,
            title = { Text("Delete?", color = L.Ink) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = L.Box) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = L.InkMuted) }
            }
        )
    }
}
