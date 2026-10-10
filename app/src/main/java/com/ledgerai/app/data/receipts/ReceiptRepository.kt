package com.ledgerai.app.data.receipts

import com.ledgerai.app.domain.receipts.ConfirmedReceipt
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptDrafts
import com.ledgerai.app.domain.receipts.ValueConfidence

interface ReceiptStore {
    suspend fun upsert(receipt: Receipt): Receipt
    suspend fun find(id: Long): Receipt?
}

/**
 * Stores receipts and builds drafts. Does not insert a transaction or change pantry stock.
 */
class ReceiptRepository(
    private val store: ReceiptStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun save(receipt: Receipt): Receipt =
        store.upsert(receipt.copy(updatedAt = now()))

    /**
     * User accepted this receipt. Non-null merchant, date, total, and line
     * prices become [ValueConfidence.CONFIRMED]. Nulls stay null.
     */
    suspend fun confirm(receipt: Receipt): ConfirmedReceipt {
        val stamped = now()
        val confirmed = receipt.copy(
            locallyConfirmed = true,
            merchantConfidence = acceptedConfidence(receipt.merchant, receipt.merchantConfidence),
            purchasedOnConfidence = acceptedConfidence(receipt.purchasedOn, receipt.purchasedOnConfidence),
            totalConfidence = acceptedConfidence(receipt.total, receipt.totalConfidence),
            updatedAt = stamped,
            lines = receipt.lines.map { line ->
                val priceKnown = line.unitPrice != null || line.lineTotal != null
                line.copy(
                    confidence = if (priceKnown) ValueConfidence.CONFIRMED else line.confidence,
                    updatedAt = stamped,
                )
            },
        )
        val saved = store.upsert(confirmed)
        return ConfirmedReceipt(saved, ReceiptDrafts.propose(saved))
    }

    suspend fun find(id: Long): Receipt? = store.find(id)
}

private fun <T> acceptedConfidence(value: T?, current: ValueConfidence?): ValueConfidence? =
    if (value != null) ValueConfidence.CONFIRMED else current

class RoomReceiptStore(private val dao: ReceiptDao) : ReceiptStore {
    override suspend fun upsert(receipt: Receipt): Receipt {
        val (entity, lines) = receipt.toRecords()
        val id = dao.upsert(entity, lines)
        return find(id) ?: error("Receipt $id was not stored")
    }

    override suspend fun find(id: Long): Receipt? {
        val entity = dao.getReceipt(id) ?: return null
        return entity.toDomain(dao.linesFor(id))
    }
}
