package com.ledgerai.app.data.wardrobe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeItemDao {

    @Query(
        """
        SELECT * FROM wardrobe_items
        WHERE userId = :userId AND deletedAt IS NULL
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeActive(userId: String): Flow<List<WardrobeItemEntity>>

    /** Includes every user and soft-deleted rows. The repository applies visibility. */
    @Query("SELECT * FROM wardrobe_items")
    suspend fun listAll(): List<WardrobeItemEntity>

    @Query("SELECT * FROM wardrobe_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WardrobeItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WardrobeItemEntity): Long

    @Query(
        """
        UPDATE wardrobe_items
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id AND userId = :userId AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(id: Long, userId: String, deletedAt: Long, updatedAt: Long)
}

@Dao
interface OutfitDao {

    @Query(
        """
        SELECT * FROM outfits
        WHERE userId = :userId AND deletedAt IS NULL
        ORDER BY wornOn DESC
        """
    )
    fun observeActive(userId: String): Flow<List<OutfitEntity>>

    @Query("SELECT * FROM outfits")
    suspend fun listAll(): List<OutfitEntity>

    @Query("SELECT * FROM outfits WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OutfitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutfitEntity): Long

    @Query(
        """
        UPDATE outfits
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id AND userId = :userId AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(id: Long, userId: String, deletedAt: Long, updatedAt: Long)
}
