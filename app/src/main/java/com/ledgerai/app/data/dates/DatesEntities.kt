package com.ledgerai.app.data.dates

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
 * `birthdays`: a yearly date for a person. Not a calendar row.
 * [personId] points at `people.id` (no foreign key, so a deleted person never blocks a delete).
 * [celebrateMinute] is minutes after local midnight; 0 is the wish-at-midnight default.
 * The nine `r*` flags are the reminder ladder, see `domain/dates/BirthdayLadder`.
 */
@Entity(
    tableName = "birthdays",
    indices = [Index("userId"), Index("personId"), Index("updatedAt")]
)
data class BirthdayEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val personId: String,
    val month: Int,
    val day: Int,
    val birthYear: Int? = null,
    val celebrateMinute: Int = 0,
    val note: String = "",
    val r1mo: Boolean = true,
    val r2w: Boolean = true,
    val r1w: Boolean = true,
    val r3d: Boolean = true,
    val r1d: Boolean = true,
    val r5h: Boolean = true,
    val r30m: Boolean = true,
    val r10m: Boolean = true,
    val rAt: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** `trips`: a named date range and a place. Status is derived from the dates, never stored. */
@Entity(
    tableName = "trips",
    indices = [Index("userId"), Index("startDate"), Index("updatedAt")]
)
data class TripEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val name: String,
    val place: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String = "",
    val r1w: Boolean = true,
    val r1d: Boolean = true,
    val visibility: String = "private",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays WHERE deletedAt IS NULL ORDER BY month, day")
    fun observeActive(): Flow<List<BirthdayEntity>>

    @Query("SELECT * FROM birthdays WHERE deletedAt IS NULL")
    suspend fun listActive(): List<BirthdayEntity>

    @Query("SELECT * FROM birthdays WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BirthdayEntity?

    @Query("SELECT * FROM birthdays WHERE personId = :personId AND deletedAt IS NULL LIMIT 1")
    suspend fun forPerson(personId: String): BirthdayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BirthdayEntity)

    @Query("SELECT * FROM birthdays WHERE updatedAt > :sinceMs")
    suspend fun listForSync(sinceMs: Long): List<BirthdayEntity>

    @Query("UPDATE birthdays SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE deletedAt IS NULL ORDER BY startDate")
    fun observeActive(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE deletedAt IS NULL")
    suspend fun listActive(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun get(id: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TripEntity)

    @Query("SELECT * FROM trips WHERE updatedAt > :sinceMs")
    suspend fun listForSync(sinceMs: Long): List<TripEntity>

    @Query("UPDATE trips SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
