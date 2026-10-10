package com.ledgerai.app.domain.home

import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate

/**
 * Money the person can see today, computed from transactions already loaded.
 * [todaySpend] and [monthSpend] are expense sums only. There is no balance here.
 */
data class HomeSnapshot(
    val today: LocalDate,
    val todaySpend: Double,
    val monthSpend: Double,
    val recent: List<Transaction>,
)

/**
 * Spend is the sum of [TransactionType.EXPENSE] amounts, the same rule as the
 * transaction month total. Income, including a refund stored as income, is left
 * out. A negative expense reduces the sum. Soft-deleted rows are ignored.
 *
 * The month is the calendar month of [today]: from the 1st up to, but not
 * including, the 1st of the next month. [recent] is the newest active rows by
 * [Transaction.createdAt], then id.
 */
fun homeSnapshot(
    transactions: List<Transaction>,
    today: LocalDate,
    recentLimit: Int = 5,
): HomeSnapshot {
    val active = transactions.filter { it.deletedAt == null }
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1)
    val expenses = active.filter { it.type == TransactionType.EXPENSE }
    val todaySpend = expenses.filter { it.date == today }.sumOf { it.amount }
    val monthSpend = expenses
        .filter { !it.date.isBefore(monthStart) && it.date.isBefore(monthEnd) }
        .sumOf { it.amount }
    val recent = active
        .sortedWith(compareByDescending<Transaction> { it.createdAt }.thenByDescending { it.id })
        .take(recentLimit.coerceAtLeast(0))
    return HomeSnapshot(
        today = today,
        todaySpend = todaySpend,
        monthSpend = monthSpend,
        recent = recent,
    )
}
