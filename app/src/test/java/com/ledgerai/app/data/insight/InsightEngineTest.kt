package com.ledgerai.app.data.insight

import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class InsightEngineTest {

    @Test
    fun weekendFiresWhenWeekendsAreHeavier() {
        val start = LocalDate.of(2026, 8, 3)
        val txs = (0 until 28).flatMap { offset ->
            val day = start.plusDays(offset.toLong())
            val weekend = day.dayOfWeek.value >= 6
            listOf(expense(if (weekend) 80.0 else 20.0, day))
        }
        val hit = InsightEngine.weekend(txs)
        assertNotNull(hit)
        assertTrue(hit!!.headline.contains("×"))
    }

    @Test
    fun weekendStaysQuietOnEvenSpend() {
        val start = LocalDate.of(2026, 8, 3)
        val txs = (0 until 21).map { expense(30.0, start.plusDays(it.toLong())) }
        assertNull(InsightEngine.weekend(txs))
    }

    @Test
    fun smallTicketsNeedFifteenPercent() {
        val day = LocalDate.of(2026, 10, 1)
        val small = (1..8).map { expense(10.0, day, merchant = "Cafe") }
        val big = listOf(expense(400.0, day, merchant = "Rent"))
        assertNotNull(InsightEngine.smallTickets(small + big))
        assertNull(InsightEngine.smallTickets(listOf(expense(200.0, day)) + (1..4).map { expense(5.0, day) }))
    }

    private fun expense(amount: Double, date: LocalDate, merchant: String = "Shop") = Transaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        category = TransactionCategory.FOOD,
        merchant = merchant,
        date = date,
        createdAt = date.atTime(12, 0)
    )
}
