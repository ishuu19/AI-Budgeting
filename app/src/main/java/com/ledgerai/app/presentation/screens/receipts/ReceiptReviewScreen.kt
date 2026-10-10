package com.ledgerai.app.presentation.screens.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.receipts.DraftExpense
import com.ledgerai.app.domain.receipts.DraftStockChange
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptLine
import com.ledgerai.app.domain.receipts.ReceiptProposals
import com.ledgerai.app.domain.receipts.ValueConfidence
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LField
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/**
 * Review entry. Build [ReceiptReviewViewModel] with the receipt already loaded.
 * A later NavHost route can call this from `composable("receipt/{id}")`.
 * Confirm sends the on-screen lines through [ReceiptReviewViewModel.confirm].
 */
@Composable
fun ReceiptReviewScreen(
    viewModel: ReceiptReviewViewModel,
    onBack: (() -> Unit)? = null,
) {
    ReceiptReviewScreen(
        receipt = viewModel.receipt,
        proposals = viewModel.proposals,
        confirmEnabled = !viewModel.saving,
        onConfirm = viewModel::confirm,
        onBack = onBack,
    )
}

/**
 * Review a captured receipt. Confirm stores it on this phone, including a line
 * whose name or price was corrected here. Merchant and date stay as loaded.
 * The screen does not start a camera capture and does not write money.
 */
@Composable
fun ReceiptReviewScreen(
    receipt: Receipt,
    proposals: ReceiptProposals?,
    confirmEnabled: Boolean,
    onConfirm: (Receipt) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var editedLines by remember(receipt.id, receipt.updatedAt, receipt.locallyConfirmed) {
        mutableStateOf(receipt.lines)
    }
    var editingIndex by remember(receipt.id, receipt.updatedAt, receipt.locallyConfirmed) {
        mutableStateOf<Int?>(null)
    }
    var draftName by remember(receipt.id, receipt.updatedAt, receipt.locallyConfirmed) {
        mutableStateOf("")
    }
    var draftPrice by remember(receipt.id, receipt.updatedAt, receipt.locallyConfirmed) {
        mutableStateOf("")
    }
    val canEdit = !receipt.locallyConfirmed

    Scaffold(
        containerColor = L.Page,
        bottomBar = {
            Column(Modifier.padding(horizontal = L.Gutter, vertical = 12.dp)) {
                LButton(
                    text = if (receipt.locallyConfirmed) "Confirmed on this phone" else "Confirm receipt",
                    onClick = {
                        onConfirm(receipt.copy(lines = linesForConfirm(receipt, editedLines, editingIndex, draftName, draftPrice)))
                    },
                    enabled = confirmEnabled && !receipt.locallyConfirmed,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = L.Gutter, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = L.Ink)
                    }
                }
                Column {
                    Text(
                        "Receipt",
                        style = MaterialTheme.typography.headlineMedium,
                        color = L.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "Saves the receipt on this phone. Spending and pantry stay drafts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.InkMuted,
                    )
                }
            }

            TotalCard(receipt)
            HeaderField(
                label = "Merchant",
                value = receipt.merchant,
                confidence = receipt.merchantConfidence,
            )
            HeaderField(
                label = "Date",
                value = receipt.purchasedOn?.format(dateFormat),
                confidence = receipt.purchasedOnConfidence,
            )
            if (!receipt.paidBy.isNullOrBlank()) {
                HeaderField(label = "Paid by", value = receipt.paidBy, confidence = null)
            }

            Text("Lines", style = MaterialTheme.typography.titleMedium, color = L.Ink)
            if (editedLines.isEmpty()) {
                Text("No lines read", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            } else {
                editedLines.forEachIndexed { index, line ->
                    if (editingIndex == index && canEdit) {
                        LineEditor(
                            name = draftName,
                            price = draftPrice,
                            onName = { draftName = it },
                            onPrice = { draftPrice = it },
                            onDone = {
                                editedLines = editedLines.replaceAt(
                                    index,
                                    correctedLine(receipt.lines[index], draftName, draftPrice),
                                )
                                editingIndex = null
                            },
                            onCancel = { editingIndex = null },
                        )
                    } else {
                        LineRow(
                            line = line,
                            onEdit = if (canEdit) {
                                {
                                    editingIndex = index
                                    draftName = line.rawText
                                    draftPrice = priceText(line.unitPrice)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            if (proposals != null) {
                DraftSection(proposals)
            }
        }
    }
}

@Composable
private fun TotalCard(receipt: Receipt) {
    val amount = amountLabel(receipt.total, receipt.currency)
    val mark = confidenceMark(receipt.totalConfidence, receipt.total == null)
    Surface(color = L.Box, shape = RoundedCornerShape(L.Radius), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(20.dp)
                .semantics { contentDescription = "Total $amount${mark?.let { ", $it" } ?: ""}" },
        ) {
            Text("Total", style = MaterialTheme.typography.labelLarge, color = L.OnBoxMuted)
            Text(
                amount,
                style = MaterialTheme.typography.displaySmall,
                color = L.OnBox,
                fontWeight = FontWeight.SemiBold,
            )
            if (mark != null) {
                Text(mark, style = MaterialTheme.typography.labelLarge, color = L.Gold)
            }
        }
    }
}

@Composable
private fun HeaderField(label: String, value: String?, confidence: ValueConfidence?) {
    val shown = value?.takeIf { it.isNotBlank() } ?: "Unknown"
    val mark = confidenceMark(confidence, value.isNullOrBlank())
    Column(Modifier.semantics { contentDescription = "$label $shown${mark?.let { ", $it" } ?: ""}" }) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown, style = MaterialTheme.typography.titleLarge, color = L.Ink)
            if (mark != null) {
                Text(mark, style = MaterialTheme.typography.labelLarge, color = L.Box)
            }
        }
    }
}

@Composable
private fun LineRow(line: ReceiptLine, onEdit: (() -> Unit)?) {
    val mark = confidenceMark(line.confidence, false)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = listOfNotNull(line.rawText, qtyLabel(line.qty), moneyLabel("Price", line.unitPrice), mark).joinToString(", ")
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                line.rawText,
                style = MaterialTheme.typography.titleMedium,
                color = L.Ink,
                modifier = Modifier.weight(1f),
            )
            if (onEdit != null) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics { contentDescription = "Edit ${line.rawText}" },
                ) {
                    Text("Edit", color = L.Box, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            listOfNotNull(qtyLabel(line.qty), moneyLabel("Price", line.unitPrice), moneyLabel("Line total", line.lineTotal), mark)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = L.InkMuted,
        )
    }
}

@Composable
private fun LineEditor(
    name: String,
    price: String,
    onName: (String) -> Unit,
    onPrice: (String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LField(value = name, onValueChange = onName, label = "Name")
        LField(
            value = price,
            onValueChange = onPrice,
            label = "Price",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = L.InkMuted, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onDone) {
                Text("Done", color = L.Box, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DraftSection(proposals: ReceiptProposals) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Drafts", style = MaterialTheme.typography.titleMedium, color = L.Ink)
        Text(
            "Not recorded as spent.",
            style = MaterialTheme.typography.bodyMedium,
            color = L.InkMuted,
        )
        Text(expenseDraftLabel(proposals.expense), style = MaterialTheme.typography.bodyLarge, color = L.Ink)
        proposals.stockChanges.forEach { change ->
            Text(stockDraftLabel(change), style = MaterialTheme.typography.bodyLarge, color = L.Ink)
        }
    }
}

internal fun amountLabel(amount: Double?, currency: String?): String {
    if (amount == null) return "Unknown"
    val number = String.format(Locale.US, "%.2f", amount)
    return if (currency.isNullOrBlank()) number else "$number $currency"
}

internal fun confidenceMark(confidence: ValueConfidence?, valueUnknown: Boolean): String? = when {
    valueUnknown -> null
    confidence == ValueConfidence.INFERRED -> "Inferred"
    confidence == ValueConfidence.CONFIRMED -> "Confirmed"
    else -> null
}

private fun qtyLabel(qty: Double?): String =
    if (qty == null) "Qty unknown" else "Qty ${trimNumber(qty)}"

private fun moneyLabel(name: String, amount: Double?): String =
    if (amount == null) "$name unknown" else "$name ${trimNumber(amount)}"

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun priceText(amount: Double?): String =
    if (amount == null) "" else trimNumber(amount)

/** Blank or unreadable text is unknown. It is not stored as 0. */
internal fun priceOrNull(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.toDoubleOrNull()
}

/**
 * Name and unit price only. A blank name keeps the current name.
 * Quantity, line total, merchant, and date are left alone.
 * A real change marks the line confirmed.
 */
internal fun correctedLine(line: ReceiptLine, name: String, priceText: String): ReceiptLine {
    val nextName = name.trim().ifEmpty { line.rawText }
    val nextPrice = priceOrNull(priceText)
    if (nextName == line.rawText && nextPrice == line.unitPrice) return line
    return line.copy(
        rawText = nextName,
        unitPrice = nextPrice,
        confidence = ValueConfidence.CONFIRMED,
    )
}

private fun linesForConfirm(
    receipt: Receipt,
    editedLines: List<ReceiptLine>,
    editingIndex: Int?,
    draftName: String,
    draftPrice: String,
): List<ReceiptLine> {
    val index = editingIndex ?: return editedLines
    val original = receipt.lines.getOrNull(index) ?: return editedLines
    return editedLines.replaceAt(index, correctedLine(original, draftName, draftPrice))
}

private fun List<ReceiptLine>.replaceAt(index: Int, line: ReceiptLine): List<ReceiptLine> =
    toMutableList().also { it[index] = line }

private fun expenseDraftLabel(expense: DraftExpense): String {
    val amount = amountLabel(expense.amount, expense.currency)
    val merchant = expense.merchant ?: "Merchant unknown"
    return "Expense draft · $merchant · $amount"
}

private fun stockDraftLabel(change: DraftStockChange): String {
    val qty = if (change.quantityDelta == null) "quantity unknown" else "quantity ${trimNumber(change.quantityDelta)}"
    return "Pantry draft · ${change.rawText} · $qty · receipt ${change.sourceId} line ${change.sourceLineId}"
}
