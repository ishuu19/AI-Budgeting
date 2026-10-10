package com.ledgerai.app.data.receipts

import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptLine
import com.ledgerai.app.domain.receipts.ValueConfidence
import java.time.LocalDate

internal fun ValueConfidence.toStored(): String = name.lowercase()

internal fun String.toConfidence(): ValueConfidence =
    ValueConfidence.valueOf(uppercase())

fun Receipt.toRecords(): Pair<ReceiptEntity, List<ReceiptLineEntity>> {
    val entity = ReceiptEntity(
        id = id,
        userId = userId,
        householdId = householdId,
        merchant = merchant?.takeIf { it.isNotBlank() },
        merchantConfidence = merchantConfidence?.toStored(),
        purchasedOn = purchasedOn?.toString(),
        purchasedOnConfidence = purchasedOnConfidence?.toStored(),
        total = total,
        totalConfidence = totalConfidence?.toStored(),
        currency = currency?.takeIf { it.isNotBlank() },
        paidBy = paidBy?.takeIf { it.isNotBlank() },
        documentId = documentId,
        transactionId = transactionId,
        locallyConfirmed = locallyConfirmed,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
    val lineEntities = lines.map { line ->
        ReceiptLineEntity(
            id = line.id,
            receiptId = id,
            rawText = line.rawText,
            itemId = line.itemId,
            qty = line.qty,
            unitPrice = line.unitPrice,
            lineTotal = line.lineTotal,
            confidence = line.confidence.toStored(),
            updatedAt = line.updatedAt,
            deletedAt = line.deletedAt,
        )
    }
    return entity to lineEntities
}

fun ReceiptEntity.toDomain(lines: List<ReceiptLineEntity>): Receipt = Receipt(
    id = id,
    userId = userId,
    householdId = householdId,
    merchant = merchant?.takeIf { it.isNotBlank() },
    merchantConfidence = merchantConfidence?.toConfidence(),
    purchasedOn = purchasedOn?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
    purchasedOnConfidence = purchasedOnConfidence?.toConfidence(),
    total = total,
    totalConfidence = totalConfidence?.toConfidence(),
    currency = currency?.takeIf { it.isNotBlank() },
    paidBy = paidBy?.takeIf { it.isNotBlank() },
    documentId = documentId,
    transactionId = transactionId,
    lines = lines.map { it.toDomain() },
    locallyConfirmed = locallyConfirmed,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun ReceiptLineEntity.toDomain(): ReceiptLine = ReceiptLine(
    id = id,
    receiptId = receiptId,
    rawText = rawText,
    itemId = itemId,
    qty = qty,
    unitPrice = unitPrice,
    lineTotal = lineTotal,
    confidence = confidence.toConfidence(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
