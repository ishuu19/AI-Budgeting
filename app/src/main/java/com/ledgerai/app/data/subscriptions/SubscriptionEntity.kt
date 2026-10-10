package com.ledgerai.app.data.subscriptions

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Room row for data-model `subscriptions`. Registered on LedgerDatabase.
 * period and status are wire strings so no new TypeConverter is required.
 * amount null means unknown, not zero.
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index("userId"), Index("updatedAt")]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String? = null,
    val merchant: String,
    val amount: Double? = null,
    val period: String,
    val nextRenewalOn: LocalDate,
    val status: String,
    val sourceId: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val remoteId: String? = null
)
