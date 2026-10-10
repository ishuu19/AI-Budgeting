package com.ledgerai.app.domain.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionChargesTest {

    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun nextChargeDate_keepsADateThatIsTodayOrLater() {
        assertEquals(
            today,
            SubscriptionCharges.nextChargeDate(today, SubscriptionPeriod.MONTHLY, today)
        )
        assertEquals(
            today.plusDays(3),
            SubscriptionCharges.nextChargeDate(today.plusDays(3), SubscriptionPeriod.WEEKLY, today)
        )
    }

    @Test
    fun nextChargeDate_rollsMonthlyFromTheOriginalDay() {
        val next = SubscriptionCharges.nextChargeDate(
            LocalDate.of(2026, 1, 15),
            SubscriptionPeriod.MONTHLY,
            today
        )
        assertEquals(LocalDate.of(2026, 10, 15), next)
    }

    @Test
    fun nextChargeDate_monthEndKeepsTheAnchorDayWhenTheMonthHasIt() {
        val next = SubscriptionCharges.nextChargeDate(
            LocalDate.of(2026, 1, 31),
            SubscriptionPeriod.MONTHLY,
            LocalDate.of(2026, 3, 1)
        )
        assertEquals(LocalDate.of(2026, 3, 31), next)
    }

    @Test
    fun nextChargeDate_rollsWeeklyQuarterlyAndYearly() {
        assertEquals(
            LocalDate.of(2026, 10, 15),
            SubscriptionCharges.nextChargeDate(LocalDate.of(2026, 10, 1), SubscriptionPeriod.WEEKLY, today)
        )
        assertEquals(
            today,
            SubscriptionCharges.nextChargeDate(LocalDate.of(2026, 1, 11), SubscriptionPeriod.QUARTERLY, today)
        )
        assertEquals(
            LocalDate.of(2027, 3, 1),
            SubscriptionCharges.nextChargeDate(LocalDate.of(2024, 3, 1), SubscriptionPeriod.YEARLY, today)
        )
    }

    @Test
    fun upcomingWithin_includesTheLastDayAndExcludesTheDayAfter() {
        val onEdge = active(nextRenewalOn = today.plusDays(7))
        val after = active(id = 2, merchant = "Later", nextRenewalOn = today.plusDays(8))
        val charges = SubscriptionCharges.upcomingWithin(listOf(onEdge, after), today, 7)
        assertEquals(listOf(onEdge.id), charges.map { it.subscriptionId })
        assertEquals(today.plusDays(7), charges.single().chargeOn)
    }

    @Test
    fun upcomingWithin_usesTheRolledDateAndSkipsInactiveOrDeleted() {
        val due = active(nextRenewalOn = today.minusMonths(1))
        val requested = due.copy(id = 2, status = SubscriptionStatus.CANCEL_REQUESTED)
        val confirmed = due.copy(id = 3, status = SubscriptionStatus.CANCELLED_CONFIRMED)
        val deleted = due.copy(id = 4, deletedAt = 1L)
        val charges = SubscriptionCharges.upcomingWithin(
            listOf(due, requested, confirmed, deleted),
            today,
            40
        )
        assertEquals(listOf(due.id), charges.map { it.subscriptionId })
        assertEquals(today, charges.single().chargeOn)
    }

    @Test
    fun upcomingWithin_unknownAmountIsNotZero() {
        val charge = SubscriptionCharges.upcomingWithin(
            listOf(active(amount = SubscriptionAmount.Unknown)),
            today,
            0
        ).single()
        assertEquals(SubscriptionAmount.Unknown, charge.amount)
        assertNull((charge.amount as? SubscriptionAmount.Known)?.value)
        assertTrue(charge.amount != SubscriptionAmount.Known(0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun upcomingWithin_rejectsANegativeWindow() {
        SubscriptionCharges.upcomingWithin(emptyList(), today, -1)
    }

    @Test
    fun upcomingWithin_knownZeroStaysAKnownZero() {
        val charge = SubscriptionCharges.upcomingWithin(
            listOf(active(amount = SubscriptionAmount.Known(0.0))),
            today,
            0
        ).single()
        assertEquals(SubscriptionAmount.Known(0.0), charge.amount)
    }

    private fun active(
        id: Long = 1,
        merchant: String = "Netflix",
        amount: SubscriptionAmount = SubscriptionAmount.Known(15.99),
        period: SubscriptionPeriod = SubscriptionPeriod.MONTHLY,
        nextRenewalOn: LocalDate = today,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        deletedAt: Long? = null
    ) = Subscription(
        id = id,
        userId = "user-1",
        merchant = merchant,
        amount = amount,
        period = period,
        nextRenewalOn = nextRenewalOn,
        status = status,
        sourceId = null,
        deletedAt = deletedAt
    )
}
