package com.ledgerai.app.domain.subscriptions

import java.time.LocalDate

/**
 * Pure charge dates. A later integrator can call [nextChargeDate] from a WorkManager
 * reminder. This function does not schedule work.
 */
object SubscriptionCharges {

    /**
     * Next charge on or after [today], stepping from [nextRenewalOn] by [period].
     * Steps are counted from the original date so a monthly charge on the 31st
     * stays on the 31st in months that have one.
     */
    fun nextChargeDate(
        nextRenewalOn: LocalDate,
        period: SubscriptionPeriod,
        today: LocalDate
    ): LocalDate {
        if (!nextRenewalOn.isBefore(today)) return nextRenewalOn
        var steps = 1
        var candidate = step(nextRenewalOn, period, steps)
        while (candidate.isBefore(today)) {
            steps += 1
            candidate = step(nextRenewalOn, period, steps)
        }
        return candidate
    }

    /**
     * Active, not-deleted subscriptions whose next charge is within [withinDays]
     * of [today], including today and the last day. [SubscriptionAmount.Unknown]
     * is copied through and is never replaced with zero.
     */
    fun upcomingWithin(
        subscriptions: List<Subscription>,
        today: LocalDate,
        withinDays: Int
    ): List<UpcomingCharge> {
        require(withinDays >= 0) { "withinDays must be >= 0" }
        val end = today.plusDays(withinDays.toLong())
        return subscriptions
            .asSequence()
            .filter { it.isActive }
            .map { subscription ->
                UpcomingCharge(
                    subscriptionId = subscription.id,
                    merchant = subscription.merchant,
                    amount = subscription.amount,
                    chargeOn = nextChargeDate(subscription.nextRenewalOn, subscription.period, today)
                )
            }
            .filter { !it.chargeOn.isAfter(end) }
            .sortedWith(compareBy({ it.chargeOn }, { it.subscriptionId }))
            .toList()
    }

    private fun step(anchor: LocalDate, period: SubscriptionPeriod, steps: Int): LocalDate =
        when (period) {
            SubscriptionPeriod.WEEKLY -> anchor.plusWeeks(steps.toLong())
            SubscriptionPeriod.MONTHLY -> anchor.plusMonths(steps.toLong())
            SubscriptionPeriod.QUARTERLY -> anchor.plusMonths(3L * steps)
            SubscriptionPeriod.YEARLY -> anchor.plusYears(steps.toLong())
        }
}
