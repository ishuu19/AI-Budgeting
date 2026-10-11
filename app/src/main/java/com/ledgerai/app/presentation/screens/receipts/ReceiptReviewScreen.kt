package com.ledgerai.app.presentation.screens.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

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
    val editing = editingIndex

    LScreen(title = "Receipt", onBack = onBack) {
        receipt.documentId?.let { photoId ->
            item(key = "photo") {
                val file = java.io.File(androidx.compose.ui.platform.LocalContext.current.filesDir, "media/$photoId.jpg")
                if (file.exists()) {
                    com.ledgerai.app.presentation.components.LocalPhoto(
                        file,
                        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(L.Radius)),
                        androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
        }
        item(key = "total") {
            ColumnTotal(receipt)
        }
        item(key = "header") {
            LGroup {
                HeaderRow("Merchant", receipt.merchant, receipt.merchantConfidence)
                LGroupDivider()
                HeaderRow("Date", receipt.purchasedOn?.format(dateFormat), receipt.purchasedOnConfidence)
                if (!receipt.paidBy.isNullOrBlank()) {
                    LGroupDivider()
                    HeaderRow("Paid by", receipt.paidBy, null)
                }
            }
        }
        item(key = "lines-header") { LSection("Lines") }
        if (editedLines.isEmpty()) {
            item(key = "lines-empty") {
                Text("No lines", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            }
        } else {
            item(key = "lines") {
                LGroup {
                    editedLines.forEachIndexed { index, line ->
                        if (index > 0) LGroupDivider()
                        LineRow(
                            line,
                            onClick = if (canEdit) {
                                {
                                    editingIndex = index
                                    draftName = line.rawText
                                    draftPrice = priceText(line.unitPrice)
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
        if (proposals != null) {
            item(key = "drafts") { DraftSection(proposals) }
        }
        item(key = "confirm") {
            if (receipt.locallyConfirmed) {
                SavedChip()
            } else {
                LButton(
                    text = "Confirm receipt",
                    onClick = {
                        onConfirm(receipt.copy(lines = linesForConfirm(receipt, editedLines, editingIndex, draftName, draftPrice)))
                    },
                    enabled = confirmEnabled,
                )
            }
        }
    }

    if (editing != null && canEdit) {
        LSheet(
            title = "Edit line",
            onDismiss = { editingIndex = null },
            primary = "Done",
            onPrimary = {
                val original = receipt.lines.getOrNull(editing) ?: editedLines.getOrNull(editing)
                if (original != null) {
                    editedLines = editedLines.replaceAt(editing, correctedLine(original, draftName, draftPrice))
                }
                editingIndex = null
            }
        ) {
            LField(value = draftName, onValueChange = { draftName = it }, label = "Name")
            LField(
                value = draftPrice,
                onValueChange = { draftPrice = it },
                label = "Price",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@Composable
private fun ColumnTotal(receipt: Receipt) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LHero(label = "Total", value = amountLabel(receipt.total, receipt.currency))
        if (confidenceMark(receipt.totalConfidence, receipt.total == null) != null) InferredChip()
    }
}

@Composable
private fun HeaderRow(label: String, value: String?, confidence: ValueConfidence?) {
    val shown = value?.takeIf { it.isNotBlank() } ?: "Unknown"
    LGroupRow(
        title = label,
        trailing = shown,
        end = if (confidenceMark(confidence, value.isNullOrBlank()) != null) {
            { InferredChip() }
        } else {
            null
        }
    )
}

@Composable
private fun LineRow(line: ReceiptLine, onClick: (() -> Unit)?) {
    val total = line.lineTotal ?: if (line.qty != null && line.unitPrice != null) line.qty * line.unitPrice else line.unitPrice
    LGroupRow(
        title = line.rawText,
        sub = lineDetail(line),
        trailing = total?.let { money(it) },
        onClick = onClick,
        end = if (line.confidence == ValueConfidence.INFERRED) {
            { InferredChip() }
        } else {
            null
        }
    )
}

@Composable
private fun DraftSection(proposals: ReceiptProposals) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LSection("Drafts")
        LGroup {
            LGroupRow(title = expenseDraftLabel(proposals.expense))
            proposals.stockChanges.forEach { change ->
                LGroupDivider()
                LGroupRow(title = stockDraftLabel(change))
            }
        }
    }
}

@Composable
private fun InferredChip() {
    Text(
        "Inferred",
        style = MaterialTheme.typography.labelSmall,
        color = L.Highlight,
        modifier = Modifier
            .clip(L.RadiusPill)
            .background(L.Box)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SavedChip() {
    Text(
        "Saved",
        style = MaterialTheme.typography.labelLarge,
        color = L.Primary,
        modifier = Modifier
            .clip(L.RadiusPill)
            .background(L.GoldSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Suppress("UNUSED_PARAMETER")
internal fun amountLabel(amount: Double?, currency: String?): String {
    if (amount == null) return "Unknown"
    return money(amount)
}

internal fun confidenceMark(confidence: ValueConfidence?, valueUnknown: Boolean): String? =
    if (!valueUnknown && confidence == ValueConfidence.INFERRED) "Inferred" else null

private fun lineDetail(line: ReceiptLine): String? {
    val qty = line.qty
    val price = line.unitPrice
    return when {
        qty != null && price != null -> "${trimNumber(qty)} × ${money(price)}"
        price != null -> money(price)
        qty != null -> trimNumber(qty)
        else -> null
    }
}

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
    val merchant = expense.merchant
    return if (merchant.isNullOrBlank()) amount else "$merchant · $amount"
}

private fun stockDraftLabel(change: DraftStockChange): String {
    val qty = change.quantityDelta?.let { " +${trimNumber(it)}" }.orEmpty()
    return "${change.rawText}$qty to pantry"
}
