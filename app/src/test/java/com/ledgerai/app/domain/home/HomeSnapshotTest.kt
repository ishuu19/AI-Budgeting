package com.ledgerai.app.domain.home

import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HomeSnapshotTest {

    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun emptyList_spendIsZeroAndRecentIsEmpty() {
        val snapshot = homeSnapshot(emptyList(), today)

        assertEquals(0.0, snapshot.todaySpend, 0.0)
        assertEquals(0.0, snapshot.monthSpend, 0.0)
        assertTrue(snapshot.recent.isEmpty())
        assertEquals(today, snapshot.today)
    }

    @Test
    fun spendToday_countsThatExpense_andLeavesEarlierDaysOutOfToday() {
        val snapshot = homeSnapshot(
            listOf(
                tx(1, 18.0, TransactionType.EXPENSE, today),
                tx(2, 7.0, TransactionType.EXPENSE, today.minusDays(1)),
            ),
            today,
        )

        assertEquals(18.0, snapshot.todaySpend, 0.0)
        assertEquals(25.0, snapshot.monthSpend, 0.0)
    }

    @Test
    fun incomeDoesNotCountAsSpend_negativeExpenseReducesTheSum() {
        val snapshot = homeSnapshot(
            listOf(
                tx(1, 12.50, TransactionType.EXPENSE, today),
                tx(2, 40.0, TransactionType.INCOME, today, merchant = "Refund"),
                tx(3, -2.50, TransactionType.EXPENSE, today, merchant = "Store credit"),
            ),
            today,
        )

        assertEquals(10.0, snapshot.todaySpend, 0.0)
        assertEquals(10.0, snapshot.monthSpend, 0.0)
    }

    @Test
    fun monthBoundary_includesFirstAndLastDay_excludesNeighbors() {
        val snapshot = homeSnapshot(
            listOf(
                tx(1, 100.0, TransactionType.EXPENSE, LocalDate.of(2026, 9, 30)),
                tx(2, 3.0, TransactionType.EXPENSE, LocalDate.of(2026, 10, 1)),
                tx(3, 4.0, TransactionType.EXPENSE, today),
                tx(4, 5.0, TransactionType.EXPENSE, LocalDate.of(2026, 10, 31)),
                tx(5, 70.0, TransactionType.EXPENSE, LocalDate.of(2026, 11, 1)),
                tx(6, 9.0, TransactionType.INCOME, LocalDate.of(2026, 10, 1)),
            ),
            today,
        )

        assertEquals(4.0, snapshot.todaySpend, 0.0)
        assertEquals(12.0, snapshot.monthSpend, 0.0)
    }

    @Test
    fun recent_isNewestCreatedFirst_skipsDeleted_andHonorsLimit() {
        val older = LocalDateTime.of(2026, 10, 11, 8, 0)
        val newer = LocalDateTime.of(2026, 10, 11, 9, 0)
        val snapshot = homeSnapshot(
            listOf(
                tx(1, 1.0, TransactionType.EXPENSE, today, createdAt = older),
                tx(2, 2.0, TransactionType.EXPENSE, today, createdAt = newer),
                tx(3, 3.0, TransactionType.INCOME, today, createdAt = newer),
                tx(4, 99.0, TransactionType.EXPENSE, today, createdAt = newer.plusHours(1), deletedAt = 1L),
            ),
            today,
            recentLimit = 2,
        )

        assertEquals(listOf(3L, 2L), snapshot.recent.map { it.id })
        assertEquals(3.0, snapshot.todaySpend, 0.0)
    }

    private fun tx(
        id: Long,
        amount: Double,
        type: TransactionType,
        date: LocalDate,
        merchant: String = "Shop",
        createdAt: LocalDateTime = date.atStartOfDay().plusHours(id),
        deletedAt: Long? = null,
    ) = Transaction(
        id = id,
        amount = amount,
        type = type,
        category = TransactionCategory.FOOD,
        merchant = merchant,
        date = date,
        createdAt = createdAt,
        deletedAt = deletedAt,
    )
}
