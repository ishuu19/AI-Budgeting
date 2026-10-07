package com.ledgerai.app.data.local

import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary process-local store until Phase 2 Room + Supabase sync.
 * Emits on every mutation so UI Flows stay reactive.
 */
@Singleton
class InMemoryStore @Inject constructor() {

    private val nextId = AtomicLong(1)

    fun nextId(): Long = nextId.getAndIncrement()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets.asStateFlow()

    private val _debts = MutableStateFlow<List<Debt>>(emptyList())
    val debts: StateFlow<List<Debt>> = _debts.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()

    fun setTransactions(list: List<Transaction>) = _transactions.update { list }
    fun setBudgets(list: List<Budget>) = _budgets.update { list }
    fun setDebts(list: List<Debt>) = _debts.update { list }
    fun setGoals(list: List<Goal>) = _goals.update { list }
    fun setBills(list: List<Bill>) = _bills.update { list }

    fun upsertTransaction(item: Transaction) = _transactions.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun removeTransaction(id: Long) = _transactions.update { it.filterNot { t -> t.id == id } }

    fun upsertBudget(item: Budget) = _budgets.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun removeBudget(id: Long) = _budgets.update { it.filterNot { b -> b.id == id } }

    fun upsertDebt(item: Debt) = _debts.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun removeDebt(id: Long) = _debts.update { it.filterNot { d -> d.id == id } }

    fun upsertGoal(item: Goal) = _goals.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun removeGoal(id: Long) = _goals.update { it.filterNot { g -> g.id == id } }

    fun upsertBill(item: Bill) = _bills.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun removeBill(id: Long) = _bills.update { it.filterNot { b -> b.id == id } }
}
