package com.ledgerai.app.data.receipts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Dates and confidence are stored as text so no new type converter is required.
 * Null prices stay null.
 */
@Dao
abstract class ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReceipt(entity: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLines(entities: List<ReceiptLineEntity>)

    @Query("DELETE FROM receipt_lines WHERE receiptId = :receiptId")
    abstract suspend fun deleteLines(receiptId: Long)

    @Query("SELECT * FROM receipts WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getReceipt(id: Long): ReceiptEntity?

    @Query(
        """
        SELECT * FROM receipt_lines
        WHERE receiptId = :receiptId AND deletedAt IS NULL
        ORDER BY id
        """
    )
    abstract suspend fun linesFor(receiptId: Long): List<ReceiptLineEntity>

    @Transaction
    open suspend fun upsert(receipt: ReceiptEntity, lines: List<ReceiptLineEntity>): Long {
        val generated = insertReceipt(receipt)
        val id = if (receipt.id == 0L) generated else receipt.id
        deleteLines(id)
        if (lines.isNotEmpty()) {
            insertLines(lines.map { it.copy(receiptId = id) })
        }
        return id
    }
}
