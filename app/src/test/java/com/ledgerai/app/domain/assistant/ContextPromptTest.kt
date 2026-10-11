package com.ledgerai.app.domain.assistant

import com.ledgerai.app.domain.model.DebtDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContextPromptTest {

    @Test
    fun emptyBundle_rendersEmptyString() {
        val text = renderContext(ContextBundle(merchants = emptyList(), debts = emptyList()))

        assertEquals("", text)
    }

    @Test
    fun nullDueDate_omitsDue() {
        val text = renderContext(
            ContextBundle(
                merchants = emptyList(),
                debts = listOf(
                    OpenDebtFact(
                        person = "Sarah",
                        direction = DebtDirection.THEY_OWE,
                        amount = 20.0,
                        dueDate = null,
                    ),
                ),
            ),
        )

        assertEquals("Open debts: Sarah THEY_OWE 20", text)
    }

    @Test
    fun nullAmount_omitsAmount() {
        val text = renderContext(
            ContextBundle(
                merchants = emptyList(),
                debts = listOf(
                    OpenDebtFact(
                        person = "Sarah",
                        direction = DebtDirection.THEY_OWE,
                        amount = null,
                        dueDate = LocalDate.of(2026, 10, 20),
                    ),
                ),
            ),
        )

        assertEquals("Open debts: Sarah THEY_OWE due 2026-10-20", text)
    }

    @Test
    fun presentAmount_copiesAmountAndRealDirectionNames() {
        val text = renderContext(
            ContextBundle(
                merchants = emptyList(),
                debts = listOf(
                    OpenDebtFact(
                        person = "Sarah",
                        direction = DebtDirection.THEY_OWE,
                        amount = 20.0,
                        dueDate = LocalDate.of(2026, 10, 20),
                    ),
                    OpenDebtFact(
                        person = "Ada",
                        direction = DebtDirection.I_OWE,
                        amount = 12.0,
                        dueDate = LocalDate.of(2026, 10, 11),
                    ),
                ),
            ),
        )

        assertEquals(
            "Open debts: Sarah THEY_OWE 20 due 2026-10-20; Ada I_OWE 12 due 2026-10-11",
            text,
        )
    }

    @Test
    fun merchantsAndDebts_produceBothLines() {
        val text = renderContext(
            ContextBundle(
                merchants = listOf(KnownMerchant("Lidl"), KnownMerchant("Starbucks")),
                debts = listOf(
                    OpenDebtFact(
                        person = "Sarah",
                        direction = DebtDirection.THEY_OWE,
                        amount = 20.0,
                        dueDate = LocalDate.of(2026, 10, 20),
                    ),
                ),
            ),
        )

        assertEquals(
            "Known merchants: Lidl, Starbucks\nOpen debts: Sarah THEY_OWE 20 due 2026-10-20",
            text,
        )
    }

    @Test
    fun debtLine_equalsNameDirectionAmountAndDueDateOnly() {
        val text = renderContext(
            ContextBundle(
                merchants = emptyList(),
                debts = listOf(
                    OpenDebtFact(
                        person = "Sarah",
                        direction = DebtDirection.THEY_OWE,
                        amount = 20.0,
                        dueDate = LocalDate.of(2026, 10, 20),
                    ),
                ),
            ),
        )

        assertEquals("Open debts: Sarah THEY_OWE 20 due 2026-10-20", text)
    }

    @Test
    fun merchantsOnly_usesStoredSpellingsOnOneLine() {
        val text = renderContext(
            ContextBundle(
                merchants = listOf(KnownMerchant("Lidl"), KnownMerchant("Starbucks")),
                debts = emptyList(),
            ),
        )

        assertEquals("Known merchants: Lidl, Starbucks", text)
    }
}
