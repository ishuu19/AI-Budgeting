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

    @Update
    suspend fun update(entity: StudyPlanEntity)

    @Query("SELECT * FROM study_plans WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<StudyPlanEntity>

    @Query("SELECT * FROM study_plans WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): StudyPlanEntity?

    @Query("SELECT * FROM study_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): StudyPlanEntity?
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

    @Update
    suspend fun update(entity: PlanBlockEntity)

    @Query("SELECT * FROM plan_blocks WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<PlanBlockEntity>

    @Query("SELECT * FROM plan_blocks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): PlanBlockEntity?
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

    @Query("UPDATE habits SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Update
    suspend fun update(entity: HabitEntity)

    @Query("SELECT * FROM habits WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): HabitEntity?
}

@Dao
interface HabitLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HabitLogEntity): Long

    @Query("SELECT * FROM habit_logs WHERE date >= :from AND date <= :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<HabitLogEntity>>

    @Update
    suspend fun update(entity: HabitLogEntity)

    @Query("SELECT * FROM habit_logs WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): HabitLogEntity?
}

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusSessionEntity): Long

    @Update
    suspend fun update(entity: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): FocusSessionEntity?
}

@Dao
interface NudgeProposalDao {
    @Query("SELECT * FROM nudge_proposals WHERE state = 'PENDING' ORDER BY suggestedAt ASC")
    fun observePending(): Flow<List<NudgeProposalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NudgeProposalEntity): Long

    @Query("UPDATE nudge_proposals SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    /** How many proposals (any state) this note already has with this exact message. Used to avoid duplicates. */
    @Query("SELECT COUNT(*) FROM nudge_proposals WHERE noteId = :noteId AND message = :message AND deletedAt IS NULL")
    suspend fun countFor(noteId: Long, message: String): Int

    @Update
    suspend fun update(entity: NudgeProposalEntity)

    @Query("SELECT * FROM nudge_proposals WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<NudgeProposalEntity>

    @Query("SELECT * FROM nudge_proposals WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): NudgeProposalEntity?
}

@Dao
interface SpendSpeculationDao {
    @Query("SELECT * FROM spend_speculations WHERE deletedAt IS NULL ORDER BY expectedDate ASC")
    fun observeAll(): Flow<List<SpendSpeculationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpendSpeculationEntity): Long

    @Query("UPDATE spend_speculations SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Update
    suspend fun update(entity: SpendSpeculationEntity)

    @Query("SELECT * FROM spend_speculations WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<SpendSpeculationEntity>

    @Query("SELECT * FROM spend_speculations WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SpendSpeculationEntity?
}

@Dao
interface SpendGuideDayDao {
    @Query("SELECT * FROM spend_guide_days WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: LocalDate): SpendGuideDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpendGuideDayEntity)

    @Update
    suspend fun update(entity: SpendGuideDayEntity)

    @Query("SELECT * FROM spend_guide_days WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<SpendGuideDayEntity>

    @Query("SELECT * FROM spend_guide_days WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SpendGuideDayEntity?
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

    @Update
    suspend fun update(entity: LeaveRuleEntity)

    @Query("SELECT * FROM leave_rules WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<LeaveRuleEntity>

    @Query("SELECT * FROM leave_rules WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): LeaveRuleEntity?
}

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationPointEntity): Long

    @Query("SELECT * FROM location_points ORDER BY ts DESC LIMIT 1")
    suspend fun latest(): LocationPointEntity?

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

    @Query("UPDATE activity_entries SET text = :text, source = :source WHERE id = :id")
    suspend fun updateText(id: Long, text: String, source: String)

    @Update
    suspend fun update(entity: ActivityEntryEntity)

    @Query("SELECT * FROM activity_entries WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<ActivityEntryEntity>

    @Query("SELECT * FROM activity_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ActivityEntryEntity?
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

    @Update
    suspend fun update(entity: CheckinWindowEntity)

    @Query("SELECT * FROM checkin_windows WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<CheckinWindowEntity>

    @Query("SELECT * FROM checkin_windows WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CheckinWindowEntity?
}

@Dao
interface JobApplicationDao {
    @Query("SELECT * FROM job_applications WHERE deletedAt IS NULL ORDER BY appliedOn DESC")
    fun observeAll(): Flow<List<JobApplicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JobApplicationEntity): Long

    @Query("SELECT * FROM job_applications WHERE url = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun findByUrl(url: String): JobApplicationEntity?

    @Query("UPDATE job_applications SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT * FROM job_applications WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): JobApplicationEntity?

    @Update
    suspend fun update(entity: JobApplicationEntity)

    @Query("SELECT * FROM job_applications WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<JobApplicationEntity>

    @Query("SELECT * FROM job_applications WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): JobApplicationEntity?
}
