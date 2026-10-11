package com.ledgerai.app.domain.assistant

import com.ledgerai.app.domain.model.DebtDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContextSelectorTest {

    @Test
    fun coffeeAtLidl_keepsLidlAndDropsStarbucks() {
        val lidl = KnownMerchant("Lidl")
        val starbucks = KnownMerchant("Starbucks")

        val bundle = selectContext(
            utterance = "coffee at Lidl",
            merchants = listOf(lidl, starbucks),
            debts = emptyList(),
        )

        assertEquals(listOf(lidl), bundle.merchants)
        assertEquals(emptyList<OpenDebtFact>(), bundle.debts)
    }

    @Test
    fun noMerchantMatch_returnsAtMostFiveRecentNames() {
        val recent = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
            .map { KnownMerchant(it) }

        val bundle = selectContext(
            utterance = "nothing about those shops",
            merchants = recent,
            debts = emptyList(),
        )

        assertEquals(recent.take(5), bundle.merchants)
        assertEquals(emptyList<OpenDebtFact>(), bundle.debts)
    }

    @Test
    fun paySarah_includesOnlySarahDebt() {
        val due = LocalDate.of(2026, 12, 1)
        val sarah = OpenDebtFact(
            person = "Sarah",
            direction = DebtDirection.I_OWE,
            amount = null,
            dueDate = due,
        )
        val ben = OpenDebtFact(
            person = "Ben",
            direction = DebtDirection.THEY_OWE,
            amount = 18.0,
            dueDate = null,
        )

        val bundle = selectContext(
            utterance = "pay Sarah",
            merchants = emptyList(),
            debts = listOf(sarah, ben),
        )

        assertEquals(emptyList<KnownMerchant>(), bundle.merchants)
        assertEquals(listOf(sarah), bundle.debts)
        assertEquals(DebtDirection.I_OWE, bundle.debts.single().direction)
        assertEquals(null, bundle.debts.single().amount)
        assertEquals(due, bundle.debts.single().dueDate)
    }

    @Test
    fun coffeeAtLidl_includesNoDebts() {
        val sarah = OpenDebtFact(
            person = "Sarah",
            direction = DebtDirection.THEY_OWE,
            amount = 20.0,
            dueDate = LocalDate.of(2026, 10, 11),
        )

        val bundle = selectContext(
            utterance = "coffee at Lidl",
            merchants = listOf(KnownMerchant("Lidl")),
            debts = listOf(sarah),
        )

        assertEquals(listOf(KnownMerchant("Lidl")), bundle.merchants)
        assertEquals(emptyList<OpenDebtFact>(), bundle.debts)
    }

    @Test
    fun sa_doesNotMatchSarah() {
        val recent = listOf("Sarah", "Mina", "Omar", "Priya", "Quinn", "Rosa")
            .map { KnownMerchant(it) }
        val sarah = OpenDebtFact(
            person = "Sarah",
            direction = DebtDirection.I_OWE,
            amount = 8.0,
            dueDate = null,
        )

        val bundle = selectContext(
            utterance = "Sa",
            merchants = recent,
            debts = listOf(sarah),
        )

        assertEquals(recent.take(5), bundle.merchants)
        assertEquals(emptyList<OpenDebtFact>(), bundle.debts)
    }

    @Test
    fun emptyUtterance_returnsEmptyBundle() {
        val bundle = selectContext(
            utterance = "",
            merchants = listOf(KnownMerchant("Lidl"), KnownMerchant("Starbucks")),
            debts = listOf(
                OpenDebtFact(
                    person = "Sarah",
                    direction = DebtDirection.I_OWE,
                    amount = 5.0,
                    dueDate = null,
                ),
            ),
        )

        assertEquals(ContextBundle(emptyList(), emptyList()), bundle)
    }

    @Test
    fun merchantMatch_isCaseInsensitive() {
        val bundle = selectContext(
            utterance = "coffee at lidl",
            merchants = listOf(KnownMerchant("Lidl"), KnownMerchant("Starbucks")),
            debts = emptyList(),
        )

        assertEquals(listOf(KnownMerchant("Lidl")), bundle.merchants)
    }

    @Test
    fun phrase_matchesWholeMerchantName() {
        val costa = KnownMerchant("Costa Coffee")

        val bundle = selectContext(
            utterance = "stopped at Costa Coffee",
            merchants = listOf(costa, KnownMerchant("Starbucks")),
            debts = emptyList(),
        )

        assertEquals(listOf(costa), bundle.merchants)
    }

    @Test
    fun matchingMerchants_areCappedAtFiveInInputOrder() {
        val names = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot")
        val merchants = names.map { KnownMerchant(it) }

        val bundle = selectContext(
            utterance = names.joinToString(" "),
            merchants = merchants,
            debts = emptyList(),
        )

        assertEquals(merchants.take(5), bundle.merchants)
    }

    @Test
    fun recentMerchants_areDistinctAndIgnoreBlanks() {
        val merchants = listOf(
            KnownMerchant(" "),
            KnownMerchant("Lidl"),
            KnownMerchant("lidl"),
            KnownMerchant("Aldi"),
            KnownMerchant("Spar"),
            KnownMerchant("Tesco"),
            KnownMerchant("Coop"),
        )

        val bundle = selectContext(
            utterance = "hello",
            merchants = merchants,
            debts = listOf(
                OpenDebtFact(
                    person = " ",
                    direction = DebtDirection.THEY_OWE,
                    amount = 1.0,
                    dueDate = null,
                ),
            ),
        )

        assertEquals(
            listOf("Lidl", "Aldi", "Spar", "Tesco", "Coop").map { KnownMerchant(it) },
            bundle.merchants,
        )
        assertEquals(emptyList<OpenDebtFact>(), bundle.debts)
    }
}
