package com.ledgerai.app.data.reminders

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * `reminders`: every pending notification instant, derived from its source row and rebuilt on demand.
 * Device only. The engine arms one alarm for the earliest PENDING row.
 */
@Entity(
    tableName = "reminders",
    indices = [Index("fireAt"), Index("sourceType", "sourceId")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceId: String,
    val rung: String,
    val fireAt: Long,
    val state: String = ReminderState.PENDING,
    val title: String,
    val body: String,
)

object ReminderState {
    const val PENDING = "PENDING"
    const val DELIVERED = "DELIVERED"
    const val SKIPPED = "SKIPPED"
}

object ReminderSource {
    const val BIRTHDAY = "birthday"
    const val TRIP = "trip"
}

@Dao
interface ReminderDao {
    /** Ignores ids that already exist, so a DELIVERED row is never turned back into PENDING by a rebuild. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rows: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE state = 'PENDING' ORDER BY fireAt LIMIT 1")
    suspend fun next(): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE state = 'PENDING' AND fireAt <= :now ORDER BY fireAt")
    suspend fun due(now: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET state = :state WHERE id IN (:ids)")
    suspend fun mark(ids: List<String>, state: String)

    @Query("DELETE FROM reminders WHERE sourceType = :type AND sourceId = :id")
    suspend fun deleteForSource(type: String, id: String)

    @Query("DELETE FROM reminders WHERE sourceType = :type AND sourceId = :id AND state = 'PENDING'")
    suspend fun deletePendingForSource(type: String, id: String)

    @Query("DELETE FROM reminders WHERE sourceType = :type AND state = 'PENDING'")
    suspend fun deletePendingForType(type: String)

    @Query("SELECT COUNT(*) FROM reminders WHERE state = 'PENDING'")
    suspend fun pendingCount(): Int
}
