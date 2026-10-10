package com.ledgerai.app.data.receipts

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String? = null,
    val householdId: String? = null,
    val merchant: String? = null,
    val merchantConfidence: String? = null,
    val purchasedOn: String? = null,
    val purchasedOnConfidence: String? = null,
    val total: Double? = null,
    val totalConfidence: String? = null,
    val currency: String? = null,
    val paidBy: String? = null,
    val documentId: String? = null,
    val transactionId: String? = null,
    val locallyConfirmed: Boolean = false,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("receiptId")],
)
data class ReceiptLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val rawText: String,
    val itemId: String? = null,
    val qty: Double? = null,
    val unitPrice: Double? = null,
    val lineTotal: Double? = null,
    val confidence: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
