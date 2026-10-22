package com.ledgerai.app.data.appusage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * `app_usage_days`: foreground time per app per local day. Device only.
 * Written by the hourly usage worker. Never synced and never read by the assistant.
 */
@Entity(
    tableName = "app_usage_days",
    primaryKeys = ["day", "packageName"],
    indices = [Index("day")]
)
data class AppUsageDayEntity(
    val day: LocalDate,
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val launches: Int = 0,
)

data class AppTotal(val packageName: String, val label: String, val foregroundMs: Long, val launches: Int)

data class DayTotal(val day: LocalDate, val ms: Long)

@Dao
interface AppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AppUsageDayEntity>)

    @Query("SELECT * FROM app_usage_days WHERE day = :day ORDER BY foregroundMs DESC")
    fun observeDay(day: LocalDate): Flow<List<AppUsageDayEntity>>

    @Query(
        """
        SELECT packageName, MAX(label) AS label, SUM(foregroundMs) AS foregroundMs, SUM(launches) AS launches
        FROM app_usage_days WHERE day BETWEEN :from AND :to
        GROUP BY packageName ORDER BY foregroundMs DESC
        """
    )
    fun observeTotals(from: LocalDate, to: LocalDate): Flow<List<AppTotal>>

    @Query("SELECT day, SUM(foregroundMs) AS ms FROM app_usage_days WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day")
    fun observeDaily(from: LocalDate, to: LocalDate): Flow<List<DayTotal>>

    @Query("SELECT * FROM app_usage_days WHERE packageName = :pkg AND day BETWEEN :from AND :to ORDER BY day")
    suspend fun history(pkg: String, from: LocalDate, to: LocalDate): List<AppUsageDayEntity>

    @Query("DELETE FROM app_usage_days WHERE day < :before")
    suspend fun pruneBefore(before: LocalDate)
}
