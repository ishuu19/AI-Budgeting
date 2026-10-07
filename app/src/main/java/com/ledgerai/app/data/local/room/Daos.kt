package com.ledgerai.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
