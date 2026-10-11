package com.ledgerai.app.domain.assistant

import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ContextFactsMapperTest {

    @Test
    fun blankName_isSkipped() {
        val merchants = knownMerchants(
            listOf(
                transaction(merchant = ""),
                transaction(merchant = "   "),
                transaction(merchant = "Lidl"),
            ),
        )
        val debts = openDebtFacts(
            listOf(
                debt(friendName = "", direction = DebtDirection.THEY_OWE),
                debt(friendName = "  ", direction = DebtDirection.THEY_OWE),
                debt(friendName = "Sarah", direction = DebtDirection.I_OWE, amount = 12.0),
            ),
        )

        assertEquals(listOf(KnownMerchant("Lidl")), merchants)
        assertEquals(
            listOf(
                OpenDebtFact(
                    person = "Sarah",
                    direction = DebtDirection.I_OWE,
                    amount = 12.0,
                    dueDate = null,
                ),
            ),
            debts,
        )
    }

    @Test
    fun nullAmount_staysNull_andDirectionIsCopied() {
        val due = LocalDate.of(2026, 12, 1)
        val fact = openDebtFact(
            person = "Sarah",
            direction = DebtDirection.THEY_OWE,
            amount = null,
            dueDate = due,
        )

        assertEquals("Sarah", fact?.person)
        assertEquals(DebtDirection.THEY_OWE, fact?.direction)
        assertNull(fact?.amount)
        assertEquals(due, fact?.dueDate)
    }

    private fun transaction(merchant: String) = Transaction(
        amount = 4.0,
        type = TransactionType.EXPENSE,
        category = TransactionCategory.FOOD,
        merchant = merchant,
    )

    private fun debt(
        friendName: String,
        direction: DebtDirection,
        amount: Double = 8.0,
        dueDate: LocalDate? = null,
    ) = Debt(
        friendName = friendName,
        amount = amount,
        direction = direction,
        dueDate = dueDate,
    )
}
