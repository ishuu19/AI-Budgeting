package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LSheet
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddTransactionSheet(
    prefilled: ParsedTransaction? = null,
    existing: Transaction? = null,
    copyDraft: Transaction? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, TransactionType, TransactionCategory, String, String, LocalDate, String, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCopy: ((Double, TransactionType, TransactionCategory, String, String, String, Boolean) -> Unit)? = null
) {
    val seed = existing ?: copyDraft
    var amountText by remember {
        mutableStateOf((seed?.amount ?: prefilled?.amount)?.let(::amountInput) ?: "")
    }
    var merchant by remember { mutableStateOf(seed?.merchant ?: prefilled?.merchant ?: "") }
    var note by remember { mutableStateOf(seed?.note ?: prefilled?.note ?: "") }
    var location by remember { mutableStateOf(seed?.location ?: prefilled?.location ?: "") }
    var type by remember { mutableStateOf(seed?.type ?: prefilled?.type ?: TransactionType.EXPENSE) }
    var category by remember {
        mutableStateOf(seed?.category ?: prefilled?.category ?: TransactionCategory.OTHER)
    }
    var date by remember { mutableStateOf(seed?.date ?: prefilled?.date ?: LocalDate.now()) }
    var isRecurring by remember { mutableStateOf(seed?.isRecurring ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val today = LocalDate.now()
    val canCopy = existing != null && onCopy != null

    LSheet(
        title = if (existing != null) "Edit" else "New",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            amount?.let { onConfirm(it, type, category, merchant.trim(), note.trim(), date, location.trim(), isRecurring) }
        },
        primaryEnabled = amount != null,
        secondary = when {
            canCopy -> "Copy"
            existing != null && onDelete != null -> "Delete"
            else -> null
        },
        onSecondary = {
            if (canCopy) {
                val copyAmount = amount ?: existing?.amount
                if (copyAmount != null) {
                    onCopy?.invoke(copyAmount, type, category, merchant.trim(), note.trim(), location.trim(), isRecurring)
                }
            } else {
                confirmDelete = true
            }
        }
    ) {
        BigAmountField(amountText, onValueChange = { amountText = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("Expense", type == TransactionType.EXPENSE, onClick = { type = TransactionType.EXPENSE })
            LChip("Income", type == TransactionType.INCOME, onClick = { type = TransactionType.INCOME })
            LChip("Repeat", isRecurring, onClick = { isRecurring = !isRecurring })
        }

        CategoryChipsRow(selected = category, onSelect = { category = it })

        LField(merchant, { merchant = it }, label = "Name")
        LField(location, { location = it }, label = "Place")
        LField(note, { note = it }, label = "Note")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("Today", date == today, onClick = { date = today })
            LChip("Yesterday", date == today.minusDays(1), onClick = { date = today.minusDays(1) })
            val custom = date != today && date != today.minusDays(1)
            DatePickChip(date, selected = custom, onDate = { date = it }, label = if (custom) shortDate(date) else "Date")
        }

        if (canCopy && onDelete != null) {
            LGhostButton("Delete", onClick = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        ConfirmDelete(
            onConfirm = { confirmDelete = false; onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Large centered money input. Accepts digits and one decimal point. */
@Composable
internal fun BigAmountField(value: String, onValueChange: (String) -> Unit, symbol: String = "$") {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, style = MaterialTheme.typography.displaySmall, color = L.Gold)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1) onValueChange(cleaned)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.displaySmall.copy(color = L.Ink),
            cursorBrush = SolidColor(L.Box),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.widthIn(min = 48.dp).width(IntrinsicSize.Min),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("0", style = MaterialTheme.typography.displaySmall, color = L.InkMuted)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun CategoryChipsRow(selected: TransactionCategory, onSelect: (TransactionCategory) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransactionCategory.entries.forEach { cat ->
            LChip(cat.displayName, cat == selected, onClick = { onSelect(cat) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickChip(
    date: LocalDate,
    selected: Boolean,
    onDate: (LocalDate) -> Unit,
    label: String = shortDate(date)
) {
    var open by remember { mutableStateOf(false) }
    LChip(label, selected, onClick = { open = true })
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("Done", color = L.Box) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel", color = L.InkMuted) }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
internal fun ConfirmDelete(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = L.Page,
        title = { Text("Delete?", color = L.Ink) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = L.Box) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = L.InkMuted) } }
    )
}

internal fun spendIcon(category: TransactionCategory): ImageVector = when (category) {
    TransactionCategory.FOOD -> Icons.Filled.Restaurant
    TransactionCategory.TRANSPORT -> Icons.Filled.DirectionsCar
    TransactionCategory.ENTERTAINMENT -> Icons.Filled.Movie
    TransactionCategory.SHOPPING -> Icons.Filled.ShoppingBag
    TransactionCategory.HEALTH -> Icons.Filled.LocalHospital
    TransactionCategory.RENT -> Icons.Filled.Home
    TransactionCategory.UTILITIES -> Icons.Filled.Bolt
    TransactionCategory.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    TransactionCategory.EDUCATION -> Icons.Filled.School
    TransactionCategory.SALARY -> Icons.Filled.Payments
    TransactionCategory.FREELANCE -> Icons.Filled.Work
    TransactionCategory.OTHER -> Icons.Filled.Category
}

internal fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

internal fun amountInput(amount: Double): String =
    BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
