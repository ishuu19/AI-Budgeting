package com.ledgerai.app.presentation.screens.transactions

import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SpendQueryTest {

    private val oct = LocalDate.of(2026, 10, 8)
    private val sep = LocalDate.of(2026, 9, 30)

    private val coffee = tx("Coffee", 4.5, TransactionType.EXPENSE, TransactionCategory.FOOD, oct, "latte")
    private val pay = tx("Acme", 2000.0, TransactionType.INCOME, TransactionCategory.SALARY, oct, "payday")
    private val rent = tx("Landlord", 900.0, TransactionType.EXPENSE, TransactionCategory.RENT, sep, "sept")

    @Test
    fun inMonth_keepsOnlyThatMonth() {
        val month = SpendQuery.inMonth(listOf(coffee, pay, rent), YearMonth.of(2026, 10))
        assertEquals(listOf(coffee, pay), month)
    }

    @Test
    fun monthNet_isIncomeMinusExpense() {
        assertEquals(1995.5, SpendQuery.monthNet(listOf(coffee, pay)), 0.001)
    }

    @Test
    fun matchesSearch_merchantNoteCategory_notLocation() {
        val withPlace = coffee.copy(location = "Downtown")
        assertTrue(SpendQuery.matchesSearch(withPlace, "cof"))
        assertTrue(SpendQuery.matchesSearch(withPlace, "LAT"))
        assertTrue(SpendQuery.matchesSearch(withPlace, "food"))
        assertFalse(SpendQuery.matchesSearch(withPlace, "Downtown"))
    }

    @Test
    fun filter_monthTypeCategoryAndQuery() {
        val all = listOf(coffee, pay, rent)
        val october = YearMonth.of(2026, 10)
        val out = SpendQuery.filter(
            all,
            month = october,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD,
            query = "cof"
        )
        assertEquals(listOf(coffee), out)
    }

    @Test
    fun categoriesIn_uniqueInAppearanceOrder() {
        val cats = SpendQuery.categoriesIn(listOf(coffee, pay, coffee))
        assertEquals(listOf(TransactionCategory.FOOD, TransactionCategory.SALARY), cats)
    }

    @Test
    fun duplicateToday_copiesFields_resetsIdAndDate() {
        val source = coffee.copy(id = 42, isRecurring = true, location = "Cafe")
        val copy = SpendQuery.duplicateToday(source, today = LocalDate.of(2026, 10, 8))
        assertEquals(0L, copy.id)
        assertEquals(LocalDate.of(2026, 10, 8), copy.date)
        assertEquals(source.amount, copy.amount, 0.0)
        assertEquals(source.type, copy.type)
        assertEquals(source.category, copy.category)
        assertEquals(source.merchant, copy.merchant)
        assertEquals(source.note, copy.note)
        assertEquals(source.location, copy.location)
        assertTrue(copy.isRecurring)
    }

    private fun tx(
        merchant: String,
        amount: Double,
        type: TransactionType,
        category: TransactionCategory,
        date: LocalDate,
        note: String
    ) = Transaction(
        amount = amount,
        type = type,
        category = category,
        merchant = merchant,
        note = note,
        date = date
    )
}
