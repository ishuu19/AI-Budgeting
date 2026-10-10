package com.ledgerai.app.data.inventory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ItemEntity>>

    /** Includes soft-deleted rows. Callers that show stock must filter [ItemEntity.deletedAt]. */
    @Query("SELECT * FROM items")
    suspend fun listAll(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ItemEntity): Long

    @Update
    suspend fun update(entity: ItemEntity)

    @Query("UPDATE items SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}

@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists")
    suspend fun listAll(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShoppingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShoppingListEntity): Long

    @Update
    suspend fun update(entity: ShoppingListEntity)

    @Query("UPDATE shopping_lists SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}

@Dao
interface ShoppingItemDao {

    @Query(
        """
        SELECT * FROM shopping_items
        WHERE listId = :listId AND deletedAt IS NULL
        ORDER BY itemName COLLATE NOCASE
        """
    )
    fun observeForList(listId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items")
    suspend fun listAll(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShoppingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ShoppingItemEntity): Long

    @Update
    suspend fun update(entity: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}
