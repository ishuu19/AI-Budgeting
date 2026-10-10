package com.ledgerai.app.domain.receipts

import java.time.LocalDate

/** Source type stored on drafts so money and pantry rows can point back here. */
const val RECEIPT_SOURCE_TYPE = "receipt"

/**
 * Expense description for the existing transaction layer to insert later.
 * Null amount, merchant, or date means unknown. Nothing here has been written.
 */
data class DraftExpense(
    val amount: Double?,
    val amountConfidence: ValueConfidence?,
    val type: String,
    val category: String?,
    val merchant: String?,
    val merchantConfidence: ValueConfidence?,
    val date: LocalDate?,
    val dateConfidence: ValueConfidence?,
    val currency: String?,
    val paidBy: String?,
    val note: String,
    val sourceType: String,
    val sourceId: Long,
    val documentId: String?,
)

/**
 * Proposed pantry quantity change. [quantityDelta], [unitPrice], and [lineTotal]
 * stay null when the receipt did not contain them.
 */
data class DraftStockChange(
    val itemId: String?,
    val rawText: String,
    val quantityDelta: Double?,
    val unitPrice: Double?,
    val lineTotal: Double?,
    val confidence: ValueConfidence,
    val sourceType: String,
    val sourceId: Long,
    val sourceLineId: Long,
)

data class ReceiptProposals(
    val expense: DraftExpense,
    val stockChanges: List<DraftStockChange>,
)

/** A receipt stored as locally confirmed, plus the drafts it proposes. Nothing is spent yet. */
data class ConfirmedReceipt(
    val receipt: Receipt,
    val proposals: ReceiptProposals,
)

object ReceiptDrafts {
    /** Projection only. Does not write money or inventory, and does not fill gaps. */
    fun propose(receipt: Receipt): ReceiptProposals {
        val merchant = receipt.merchant?.takeIf { it.isNotBlank() }
        return ReceiptProposals(
            expense = DraftExpense(
                amount = receipt.total,
                amountConfidence = if (receipt.total == null) null else receipt.totalConfidence,
                type = "EXPENSE",
                category = null,
                merchant = merchant,
                merchantConfidence = if (merchant == null) null else receipt.merchantConfidence,
                date = receipt.purchasedOn,
                dateConfidence = if (receipt.purchasedOn == null) null else receipt.purchasedOnConfidence,
                currency = receipt.currency?.takeIf { it.isNotBlank() },
                paidBy = receipt.paidBy?.takeIf { it.isNotBlank() },
                note = receipt.lines.joinToString("\n") { it.rawText },
                sourceType = RECEIPT_SOURCE_TYPE,
                sourceId = receipt.id,
                documentId = receipt.documentId,
            ),
            stockChanges = receipt.lines.map { line ->
                DraftStockChange(
                    itemId = line.itemId,
                    rawText = line.rawText,
                    quantityDelta = line.qty,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    confidence = line.confidence,
                    sourceType = RECEIPT_SOURCE_TYPE,
                    sourceId = receipt.id,
                    sourceLineId = line.id,
                )
            },
        )
    }
}
