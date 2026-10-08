package com.ledgerai.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plans WHERE deletedAt IS NULL ORDER BY id DESC")
    fun observeAll(): Flow<List<StudyPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StudyPlanEntity): Long
}

@Dao
interface PlanBlockDao {
    @Query(
        """
        SELECT * FROM plan_blocks
        WHERE deletedAt IS NULL AND startAt >= :from AND startAt < :to
        ORDER BY startAt ASC
        """
    )
    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<PlanBlockEntity>>

    @Query("SELECT * FROM plan_blocks WHERE deletedAt IS NULL ORDER BY startAt ASC")
    fun observeAll(): Flow<List<PlanBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlanBlockEntity): Long

    @Query("UPDATE plan_blocks SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE plan_blocks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    @Query("SELECT * FROM plan_blocks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlanBlockEntity?

    @Query(
        """
        SELECT * FROM plan_blocks
        WHERE deletedAt IS NULL AND status = 'SCHEDULED' AND startAt > :now
        ORDER BY startAt ASC
        """
    )
    suspend fun listFutureScheduled(now: LocalDateTime): List<PlanBlockEntity>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY title ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HabitEntity): Long

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL AND nudgeEnabled = 1")
    suspend fun listNudgeEnabled(): List<HabitEntity>
}

@Dao
interface HabitLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HabitLogEntity): Long
}

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusSessionEntity): Long

    @Update
    suspend fun update(entity: FocusSessionEntity)
}

@Dao
interface NudgeProposalDao {
    @Query("SELECT * FROM nudge_proposals WHERE state = 'PENDING' ORDER BY suggestedAt ASC")
    fun observePending(): Flow<List<NudgeProposalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NudgeProposalEntity): Long

    @Query("UPDATE nudge_proposals SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)
}

@Dao
interface SpendSpeculationDao {
    @Query("SELECT * FROM spend_speculations WHERE deletedAt IS NULL ORDER BY expectedDate ASC")
    fun observeAll(): Flow<List<SpendSpeculationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpendSpeculationEntity): Long

    @Query("UPDATE spend_speculations SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface SpendGuideDayDao {
    @Query("SELECT * FROM spend_guide_days WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: LocalDate): SpendGuideDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpendGuideDayEntity)
}

@Dao
interface LeaveRuleDao {
    @Query("SELECT * FROM leave_rules WHERE refType = :refType AND refId = :refId LIMIT 1")
    suspend fun getForRef(refType: String, refId: Long): LeaveRuleEntity?

    @Query("SELECT * FROM leave_rules WHERE enabled = 1")
    suspend fun listEnabled(): List<LeaveRuleEntity>

    @Query("DELETE FROM leave_rules WHERE refType = :refType AND refId = :refId")
    suspend fun deleteForRef(refType: String, refId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LeaveRuleEntity): Long
}

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationPointEntity): Long

    @Query("DELETE FROM location_points WHERE ts < :before")
    suspend fun pruneBefore(before: LocalDateTime)
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY arrivedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<VisitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VisitEntity): Long
}

@Dao
interface ActivityEntryDao {
    @Query("SELECT * FROM activity_entries WHERE startAt >= :dayStart AND startAt < :dayEnd ORDER BY startAt ASC")
    fun observeDay(dayStart: LocalDateTime, dayEnd: LocalDateTime): Flow<List<ActivityEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityEntryEntity): Long
}

@Dao
interface CheckinWindowDao {
    @Query("SELECT * FROM checkin_windows WHERE startAt >= :dayStart AND startAt < :dayEnd ORDER BY startAt ASC")
    fun observeDay(dayStart: LocalDateTime, dayEnd: LocalDateTime): Flow<List<CheckinWindowEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM checkin_windows
        WHERE startAt = :start AND endAt = :end
        """
    )
    suspend fun countWindow(start: LocalDateTime, end: LocalDateTime): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CheckinWindowEntity): Long

    @Query("UPDATE checkin_windows SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("UPDATE checkin_windows SET state = 'GAP' WHERE state = 'PENDING' AND endAt < :now")
    suspend fun markExpiredGaps(now: LocalDateTime)
}

@Dao
interface JobApplicationDao {
    @Query("SELECT * FROM job_applications WHERE deletedAt IS NULL ORDER BY appliedOn DESC")
    fun observeAll(): Flow<List<JobApplicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JobApplicationEntity): Long

    @Query("SELECT * FROM job_applications WHERE url = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun findByUrl(url: String): JobApplicationEntity?
}
