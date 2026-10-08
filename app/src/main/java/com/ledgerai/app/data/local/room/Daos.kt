package com.ledgerai.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY date DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND date >= :startInclusive
          AND date < :endExclusive
        ORDER BY date DESC
        """
    )
    fun observeForMonth(startInclusive: String, endExclusive: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND type = :type
          AND date >= :startInclusive
          AND date < :endExclusive
        """
    )
    suspend fun sumByTypeForMonth(
        type: TransactionType,
        startInclusive: String,
        endExclusive: String
    ): Double

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE deletedAt IS NULL
          AND type = :type
          AND category = :category
          AND date >= :startInclusive
          AND date < :endExclusive
        """
    )
    suspend fun sumByCategoryForMonth(
        type: TransactionType,
        category: TransactionCategory,
        startInclusive: String,
        endExclusive: String
    ): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM transactions WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TransactionEntity?
}

@Dao
interface BudgetDao {

    @Query(
        """
        SELECT * FROM budgets
        WHERE deletedAt IS NULL AND month = :month AND year = :year
        """
    )
    fun observeForMonth(month: Int, year: Int): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE deletedAt IS NULL
          AND category = :category
          AND month = :month
          AND year = :year
        LIMIT 1
        """
    )
    suspend fun getForCategory(category: TransactionCategory, month: Int, year: Int): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BudgetEntity): Long

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("UPDATE budgets SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM budgets WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BudgetEntity?
}

@Dao
interface DebtDao {

    @Query(
        """
        SELECT * FROM debts
        WHERE deletedAt IS NULL AND isPaid = 0
        ORDER BY dueDate ASC
        """
    )
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE deletedAt IS NULL ORDER BY dateLent DESC")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): DebtEntity?

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM debts
        WHERE deletedAt IS NULL AND isPaid = 0 AND direction = :direction
        """
    )
    suspend fun sumByDirection(direction: DebtDirection): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DebtEntity): Long

    @Update
    suspend fun update(entity: DebtEntity)

    @Query("UPDATE debts SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM debts WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): DebtEntity?
}

@Dao
interface GoalDao {

    @Query(
        """
        SELECT * FROM goals
        WHERE deletedAt IS NULL AND isCompleted = 0
        """
    )
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GoalEntity): Long

    @Update
    suspend fun update(entity: GoalEntity)

    @Query("UPDATE goals SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM goals WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): GoalEntity?
}

@Dao
interface BillDao {

    @Query(
        """
        SELECT * FROM bills
        WHERE deletedAt IS NULL AND isActive = 1
        ORDER BY nextDueDate ASC
        """
    )
    fun observeActive(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<BillEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM bills
        WHERE deletedAt IS NULL AND isActive = 1 AND frequency = :frequency
        """
    )
    suspend fun sumByFrequency(frequency: BillFrequency): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BillEntity): Long

    @Update
    suspend fun update(entity: BillEntity)

    @Query("UPDATE bills SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM bills WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BillEntity?
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeWithReminders(): Flow<List<TaskWithReminders>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL AND isCompleted = 0
        ORDER BY dueAt ASC
        """
    )
    fun observeIncomplete(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    /** Includes soft-deleted rows (sync / reminder FK resolution). */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getByIdAny(id: Long): TaskEntity?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getWithReminders(id: Long): TaskWithReminders?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM tasks WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TaskEntity?
}

@Dao
interface TaskReminderDao {

    @Query(
        """
        SELECT * FROM task_reminders
        WHERE deletedAt IS NULL AND taskId = :taskId
        ORDER BY remindAt ASC
        """
    )
    fun observeForTask(taskId: Long): Flow<List<TaskReminderEntity>>

    @Query(
        """
        SELECT * FROM task_reminders
        WHERE deletedAt IS NULL AND taskId = :taskId
        ORDER BY remindAt ASC
        """
    )
    suspend fun listForTask(taskId: Long): List<TaskReminderEntity>

    @Query(
        """
        SELECT COUNT(*) FROM task_reminders
        WHERE deletedAt IS NULL AND taskId = :taskId
        """
    )
    suspend fun countActiveForTask(taskId: Long): Int

    @Query("SELECT * FROM task_reminders WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): TaskReminderEntity?

    @Query(
        """
        SELECT * FROM task_reminders
        WHERE deletedAt IS NULL AND isEnabled = 1
        ORDER BY remindAt ASC
        """
    )
    suspend fun listEnabled(): List<TaskReminderEntity>

    @Query(
        """
        SELECT * FROM task_reminders
        WHERE deletedAt IS NULL AND taskId = :taskId AND isEnabled = 1
        """
    )
    suspend fun listEnabledForTask(taskId: Long): List<TaskReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskReminderEntity): Long

    @Update
    suspend fun update(entity: TaskReminderEntity)

    @Query(
        """
        UPDATE task_reminders
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE task_reminders
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE taskId = :taskId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteForTask(taskId: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM task_reminders WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<TaskReminderEntity>

    @Query("SELECT * FROM task_reminders WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): TaskReminderEntity?
}

@Dao
interface RoutineDao {

    @Query(
        """
        SELECT * FROM routines
        WHERE deletedAt IS NULL AND isActive = 1
        ORDER BY title ASC
        """
    )
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY title ASC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoutineEntity): Long

    @Update
    suspend fun update(entity: RoutineEntity)

    @Query("UPDATE routines SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM routines WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): RoutineEntity?
}

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL ORDER BY time ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query(
        """
        SELECT * FROM alarms
        WHERE deletedAt IS NULL AND isEnabled = 1
        ORDER BY time ASC
        """
    )
    fun observeEnabled(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlarmEntity): Long

    @Update
    suspend fun update(entity: AlarmEntity)

    @Query("UPDATE alarms SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM alarms WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): AlarmEntity?
}

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY editedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteEntity): Long

    @Update
    suspend fun update(entity: NoteEntity)

    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM notes WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): NoteEntity?
}

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE deletedAt IS NULL ORDER BY code ASC, name ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query(
        """
        SELECT * FROM courses
        WHERE deletedAt IS NULL AND code = :code COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun findByCode(code: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CourseEntity): Long

    @Update
    suspend fun update(entity: CourseEntity)

    @Query("UPDATE courses SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}

@Dao
interface ScheduleSlotDao {

    @Transaction
    @Query(
        """
        SELECT * FROM schedule_slots
        WHERE deletedAt IS NULL AND routineId = :routineId
        ORDER BY dayOfWeek ASC, startTime ASC
        """
    )
    fun observeWithReminders(routineId: Long): Flow<List<ScheduleSlotWithReminders>>

    @Query("SELECT * FROM schedule_slots WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): ScheduleSlotEntity?

    @Transaction
    @Query("SELECT * FROM schedule_slots WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getWithReminders(id: Long): ScheduleSlotWithReminders?

    @Query(
        """
        SELECT id FROM schedule_slots
        WHERE routineId = :routineId AND deletedAt IS NULL
        """
    )
    suspend fun listActiveIds(routineId: Long): List<Long>

    @Query("SELECT * FROM schedule_slots WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<ScheduleSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleSlotEntity): Long

    @Update
    suspend fun update(entity: ScheduleSlotEntity)

    @Query(
        """
        UPDATE schedule_slots
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE routineId = :routineId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteForRoutine(routineId: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE schedule_slots
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}

@Dao
interface ScheduleSlotExceptionDao {

    @Query("SELECT * FROM schedule_slot_exceptions")
    fun observeAll(): Flow<List<ScheduleSlotExceptionEntity>>

    @Query("SELECT * FROM schedule_slot_exceptions WHERE slotId = :slotId")
    suspend fun listForSlot(slotId: Long): List<ScheduleSlotExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ScheduleSlotExceptionEntity)

    @Query("DELETE FROM schedule_slot_exceptions WHERE slotId = :slotId")
    suspend fun clearForSlot(slotId: Long)
}

@Dao
interface RoutineSlotReminderDao {

    @Query(
        """
        SELECT COUNT(*) FROM routine_slot_reminders
        WHERE deletedAt IS NULL AND slotId = :slotId
        """
    )
    suspend fun countActiveForSlot(slotId: Long): Int

    @Query("SELECT * FROM routine_slot_reminders WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): RoutineSlotReminderEntity?

    @Query(
        """
        SELECT * FROM routine_slot_reminders
        WHERE deletedAt IS NULL AND isEnabled = 1
        ORDER BY remindAt ASC
        """
    )
    suspend fun listEnabled(): List<RoutineSlotReminderEntity>

    @Query(
        """
        SELECT * FROM routine_slot_reminders
        WHERE deletedAt IS NULL AND slotId = :slotId
        ORDER BY remindAt ASC
        """
    )
    suspend fun listActiveForSlot(slotId: Long): List<RoutineSlotReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoutineSlotReminderEntity): Long

    @Update
    suspend fun update(entity: RoutineSlotReminderEntity)

    @Query(
        """
        UPDATE routine_slot_reminders
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE slotId = :slotId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteForSlot(slotId: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE routine_slot_reminders
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}

@Dao
interface CalendarEventDao {

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE deletedAt IS NULL
        AND startAt < :to
        AND (
            recurrenceFrequency != 'NONE'
            OR (startAt >= :from AND startAt < :to)
        )
        AND (recurrenceUntil IS NULL OR recurrenceUntil >= :fromDate)
        ORDER BY startAt ASC
        """
    )
    fun observeForMonth(from: String, to: String, fromDate: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE deletedAt IS NULL AND startAt >= :from AND startAt < :to
        ORDER BY startAt ASC
        """
    )
    suspend fun listBetween(from: String, to: String): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE taskId = :taskId AND deletedAt IS NULL LIMIT 1")
    suspend fun getByTaskId(taskId: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CalendarEventEntity): Long

    @Update
    suspend fun update(entity: CalendarEventEntity)

    @Query(
        """
        UPDATE calendar_events
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE taskId = :taskId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteForTask(taskId: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE calendar_events
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)
}
