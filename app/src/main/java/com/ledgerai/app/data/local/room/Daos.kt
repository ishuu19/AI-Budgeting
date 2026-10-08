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
interface CalendarEventDao {

    @Transaction
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE deletedAt IS NULL AND hasDate = 1 AND isEnabled = 1
        AND startAt < :to
        AND (
            recurrenceFrequency != 'NONE'
            OR startAt >= :from
        )
        AND (recurrenceUntil IS NULL OR recurrenceUntil >= :fromDate)
        ORDER BY startAt ASC
        """
    )
    fun observeForRange(from: String, to: String, fromDate: String): Flow<List<EventWithReminders>>

    @Transaction
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL ORDER BY startAt ASC")
    fun observeAll(): Flow<List<EventWithReminders>>

    @Transaction
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL AND kind = :kind ORDER BY startAt ASC")
    fun observeByKind(kind: String): Flow<List<EventWithReminders>>

    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL AND kind = :kind")
    suspend fun listByKind(kind: String): List<CalendarEventEntity>

    @Transaction
    @Query("SELECT * FROM calendar_events WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getWithReminders(id: Long): EventWithReminders?

    @Query("SELECT * FROM calendar_events WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): CalendarEventEntity?

    /** Includes soft-deleted rows (sync). */
    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getByIdAny(id: Long): CalendarEventEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE deletedAt IS NULL AND hasDate = 1 AND isEnabled = 1
        AND (
            recurrenceFrequency != 'NONE'
            OR (startAt >= :from AND startAt < :to)
        )
        AND startAt < :to
        AND (recurrenceUntil IS NULL OR recurrenceUntil >= :fromDate)
        ORDER BY startAt ASC
        """
    )
    suspend fun listForRange(from: String, to: String, fromDate: String): List<EventWithReminders>

    @Transaction
    @Query("SELECT * FROM calendar_events WHERE deletedAt IS NULL AND kind = 'ALARM' AND isEnabled = 1")
    suspend fun listEnabledAlarms(): List<EventWithReminders>

    @Transaction
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE deletedAt IS NULL AND isEnabled = 1 AND isCompleted = 0 AND kind != 'ALARM'
        AND id IN (SELECT DISTINCT eventId FROM event_reminders WHERE deletedAt IS NULL AND isEnabled = 1)
        """
    )
    suspend fun listWithActiveReminders(): List<EventWithReminders>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CalendarEventEntity): Long

    @Update
    suspend fun update(entity: CalendarEventEntity)

    @Query(
        """
        UPDATE calendar_events
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM calendar_events WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CalendarEventEntity?
}

@Dao
interface EventReminderDao {

    @Query("SELECT * FROM event_reminders WHERE eventId = :eventId AND deletedAt IS NULL ORDER BY id ASC")
    suspend fun listForEvent(eventId: Long): List<EventReminderEntity>

    @Query("SELECT id FROM event_reminders WHERE eventId = :eventId")
    suspend fun allIdsForEvent(eventId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM event_reminders WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun countActiveForEvent(eventId: Long): Int

    @Query("SELECT * FROM event_reminders WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): EventReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventReminderEntity): Long

    @Update
    suspend fun update(entity: EventReminderEntity)

    @Query("UPDATE event_reminders SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE event_reminders SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE eventId = :eventId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteForEvent(eventId: Long, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM event_reminders WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<EventReminderEntity>

    @Query("SELECT * FROM event_reminders WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): EventReminderEntity?
}
