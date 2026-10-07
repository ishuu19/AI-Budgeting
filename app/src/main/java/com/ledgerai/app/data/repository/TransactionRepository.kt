package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.InMemoryStore
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val store: InMemoryStore) {

    fun getAllTransactions(): Flow<List<Transaction>> =
        store.transactions.map { list -> list.sortedByDescending { it.date } }

    fun getTransactionsForMonth(year: Int, month: Int): Flow<List<Transaction>> =
        store.transactions.map { list ->
            list.filter { it.date.year == year && it.date.monthValue == month }
                .sortedByDescending { it.date }
        }

    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> =
        store.transactions.map { list ->
            list.sortedByDescending { it.createdAt }.take(limit)
        }

    suspend fun getTotalExpensesForMonth(year: Int, month: Int): Double =
        store.transactions.value
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.date.year == year && it.date.monthValue == month
            }
            .sumOf { it.amount }

    suspend fun getTotalIncomeForMonth(year: Int, month: Int): Double =
        store.transactions.value
            .filter {
                it.type == TransactionType.INCOME &&
                    it.date.year == year && it.date.monthValue == month
            }
            .sumOf { it.amount }

    suspend fun getSpendingForCategoryMonth(
        category: TransactionCategory, year: Int, month: Int
    ): Double =
        store.transactions.value
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.category == category &&
                    it.date.year == year && it.date.monthValue == month
            }
            .sumOf { it.amount }

    suspend fun insert(transaction: Transaction) {
        val id = if (transaction.id == 0L) store.nextId() else transaction.id
        store.upsertTransaction(transaction.copy(id = id))
    }

    suspend fun update(transaction: Transaction) {
        if (transaction.id == 0L) return
        store.upsertTransaction(transaction)
    }

    suspend fun delete(transaction: Transaction) {
        if (transaction.id == 0L) return
        store.removeTransaction(transaction.id)
    }
}
