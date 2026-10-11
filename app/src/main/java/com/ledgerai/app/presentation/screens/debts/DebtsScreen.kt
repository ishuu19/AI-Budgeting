package com.ledgerai.app.presentation.screens.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.money.OptionalDateField
import com.ledgerai.app.presentation.screens.money.PillAction
import com.ledgerai.app.presentation.screens.money.dueLabel
import com.ledgerai.app.presentation.theme.IncomeGreen
import com.ledgerai.app.presentation.screens.transactions.amountInput
import java.time.LocalDate

/**
 * Debts section for the Money Owed segment: totals, filter chips, then open debts with the soonest due first
 * (overdue in red). [onRepay] is the one-tap "record a payment" action on each row.
 */
fun LazyListScope.debtItems(
    state: DebtsUiState,
    filter: DebtFilter,
    onFilter: (DebtFilter) -> Unit,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Debt) -> Unit,
    onRepay: (Debt) -> Unit = onEdit
) {
    val visible = state.activeDebts.filter { it.matches(filter, today) }
        .sortedWith(compareBy<Debt> { it.dueDate == null }.thenBy { it.dueDate })
    item(key = "debts-header") { LSection("Debts", action = "New debt", onAction = onAdd) }
    if (state.activeDebts.isNotEmpty()) {
        item(key = "debts-summary") { DebtNet(state) }
    }
    item(key = "debts-chips") {
        LSegments(DebtFilter.entries.toList(), filter, { it.label }, onFilter)
    }
    if (visible.isEmpty()) {
        if (!state.isLoading) {
            item(key = "debts-empty") {
                LEmpty(
                    Icons.Filled.Handshake,
                    if (state.activeDebts.isEmpty()) "No debts yet" else "No debts match"
                )
            }
        }
    } else {
        item(key = "debts-group") {
            LimitedGroup(visible, id = { it.id }, expandKey = "debts-${filter.name}") { debt ->
                DebtRow(debt, today, onClick = { onEdit(debt) }, onRepay = { onRepay(debt) })
            }
        }
    }
    if (filter == DebtFilter.ALL && state.settledDebts.isNotEmpty()) {
        item(key = "debts-settled-header") { LSection("Settled") }
        item(key = "debts-settled-group") {
            LimitedGroup(state.settledDebts, id = { it.id }, expandKey = "debts-settled") { debt ->
                LGroupRow(
                    title = debt.friendName,
                    sub = "Settled",
                    trailing = money(debt.amount),
                    trailingColor = L.OnBoxMuted,
                    onClick = { onEdit(debt) }
                )
            }
        }
    }
}

@Composable
private fun DebtNet(state: DebtsUiState) {
    val net = state.netPosition
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Net " + (if (net > 0) "+" else "") + money(net),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                net > 0 -> IncomeGreen
                net < 0 -> L.Danger
                else -> L.OnBox
            }
        )
        Row(Modifier.fillMaxWidth().height(L.Space.x8).clip(L.RadiusPill)) {
            if (state.totalOwedToMe > 0.0) {
                Box(Modifier.weight(state.totalOwedToMe.toFloat()).fillMaxHeight().background(IncomeGreen))
            }
            if (state.totalIOwe > 0.0) {
                Box(Modifier.weight(state.totalIOwe.toFloat()).fillMaxHeight().background(L.BoxDeep))
            }
        }
        Text(
            "Owes you ${money(state.totalOwedToMe)} · You owe ${money(state.totalIOwe)}",
            style = MaterialTheme.typography.bodySmall,
            color = L.OnBoxMuted
        )
    }
}

@Composable
private fun DebtRow(debt: Debt, today: LocalDate, onClick: () -> Unit, onRepay: () -> Unit) {
    val overdue = debt.isOverdue(today)
    val owedToMe = debt.direction == DebtDirection.THEY_OWE
    val who = if (owedToMe) "Owes you" else "You owe"
    val due = debt.dueDate?.let { dueLabel(it, today) }
    LGroupRow(
        title = debt.friendName,
        sub = if (due != null) "$who · $due" else who,
        trailing = money(debt.amount),
        trailingColor = if (overdue) L.Danger else L.OnBox,
        icon = if (owedToMe) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
        onClick = onClick,
        end = { PillAction("Paid back", "Paid back ${debt.friendName}", onRepay) }
    )
}

/** Quick repayment: amount starts at the full balance, chips for half or all, or settle in one tap. */
@Composable
fun RepaySheet(
    debt: Debt,
    onDismiss: () -> Unit,
    onPay: (Double) -> Unit,
    onSettle: () -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf(amountInput(debt.amount)) }
    val amount = amountText.toDoubleOrNull()
    val canPay = amount != null && amount > 0
    val half = kotlin.math.round(debt.amount * 50) / 100.0
    LSheet(
        title = debt.friendName,
        onDismiss = onDismiss,
        primary = "Paid back",
        onPrimary = { if (canPay) onPay(amount!!) },
        primaryEnabled = canPay,
        secondary = "Settle",
        onSecondary = onSettle
    ) {
        Text("Balance ${money(debt.amount)}", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        ChipsRow {
            if (half > 0.0) LChip("Half", amountText == amountInput(half), onClick = { amountText = amountInput(half) })
            LChip("All", amountText == amountInput(debt.amount), onClick = { amountText = amountInput(debt.amount) })
        }
        DecimalField(amountText, { amountText = it }, "Amount")
    }
}

@Composable
fun DebtSheet(
    existing: Debt?,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, direction: DebtDirection, due: LocalDate?, phone: String, email: String, note: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(existing?.friendName ?: "") }
    var amountText by rememberSaveable { mutableStateOf(existing?.amount?.let(::amountInput) ?: "") }
    var direction by rememberSaveable { mutableStateOf(existing?.direction ?: DebtDirection.THEY_OWE) }
    var due by rememberSaveable { mutableStateOf(existing?.dueDate) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var more by rememberSaveable {
        mutableStateOf(existing != null && (existing.phone.isNotBlank() || existing.email.isNotBlank() || existing.note.isNotBlank()))
    }
    val amount = amountText.toDoubleOrNull()
    val canSave = name.isNotBlank() && amount != null && amount > 0

    val save = { if (canSave) onSave(name.trim(), amount!!, direction, due, phone.trim(), email.trim(), note.trim()) }
    val body: @Composable ColumnScope.() -> Unit = {
        LSegments(
            options = listOf(DebtDirection.THEY_OWE, DebtDirection.I_OWE),
            selected = direction,
            label = { if (it == DebtDirection.THEY_OWE) "Owes you" else "You owe" },
            onSelect = { direction = it }
        )
        LField(name, { name = it }, "Name")
        DecimalField(amountText, { amountText = it }, "Amount")
        OptionalDateField("Due", due) { due = it }
        if (more) {
            LField(phone, { phone = it }, "Phone", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            LField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            LField(note, { note = it }, "Note")
        } else {
            LChip("More", false, onClick = { more = true })
        }
    }

    if (existing != null) {
        LItemSheet(
            title = "Edit debt",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            onDelete = onDelete,
            content = body
        )
    } else {
        LSheet(
            title = "New debt",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            content = body
        )
    }
}
