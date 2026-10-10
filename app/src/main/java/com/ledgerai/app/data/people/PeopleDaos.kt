package com.ledgerai.app.data.people

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PersonDao {
    @Query("SELECT * FROM people")
    suspend fun listAll(): List<PersonEntity>

    @Upsert
    suspend fun upsert(entity: PersonEntity)
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions")
    suspend fun listAll(): List<InteractionEntity>

    @Upsert
    suspend fun upsert(entity: InteractionEntity)
}

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments")
    suspend fun listAll(): List<CommitmentEntity>

    @Upsert
    suspend fun upsert(entity: CommitmentEntity)
}
