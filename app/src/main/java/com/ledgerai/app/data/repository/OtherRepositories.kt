package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.TransactionCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao
) {

    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>> =
        dao.observeForMonth(month, year).map { list -> list.map { it.toDomain() } }

    suspend fun getBudgetForCategory(category: TransactionCategory, month: Int, year: Int): Budget? =
        dao.getForCategory(category, month, year)?.toDomain()

    suspend fun insert(budget: Budget): Long {
        val now = System.currentTimeMillis()
        val entity = budget.copy(
            updatedAt = if (budget.updatedAt == 0L) now else budget.updatedAt,
            deletedAt = null
        ).toEntity()
        return if (budget.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.insert(entity)
        }
    }

    suspend fun update(budget: Budget) {
        if (budget.id == 0L) return
        dao.update(budget.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(budget: Budget) {
        if (budget.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(budget.id, deletedAt = now, updatedAt = now)
    }
}

@Singleton
class DebtRepository @Inject constructor(
    private val dao: DebtDao
) {

    fun getActiveDebts(): Flow<List<Debt>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun getAllDebts(): Flow<List<Debt>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getTotalOwedToMe(): Double =
        dao.sumByDirection(DebtDirection.THEY_OWE)

    suspend fun getTotalIOwe(): Double =
        dao.sumByDirection(DebtDirection.I_OWE)

    suspend fun insert(debt: Debt): Long {
        val now = System.currentTimeMillis()
        val entity = debt.copy(
            updatedAt = if (debt.updatedAt == 0L) now else debt.updatedAt,
            deletedAt = null
        ).toEntity()
        return if (debt.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.insert(entity)
            debt.id
        }
    }

    suspend fun update(debt: Debt) {
        if (debt.id == 0L) return
        dao.update(debt.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(debt: Debt) {
        if (debt.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(debt.id, deletedAt = now, updatedAt = now)
    }

    suspend fun markAsPaid(id: Long) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(isPaid = true, updatedAt = System.currentTimeMillis())
        )
    }
}

@Singleton
class GoalRepository @Inject constructor(
    private val dao: GoalDao
) {

    fun getActiveGoals(): Flow<List<Goal>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun getAllGoals(): Flow<List<Goal>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun insert(goal: Goal): Long {
        val now = System.currentTimeMillis()
        val entity = goal.copy(
            updatedAt = if (goal.updatedAt == 0L) now else goal.updatedAt,
            deletedAt = null
        ).toEntity()
        return if (goal.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.insert(entity)
        }
    }

    suspend fun update(goal: Goal) {
        if (goal.id == 0L) return
        dao.update(goal.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(goal: Goal) {
        if (goal.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(goal.id, deletedAt = now, updatedAt = now)
    }
}

@Singleton
class BillRepository @Inject constructor(
    private val dao: BillDao
) {

    fun getActiveBills(): Flow<List<Bill>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun getAllBills(): Flow<List<Bill>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getTotalMonthlyBills(): Double =
        dao.sumByFrequency(BillFrequency.MONTHLY)

    suspend fun insert(bill: Bill): Long {
        val now = System.currentTimeMillis()
        val entity = bill.copy(
            updatedAt = if (bill.updatedAt == 0L) now else bill.updatedAt,
            deletedAt = null
        ).toEntity()
        return if (bill.id == 0L) {
            dao.insert(entity.copy(id = 0))
        } else {
            dao.insert(entity)
        }
    }

    suspend fun update(bill: Bill) {
        if (bill.id == 0L) return
        dao.update(bill.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(bill: Bill) {
        if (bill.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(bill.id, deletedAt = now, updatedAt = now)
    }
}
