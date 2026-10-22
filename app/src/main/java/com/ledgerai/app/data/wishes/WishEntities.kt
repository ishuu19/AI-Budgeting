package com.ledgerai.app.data.wishes

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `wishes`: a future purchase. Not a shopping item and not a goal.
 * [amount] is null when the price is unknown; it is never filled by the model.
 * [wantedBy] is a `yyyy-MM` month. [goalId] and [transactionId] are set by Start saving and Bought.
 */
@Entity(
    tableName = "wishes",
    indices = [Index("userId"), Index("status"), Index("updatedAt")]
)
data class WishEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val name: String,
    val amount: Double? = null,
    val priority: Int = 2,
    val wantedBy: String? = null,
    val link: String? = null,
    val note: String = "",
    val status: String = WishStatus.WANT,
    val goalId: String? = null,
    val transactionId: String? = null,
    val visibility: String = "private",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

object WishStatus {
    const val WANT = "WANT"
    const val SAVING = "SAVING"
    const val BOUGHT = "BOUGHT"
    const val DROPPED = "DROPPED"
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes WHERE deletedAt IS NULL ORDER BY priority DESC, createdAt DESC")
    fun observeActive(): Flow<List<WishEntity>>

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    suspend fun get(id: String): WishEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WishEntity)

    @Query("SELECT * FROM wishes WHERE updatedAt > :sinceMs")
    suspend fun listForSync(sinceMs: Long): List<WishEntity>

    @Query("UPDATE wishes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
