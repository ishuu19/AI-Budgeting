package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.TransactionDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<Transaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun getTransactionsForMonth(year: Int, month: Int): Flow<List<Transaction>> {
        val (start, end) = monthBounds(year, month)
        return dao.observeForMonth(start, end).map { list -> list.map { it.toDomain() } }
    }

    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getTotalExpensesForMonth(year: Int, month: Int): Double {
        val (start, end) = monthBounds(year, month)
        return dao.sumByTypeForMonth(TransactionType.EXPENSE, start, end)
    }

    suspend fun getTotalIncomeForMonth(year: Int, month: Int): Double {
        val (start, end) = monthBounds(year, month)
        return dao.sumByTypeForMonth(TransactionType.INCOME, start, end)
    }

    suspend fun getSpendingForCategoryMonth(
        category: TransactionCategory, year: Int, month: Int
    ): Double {
        val (start, end) = monthBounds(year, month)
        return dao.sumByCategoryForMonth(TransactionType.EXPENSE, category, start, end)
    }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun insert(transaction: Transaction): Long {
        val now = System.currentTimeMillis()
        val entity = transaction.copy(
            updatedAt = if (transaction.updatedAt == 0L) now else transaction.updatedAt,
            deletedAt = null
        ).toEntity()
        return if (transaction.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.insert(entity)
        }
    }

    suspend fun update(transaction: Transaction) {
        if (transaction.id == 0L) return
        dao.update(
            transaction.copy(updatedAt = System.currentTimeMillis()).toEntity()
        )
    }

    suspend fun delete(transaction: Transaction) {
        if (transaction.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(transaction.id, deletedAt = now, updatedAt = now)
    }

    private fun monthBounds(year: Int, month: Int): Pair<String, String> {
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).toString()
        val end = ym.plusMonths(1).atDay(1).toString()
        return start to end
    }
}
