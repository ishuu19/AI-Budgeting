package com.ledgerai.app.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    @Test
    fun buildCompactSummary_includesIncomeExpensesAndNet() {
        val summary = ContextBuilder.buildCompactSummary(
            monthLabel = "October 2026",
            monthlyIncome = 4000.0,
            monthlyExpenses = 2500.0,
            categoryTotals = mapOf("Food" to 800.0, "Rent" to 1200.0)
        )
        assertTrue(summary.contains("Income: 4000.00"))
        assertTrue(summary.contains("Expenses: 2500.00"))
        assertTrue(summary.contains("Net: 1500.00"))
        assertTrue(summary.contains("Food: 800"))
        assertTrue(summary.contains("Period: October 2026"))
    }

    @Test
    fun buildCompactSummary_isCapped() {
        val huge = (1..200).associate { "Cat$it" to it.toDouble() }
        val summary = ContextBuilder.buildCompactSummary(categoryTotals = huge)
        assertTrue(summary.length <= 1500)
    }

    @Test
    fun fromFinancialContextString_emptyReturnsEmpty() {
        assertTrue(ContextBuilder.fromFinancialContextString("   ").isEmpty())
        assertFalse(ContextBuilder.fromFinancialContextString("income 10").isEmpty())
    }
}
