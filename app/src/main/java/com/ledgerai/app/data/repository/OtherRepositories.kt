package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.InMemoryStore
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
class BudgetRepository @Inject constructor(private val store: InMemoryStore) {

    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<Budget>> =
        store.budgets.map { list -> list.filter { it.month == month && it.year == year } }

    suspend fun getBudgetForCategory(category: TransactionCategory, month: Int, year: Int): Budget? =
        store.budgets.value.firstOrNull {
            it.category == category && it.month == month && it.year == year
        }

    suspend fun insert(budget: Budget) {
        val id = if (budget.id == 0L) store.nextId() else budget.id
        store.upsertBudget(budget.copy(id = id))
    }

    suspend fun update(budget: Budget) {
        if (budget.id == 0L) return
        store.upsertBudget(budget)
    }

    suspend fun delete(budget: Budget) {
        if (budget.id == 0L) return
        store.removeBudget(budget.id)
    }
}

@Singleton
class DebtRepository @Inject constructor(private val store: InMemoryStore) {

    fun getActiveDebts(): Flow<List<Debt>> =
        store.debts.map { list ->
            list.filter { !it.isPaid }.sortedBy { it.dueDate }
        }

    fun getAllDebts(): Flow<List<Debt>> =
        store.debts.map { list -> list.sortedByDescending { it.dateLent } }

    suspend fun getTotalOwedToMe(): Double =
        store.debts.value
            .filter { it.direction == DebtDirection.THEY_OWE && !it.isPaid }
            .sumOf { it.amount }

    suspend fun getTotalIOwe(): Double =
        store.debts.value
            .filter { it.direction == DebtDirection.I_OWE && !it.isPaid }
            .sumOf { it.amount }

    suspend fun insert(debt: Debt): Long {
        val id = if (debt.id == 0L) store.nextId() else debt.id
        store.upsertDebt(debt.copy(id = id))
        return id
    }

    suspend fun update(debt: Debt) {
        if (debt.id == 0L) return
        store.upsertDebt(debt)
    }

    suspend fun delete(debt: Debt) {
        if (debt.id == 0L) return
        store.removeDebt(debt.id)
    }

    suspend fun markAsPaid(id: Long) {
        val current = store.debts.value.firstOrNull { it.id == id } ?: return
        store.upsertDebt(current.copy(isPaid = true))
    }
}

@Singleton
class GoalRepository @Inject constructor(private val store: InMemoryStore) {

    fun getActiveGoals(): Flow<List<Goal>> =
        store.goals.map { list -> list.filter { !it.isCompleted } }

    fun getAllGoals(): Flow<List<Goal>> = store.goals

    suspend fun insert(goal: Goal) {
        val id = if (goal.id == 0L) store.nextId() else goal.id
        store.upsertGoal(goal.copy(id = id))
    }

    suspend fun update(goal: Goal) {
        if (goal.id == 0L) return
        store.upsertGoal(goal)
    }

    suspend fun delete(goal: Goal) {
        if (goal.id == 0L) return
        store.removeGoal(goal.id)
    }
}

@Singleton
class BillRepository @Inject constructor(private val store: InMemoryStore) {

    fun getActiveBills(): Flow<List<Bill>> =
        store.bills.map { list ->
            list.filter { it.isActive }.sortedBy { it.nextDueDate }
        }

    fun getAllBills(): Flow<List<Bill>> = store.bills

    suspend fun getTotalMonthlyBills(): Double =
        store.bills.value
            .filter { it.isActive && it.frequency == BillFrequency.MONTHLY }
            .sumOf { it.amount }

    suspend fun insert(bill: Bill) {
        val id = if (bill.id == 0L) store.nextId() else bill.id
        store.upsertBill(bill.copy(id = id))
    }

    suspend fun update(bill: Bill) {
        if (bill.id == 0L) return
        store.upsertBill(bill)
    }

    suspend fun delete(bill: Bill) {
        if (bill.id == 0L) return
        store.removeBill(bill.id)
    }
}
