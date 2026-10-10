package com.ledgerai.app.data.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories")
    suspend fun listAll(): List<MemoryEntity>

    @Upsert
    suspend fun upsert(entity: MemoryEntity)
}
