package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.mongo.MongoProvider
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val mongo: MongoProvider) {

    private val col get() = mongo.transactionsCollection()

    fun getAllTransactions(): Flow<List<Transaction>> = flow {
        val list = col.find().sort(Sorts.descending("date")).toList()
        emit(list.map { it.toTransaction() })
    }.flowOn(Dispatchers.IO)

    fun getTransactionsForMonth(year: Int, month: Int): Flow<List<Transaction>> = flow {
        val prefix = "%04d-%02d".format(year, month)
        val list = col.find(Filters.regex("date", "^$prefix"))
            .sort(Sorts.descending("date")).toList()
        emit(list.map { it.toTransaction() })
    }.flowOn(Dispatchers.IO)

    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> = flow {
        val list = col.find().sort(Sorts.descending("createdAt")).limit(limit).toList()
        emit(list.map { it.toTransaction() })
    }.flowOn(Dispatchers.IO)

    suspend fun getTotalExpensesForMonth(year: Int, month: Int): Double = withContext(Dispatchers.IO) {
        val prefix = "%04d-%02d".format(year, month)
        col.find(Filters.and(
            Filters.eq("type", "EXPENSE"),
            Filters.regex("date", "^$prefix")
        )).toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun getTotalIncomeForMonth(year: Int, month: Int): Double = withContext(Dispatchers.IO) {
        val prefix = "%04d-%02d".format(year, month)
        col.find(Filters.and(
            Filters.eq("type", "INCOME"),
            Filters.regex("date", "^$prefix")
        )).toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun getSpendingForCategoryMonth(
        category: TransactionCategory, year: Int, month: Int
    ): Double = withContext(Dispatchers.IO) {
        val prefix = "%04d-%02d".format(year, month)
        col.find(Filters.and(
            Filters.eq("type", "EXPENSE"),
            Filters.eq("category", category.name),
            Filters.regex("date", "^$prefix")
        )).toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun insert(transaction: Transaction) = withContext(Dispatchers.IO) {
        val doc = transaction.toDocument()
        col.insertOne(doc)
    }

    suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
        val id = transaction.mongoId ?: return@withContext
        col.updateOne(
            Filters.eq("_id", ObjectId(id)),
            Document("\$set", transaction.toDocument().apply { remove("_id") })
        )
    }

    suspend fun delete(transaction: Transaction) = withContext(Dispatchers.IO) {
        transaction.mongoId?.let { col.deleteOne(Filters.eq("_id", ObjectId(it))) }
    }

    private fun Document.toTransaction(): Transaction {
        val idStr = getObjectId("_id")?.toHexString()
        return Transaction(
            id = idStr?.hashCode()?.toLong() ?: 0L,
            mongoId = idStr,
            amount = (this["amount"] as? Number)?.toDouble() ?: 0.0,
            type = runCatching { TransactionType.valueOf(getString("type") ?: "EXPENSE") }.getOrDefault(TransactionType.EXPENSE),
            category = runCatching { TransactionCategory.valueOf(getString("category") ?: "OTHER") }.getOrDefault(TransactionCategory.OTHER),
            merchant = getString("merchant") ?: "",
            note = getString("note") ?: "",
            date = runCatching { LocalDate.parse(getString("date")) }.getOrDefault(LocalDate.now()),
            createdAt = runCatching { LocalDateTime.parse(getString("createdAt")) }.getOrDefault(LocalDateTime.now()),
            isRecurring = (this["isRecurring"] as? Boolean) == true,
            currency = getString("currency") ?: "USD"
        )
    }

    private fun Transaction.toDocument(): Document {
        val doc = Document()
        if (!mongoId.isNullOrBlank()) doc["_id"] = ObjectId(mongoId)
        else doc["_id"] = ObjectId()
        doc["amount"] = amount
        doc["type"] = type.name
        doc["category"] = category.name
        doc["merchant"] = merchant
        doc["note"] = note
        doc["date"] = date.toString()
        doc["createdAt"] = createdAt.toString()
        doc["isRecurring"] = isRecurring
        doc["currency"] = currency
        return doc
    }
}
