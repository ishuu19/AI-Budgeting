package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.mongo.MongoProvider
import com.ledgerai.app.domain.model.*
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
import javax.inject.Inject
import javax.inject.Singleton

// ─── BudgetRepository ─────────────────────────────────────────────────────────

@Singleton
class BudgetRepository @Inject constructor(private val mongo: MongoProvider) {

    private val col get() = mongo.budgetsCollection()

    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>> = flow {
        val list = col.find(Filters.and(
            Filters.eq("month", month),
            Filters.eq("year", year)
        )).toList()
        emit(list.map { it.toBudget() })
    }.flowOn(Dispatchers.IO)

    suspend fun getBudgetForCategory(category: TransactionCategory, month: Int, year: Int): Budget? =
        withContext(Dispatchers.IO) {
            col.find(Filters.and(
                Filters.eq("category", category.name),
                Filters.eq("month", month),
                Filters.eq("year", year)
            )).firstOrNull()?.toBudget()
        }

    suspend fun insert(budget: Budget) = withContext(Dispatchers.IO) {
        col.insertOne(budget.toDocument())
    }

    suspend fun update(budget: Budget) = withContext(Dispatchers.IO) {
        budget.mongoId?.let { id ->
            col.updateOne(Filters.eq("_id", ObjectId(id)), Document("\$set", budget.toDocument().apply { remove("_id") }))
        }
    }

    suspend fun delete(budget: Budget) = withContext(Dispatchers.IO) {
        budget.mongoId?.let { col.deleteOne(Filters.eq("_id", ObjectId(it))) }
    }

    private fun Document.toBudget() = Budget(
        id = getObjectId("_id")?.toHexString()?.hashCode()?.toLong() ?: 0L,
        mongoId = getObjectId("_id")?.toHexString(),
        category = runCatching { TransactionCategory.valueOf(getString("category") ?: "OTHER") }.getOrDefault(TransactionCategory.OTHER),
        monthlyLimit = (this["monthlyLimit"] as? Number)?.toDouble() ?: 0.0,
        month = (this["month"] as? Number)?.toInt() ?: 1,
        year = (this["year"] as? Number)?.toInt() ?: 2024,
        alertThreshold = (this["alertThreshold"] as? Number)?.toInt() ?: 80
    )

    private fun Budget.toDocument(): Document = Document().apply {
        if (!mongoId.isNullOrBlank()) put("_id", ObjectId(mongoId))
        else put("_id", ObjectId())
        put("category", category.name)
        put("monthlyLimit", monthlyLimit)
        put("month", month)
        put("year", year)
        put("alertThreshold", alertThreshold)
    }
}

// ─── DebtRepository ───────────────────────────────────────────────────────────

@Singleton
class DebtRepository @Inject constructor(private val mongo: MongoProvider) {

    private val col get() = mongo.debtsCollection()

    fun getActiveDebts(): Flow<List<Debt>> = flow {
        val list = col.find(Filters.eq("isPaid", false))
            .sort(Sorts.ascending("dueDate")).toList()
        emit(list.map { it.toDebt() })
    }.flowOn(Dispatchers.IO)

    fun getAllDebts(): Flow<List<Debt>> = flow {
        val list = col.find().sort(Sorts.descending("dateLent")).toList()
        emit(list.map { it.toDebt() })
    }.flowOn(Dispatchers.IO)

    suspend fun getTotalOwedToMe(): Double = withContext(Dispatchers.IO) {
        col.find(Filters.and(Filters.eq("direction", "THEY_OWE"), Filters.eq("isPaid", false)))
            .toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun getTotalIOwe(): Double = withContext(Dispatchers.IO) {
        col.find(Filters.and(Filters.eq("direction", "I_OWE"), Filters.eq("isPaid", false)))
            .toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun insert(debt: Debt): String = withContext(Dispatchers.IO) {
        val doc = debt.toDocument()
        col.insertOne(doc)
        doc.getObjectId("_id").toHexString()
    }

    suspend fun update(debt: Debt) = withContext(Dispatchers.IO) {
        debt.mongoId?.let { id ->
            val set = debt.toDocument().apply { remove("_id") }
            col.updateOne(Filters.eq("_id", ObjectId(id)), Document("\$set", set))
        }
    }

    suspend fun delete(debt: Debt) = withContext(Dispatchers.IO) {
        debt.mongoId?.let { col.deleteOne(Filters.eq("_id", ObjectId(it))) }
    }

    suspend fun markAsPaid(mongoId: String) = withContext(Dispatchers.IO) {
        col.updateOne(Filters.eq("_id", ObjectId(mongoId)), Document("\$set", Document("isPaid", true)))
    }

    private fun Document.toDebt() = Debt(
        id = getObjectId("_id")?.toHexString()?.hashCode()?.toLong() ?: 0L,
        mongoId = getObjectId("_id")?.toHexString(),
        friendName = getString("friendName") ?: "",
        amount = (this["amount"] as? Number)?.toDouble() ?: 0.0,
        direction = runCatching { DebtDirection.valueOf(getString("direction") ?: "I_OWE") }.getOrDefault(DebtDirection.I_OWE),
        dateLent = runCatching { LocalDate.parse(getString("dateLent")) }.getOrDefault(LocalDate.now()),
        dueDate = getString("dueDate")?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        phone = getString("phone") ?: "",
        email = getString("email") ?: "",
        note = getString("note") ?: "",
        isPaid = (this["isPaid"] as? Boolean) == true,
        calendarEventId = (this["calendarEventId"] as? Number)?.toLong()?.takeIf { it >= 0 },
        currency = getString("currency") ?: "USD"
    )

    private fun Debt.toDocument(): Document = Document().apply {
        if (!mongoId.isNullOrBlank()) put("_id", ObjectId(mongoId))
        else put("_id", ObjectId())
        put("friendName", friendName)
        put("amount", amount)
        put("direction", direction.name)
        put("dateLent", dateLent.toString())
        put("dueDate", dueDate?.toString() ?: "")
        put("phone", phone)
        put("email", email)
        put("note", note)
        put("isPaid", isPaid)
        put("calendarEventId", calendarEventId ?: -1L)
        put("currency", currency)
    }
}

// ─── GoalRepository ───────────────────────────────────────────────────────────

@Singleton
class GoalRepository @Inject constructor(private val mongo: MongoProvider) {

    private val col get() = mongo.goalsCollection()

    fun getActiveGoals(): Flow<List<Goal>> = flow {
        val list = col.find(Filters.eq("isCompleted", false)).toList()
        emit(list.map { it.toGoal() })
    }.flowOn(Dispatchers.IO)

    fun getAllGoals(): Flow<List<Goal>> = flow {
        val list = col.find().toList()
        emit(list.map { it.toGoal() })
    }.flowOn(Dispatchers.IO)

    suspend fun insert(goal: Goal) = withContext(Dispatchers.IO) { col.insertOne(goal.toDocument()) }
    suspend fun update(goal: Goal) = withContext(Dispatchers.IO) {
        goal.mongoId?.let { id ->
            col.updateOne(Filters.eq("_id", ObjectId(id)), Document("\$set", goal.toDocument().apply { remove("_id") }))
        }
    }
    suspend fun delete(goal: Goal) = withContext(Dispatchers.IO) {
        goal.mongoId?.let { col.deleteOne(Filters.eq("_id", ObjectId(it))) }
    }

    private fun Document.toGoal() = Goal(
        id = getObjectId("_id")?.toHexString()?.hashCode()?.toLong() ?: 0L,
        mongoId = getObjectId("_id")?.toHexString(),
        name = getString("name") ?: "",
        targetAmount = (this["targetAmount"] as? Number)?.toDouble() ?: 0.0,
        savedAmount = (this["savedAmount"] as? Number)?.toDouble() ?: 0.0,
        targetDate = getString("targetDate")?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        emoji = getString("emoji") ?: "🎯",
        note = getString("note") ?: "",
        isCompleted = (this["isCompleted"] as? Boolean) == true,
        createdAt = runCatching { LocalDate.parse(getString("createdAt")) }.getOrDefault(LocalDate.now())
    )

    private fun Goal.toDocument(): Document = Document().apply {
        if (!mongoId.isNullOrBlank()) put("_id", ObjectId(mongoId))
        else put("_id", ObjectId())
        put("name", name)
        put("targetAmount", targetAmount)
        put("savedAmount", savedAmount)
        put("targetDate", targetDate?.toString() ?: "")
        put("emoji", emoji)
        put("note", note)
        put("isCompleted", isCompleted)
        put("createdAt", createdAt.toString())
    }
}

// ─── BillRepository ───────────────────────────────────────────────────────────

@Singleton
class BillRepository @Inject constructor(private val mongo: MongoProvider) {

    private val col get() = mongo.billsCollection()

    fun getActiveBills(): Flow<List<Bill>> = flow {
        val list = col.find(Filters.eq("isActive", true))
            .sort(Sorts.ascending("nextDueDate")).toList()
        emit(list.map { it.toBill() })
    }.flowOn(Dispatchers.IO)

    fun getAllBills(): Flow<List<Bill>> = flow {
        val list = col.find().toList()
        emit(list.map { it.toBill() })
    }.flowOn(Dispatchers.IO)

    suspend fun getTotalMonthlyBills(): Double = withContext(Dispatchers.IO) {
        col.find(Filters.and(Filters.eq("isActive", true), Filters.eq("frequency", "MONTHLY")))
            .toList().sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
    }

    suspend fun insert(bill: Bill) = withContext(Dispatchers.IO) { col.insertOne(bill.toDocument()) }
    suspend fun update(bill: Bill) = withContext(Dispatchers.IO) {
        bill.mongoId?.let { id ->
            col.updateOne(Filters.eq("_id", ObjectId(id)), Document("\$set", bill.toDocument().apply { remove("_id") }))
        }
    }
    suspend fun delete(bill: Bill) = withContext(Dispatchers.IO) {
        bill.mongoId?.let { col.deleteOne(Filters.eq("_id", ObjectId(it))) }
    }

    private fun Document.toBill() = Bill(
        id = getObjectId("_id")?.toHexString()?.hashCode()?.toLong() ?: 0L,
        mongoId = getObjectId("_id")?.toHexString(),
        name = getString("name") ?: "",
        amount = (this["amount"] as? Number)?.toDouble() ?: 0.0,
        frequency = runCatching { BillFrequency.valueOf(getString("frequency") ?: "MONTHLY") }.getOrDefault(BillFrequency.MONTHLY),
        nextDueDate = runCatching { LocalDate.parse(getString("nextDueDate")) }.getOrDefault(LocalDate.now()),
        category = runCatching { TransactionCategory.valueOf(getString("category") ?: "SUBSCRIPTIONS") }.getOrDefault(TransactionCategory.SUBSCRIPTIONS),
        note = getString("note") ?: "",
        isActive = (this["isActive"] as? Boolean) != false,
        currency = getString("currency") ?: "USD"
    )

    private fun Bill.toDocument(): Document = Document().apply {
        if (!mongoId.isNullOrBlank()) put("_id", ObjectId(mongoId))
        else put("_id", ObjectId())
        put("name", name)
        put("amount", amount)
        put("frequency", frequency.name)
        put("nextDueDate", nextDueDate.toString())
        put("category", category.name)
        put("note", note)
        put("isActive", isActive)
        put("currency", currency)
    }
}
