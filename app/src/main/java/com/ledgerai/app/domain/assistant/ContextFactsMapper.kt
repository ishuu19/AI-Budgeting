package com.ledgerai.app.domain.assistant

import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Transaction
import java.time.LocalDate

/** Copies stored merchant names. A blank name is left out. */
fun knownMerchants(transactions: List<Transaction>): List<KnownMerchant> =
    transactions.mapNotNull { transaction ->
        val name = transaction.merchant
        if (name.isBlank()) null else KnownMerchant(name)
    }

/** Copies stored open-debt fields. A blank person is left out. */
fun openDebtFacts(debts: List<Debt>): List<OpenDebtFact> =
    debts.mapNotNull { debt ->
        openDebtFact(debt.friendName, debt.direction, debt.amount, debt.dueDate)
    }

/**
 * Copies one debt. Amount and due date stay null when unknown.
 * A blank person is left out.
 */
fun openDebtFact(
    person: String,
    direction: DebtDirection,
    amount: Double?,
    dueDate: LocalDate?,
): OpenDebtFact? {
    if (person.isBlank()) return null
    return OpenDebtFact(
        person = person,
        direction = direction,
        amount = amount,
        dueDate = dueDate,
    )
}
