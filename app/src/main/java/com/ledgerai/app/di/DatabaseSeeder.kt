package com.ledgerai.app.di

import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val billRepo: BillRepository
) {

    fun seedIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            val existing = transactionRepo.getAllTransactions().first()
            if (existing.isEmpty()) {
                seedTransactions()
                seedBudgets()
                seedBills()
            }
        }
    }

    private suspend fun seedTransactions() {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        listOf(
            Transaction(amount = 3200.0, type = TransactionType.INCOME, category = TransactionCategory.SALARY, merchant = "Employer", note = "Monthly salary", date = today.withDayOfMonth(1), createdAt = now),
            Transaction(amount = 12.50, type = TransactionType.EXPENSE, category = TransactionCategory.FOOD, merchant = "McDonald's", note = "Lunch", date = today, createdAt = now),
            Transaction(amount = 45.0, type = TransactionType.EXPENSE, category = TransactionCategory.TRANSPORT, merchant = "Gas Station", note = "Fuel", date = today.minusDays(1), createdAt = now),
            Transaction(amount = 9.99, type = TransactionType.EXPENSE, category = TransactionCategory.SUBSCRIPTIONS, merchant = "Spotify", note = "Monthly plan", date = today.minusDays(2), createdAt = now, isRecurring = true),
            Transaction(amount = 15.99, type = TransactionType.EXPENSE, category = TransactionCategory.SUBSCRIPTIONS, merchant = "Netflix", note = "Monthly plan", date = today.minusDays(2), createdAt = now, isRecurring = true),
            Transaction(amount = 65.0, type = TransactionType.EXPENSE, category = TransactionCategory.SHOPPING, merchant = "Amazon", note = "Household items", date = today.minusDays(3), createdAt = now),
            Transaction(amount = 28.0, type = TransactionType.EXPENSE, category = TransactionCategory.FOOD, merchant = "Grocery Store", note = "Weekly groceries", date = today.minusDays(4), createdAt = now),
            Transaction(amount = 120.0, type = TransactionType.EXPENSE, category = TransactionCategory.UTILITIES, merchant = "Electric Co.", note = "Electricity bill", date = today.minusDays(5), createdAt = now),
        ).forEach { transactionRepo.insert(it) }
    }

    private suspend fun seedBudgets() {
        val now = LocalDate.now()
        listOf(
            com.ledgerai.app.domain.model.Budget(category = TransactionCategory.FOOD, monthlyLimit = 400.0, month = now.monthValue, year = now.year),
            com.ledgerai.app.domain.model.Budget(category = TransactionCategory.TRANSPORT, monthlyLimit = 200.0, month = now.monthValue, year = now.year),
            com.ledgerai.app.domain.model.Budget(category = TransactionCategory.ENTERTAINMENT, monthlyLimit = 100.0, month = now.monthValue, year = now.year),
            com.ledgerai.app.domain.model.Budget(category = TransactionCategory.SHOPPING, monthlyLimit = 300.0, month = now.monthValue, year = now.year),
            com.ledgerai.app.domain.model.Budget(category = TransactionCategory.SUBSCRIPTIONS, monthlyLimit = 50.0, month = now.monthValue, year = now.year),
        ).forEach { budgetRepo.insert(it) }
    }

    private suspend fun seedBills() {
        val today = LocalDate.now()
        listOf(
            com.ledgerai.app.domain.model.Bill(name = "Netflix", amount = 15.99, frequency = BillFrequency.MONTHLY, nextDueDate = today.plusDays(5), category = TransactionCategory.SUBSCRIPTIONS),
            com.ledgerai.app.domain.model.Bill(name = "Spotify", amount = 9.99, frequency = BillFrequency.MONTHLY, nextDueDate = today.plusDays(5), category = TransactionCategory.SUBSCRIPTIONS),
            com.ledgerai.app.domain.model.Bill(name = "Gym Membership", amount = 40.0, frequency = BillFrequency.MONTHLY, nextDueDate = today.plusDays(10), category = TransactionCategory.HEALTH),
            com.ledgerai.app.domain.model.Bill(name = "Internet", amount = 60.0, frequency = BillFrequency.MONTHLY, nextDueDate = today.plusDays(15), category = TransactionCategory.UTILITIES),
        ).forEach { billRepo.insert(it) }
    }
}
