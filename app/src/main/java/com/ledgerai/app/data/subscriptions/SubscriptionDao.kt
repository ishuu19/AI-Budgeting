package com.ledgerai.app.data.subscriptions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubscriptionEntity): Long

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAt IS NULL
          AND status = 'active'
          AND ((userId IS NULL AND :userId IS NULL) OR userId = :userId)
        ORDER BY nextRenewalOn ASC, id ASC
        """
    )
    suspend fun listActive(userId: String?): List<SubscriptionEntity>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAt IS NULL
          AND status = 'active'
          AND ((userId IS NULL AND :userId IS NULL) OR userId = :userId)
        ORDER BY nextRenewalOn ASC, id ASC
        """
    )
    fun observeActive(userId: String?): Flow<List<SubscriptionEntity>>

    /**
     * Local status only. Writes cancel_requested for an active row in this user scope.
     * Does not set cancelled_confirmed and does not contact a merchant.
     */
    @Query(
        """
        UPDATE subscriptions
        SET status = :status, updatedAt = :now
        WHERE id = :id
          AND deletedAt IS NULL
          AND status = 'active'
          AND ((userId IS NULL AND :userId IS NULL) OR userId = :userId)
        """
    )
    suspend fun updateStatus(id: Long, userId: String?, status: String, now: Long): Int
}
