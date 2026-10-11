package com.ledgerai.app.presentation.screens.debts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.money.OptionalDateField
import com.ledgerai.app.presentation.screens.transactions.amountInput
import com.ledgerai.app.presentation.screens.transactions.shortDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Debts section for the Money Owed segment: totals, filter chips, then open debts with the soonest due first
 * (overdue in red). [onRepay] is the one-tap "record a payment" action on each row.
 */
fun LazyListScope.debtItems(
    state: DebtsUiState,
    filter: DebtFilter,
    onFilter: (DebtFilter) -> Unit,
    showSettled: Boolean,
    onToggleSettled: () -> Unit,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Debt) -> Unit,
    onRepay: (Debt) -> Unit = onEdit
) {
    val visible = state.activeDebts.filter { it.matches(filter, today) }
        .sortedWith(compareBy<Debt> { it.dueDate == null }.thenBy { it.dueDate })
    item(key = "debts-header") { LSection("Debts", action = "Add", onAction = onAdd) }
    if (state.activeDebts.isNotEmpty()) {
        item(key = "debts-summary") {
            LSmallPair(
                left = { m -> LSmallBlock("Owed to you", money(state.totalOwedToMe), modifier = m) },
                right = { m -> LSmallBlock("You owe", money(state.totalIOwe), modifier = m) }
            )
        }
    }
    item(key = "debts-chips") {
        LKindChips(DebtFilter.entries.toList(), filter, { it.label }, onFilter)
    }
    if (visible.isEmpty()) {
        if (!state.isLoading) {
            item(key = "debts-empty") {
                LEmpty(
                    Icons.Filled.Handshake,
                    if (state.activeDebts.isEmpty()) "Say: Sam owes me 40 until Friday" else "Nothing here"
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
        item(key = "debts-settled-header") {
            LSection("Settled", action = if (showSettled) "Hide" else "Show", onAction = onToggleSettled)
        }
        if (showSettled) {
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
}

@Composable
private fun DebtRow(debt: Debt, today: LocalDate, onClick: () -> Unit, onRepay: () -> Unit) {
    val overdue = debt.isOverdue(today)
    val owedToMe = debt.direction == DebtDirection.THEY_OWE
    val who = if (owedToMe) "Owes you" else "You owe"
    val due = dueLabel(debt.dueDate, today)
    val description = if (owedToMe) "Record payment from ${debt.friendName}" else "Record payment to ${debt.friendName}"
    LGroupRow(
        title = debt.friendName,
        sub = if (due != null) "$who · $due" else who,
        trailing = money(debt.amount),
        trailingColor = if (overdue) L.Danger else L.OnBox,
        icon = if (owedToMe) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
        onClick = onClick,
        end = {
            val tint = if (overdue) L.Danger else L.Primary
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onRepay)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (owedToMe) "Received" else "Pay",
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    modifier = Modifier
                        .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    )
}

private fun dueLabel(due: LocalDate?, today: LocalDate): String? {
    if (due == null) return null
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days < 0 -> "Overdue ${-days}d"
        days == 0L -> "Due today"
        else -> "Due ${shortDate(due)}"
    }
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
        title = if (debt.direction == DebtDirection.THEY_OWE) "${debt.friendName} paid you" else "Pay ${debt.friendName}",
        onDismiss = onDismiss,
        primary = "Record",
        onPrimary = { if (canPay) onPay(amount!!) },
        primaryEnabled = canPay,
        secondary = "Settle in full",
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
    onPay: (Double) -> Unit = {},
    onSettle: () -> Unit = {},
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
    var paying by rememberSaveable { mutableStateOf(false) }
    var payText by rememberSaveable { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull()
    val canSave = name.isNotBlank() && amount != null && amount > 0
    val payment = payText.toDoubleOrNull()
    val canPay = payment != null && payment > 0
    val open = existing != null && !existing.isPaid

    if (paying) {
        LSheet(
            title = "Pay ${existing?.friendName.orEmpty()}",
            onDismiss = onDismiss,
            primary = "Pay",
            onPrimary = { if (canPay) onPay(payment!!) },
            primaryEnabled = canPay,
            secondary = "Back",
            onSecondary = { paying = false; payText = "" }
        ) {
            DecimalField(payText, { payText = it }, "Amount")
        }
        return
    }

    val save = { if (canSave) onSave(name.trim(), amount!!, direction, due, phone.trim(), email.trim(), note.trim()) }
    val body: @Composable ColumnScope.() -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("They owe me", direction == DebtDirection.THEY_OWE, { direction = DebtDirection.THEY_OWE })
            LChip("I owe", direction == DebtDirection.I_OWE, { direction = DebtDirection.I_OWE })
        }
        LField(name, { name = it }, "Name")
        DecimalField(amountText, { amountText = it }, "Amount")
        OptionalDateField("Due", due) { due = it }
        if (more) {
            LField(phone, { phone = it }, "Phone", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            LField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            LField(note, { note = it }, "Note")
        } else {
            LChip("More details", false, onClick = { more = true })
        }

        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LGhostButton("Pay", { paying = true }, Modifier.weight(1f))
                LGhostButton("Settle", onSettle, Modifier.weight(1f))
            }
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
