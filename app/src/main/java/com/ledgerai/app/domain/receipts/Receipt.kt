package com.ledgerai.app.domain.receipts

import java.time.LocalDate

/** User-accepted value, or a value the model produced. Unknown values stay null. */
enum class ValueConfidence { CONFIRMED, INFERRED }

/**
 * Local receipt. Columns follow docs/life-os/data-model.md, plus a confidence
 * mark on each extracted header value and a local review flag.
 * [transactionId] is only a link. This type does not create a transaction.
 */
data class Receipt(
    val id: Long = 0,
    val userId: String? = null,
    val householdId: String? = null,
    val merchant: String? = null,
    val merchantConfidence: ValueConfidence? = null,
    val purchasedOn: LocalDate? = null,
    val purchasedOnConfidence: ValueConfidence? = null,
    val total: Double? = null,
    val totalConfidence: ValueConfidence? = null,
    val currency: String? = null,
    val paidBy: String? = null,
    val documentId: String? = null,
    val transactionId: String? = null,
    val lines: List<ReceiptLine> = emptyList(),
    val locallyConfirmed: Boolean = false,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** One line. [confidence] is the data-model mark for this extracted line. */
data class ReceiptLine(
    val id: Long = 0,
    val receiptId: Long = 0,
    val rawText: String,
    val itemId: String? = null,
    val qty: Double? = null,
    val unitPrice: Double? = null,
    val lineTotal: Double? = null,
    val confidence: ValueConfidence,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
