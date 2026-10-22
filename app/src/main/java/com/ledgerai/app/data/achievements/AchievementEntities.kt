package com.ledgerai.app.data.achievements

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * `achievements`: a finished thing. No points, streak or reminder.
 * Photos are `media_assets` rows with `linkedType = 'achievement'`.
 * [sourceTaskId] is the local `calendar_events.id` of a task saved as an achievement.
 */
@Entity(
    tableName = "achievements",
    indices = [Index("userId"), Index("happenedOn"), Index("updatedAt")]
)
data class AchievementEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val title: String,
    val happenedOn: LocalDate,
    val note: String = "",
    val sourceTaskId: Long? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE deletedAt IS NULL ORDER BY happenedOn DESC, updatedAt DESC")
    fun observeActive(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AchievementEntity?

    @Query("SELECT * FROM achievements WHERE sourceTaskId = :taskId AND deletedAt IS NULL LIMIT 1")
    suspend fun forTask(taskId: Long): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AchievementEntity)

    @Query("SELECT * FROM achievements WHERE updatedAt > :sinceMs")
    suspend fun listForSync(sinceMs: Long): List<AchievementEntity>

    @Query("UPDATE achievements SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
