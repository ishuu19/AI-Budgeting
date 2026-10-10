package com.ledgerai.app.domain.subscriptions

import java.time.LocalDate

/**
 * Fields follow docs/life-os/data-model.md `subscriptions`:
 * id, user_id, merchant, amount, period, next_renewal_on,
 * status[active|cancel_requested|cancelled_confirmed], source_id.
 * updatedAt and deletedAt are the shared soft-delete sync columns.
 * remoteId is the local Room key for a future Supabase uuid, same as other entities.
 * There is no separate active column: active means status active and not deleted.
 */
data class Subscription(
    val id: Long = 0,
    val userId: String? = null,
    val merchant: String,
    val amount: SubscriptionAmount = SubscriptionAmount.Unknown,
    val period: SubscriptionPeriod,
    val nextRenewalOn: LocalDate,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val sourceId: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val remoteId: String? = null
) {
    val isActive: Boolean
        get() = status == SubscriptionStatus.ACTIVE && deletedAt == null
}

/** A blank amount is [Unknown]. A known 0 is [Known] and must not be used as a stand-in for unknown. */
sealed interface SubscriptionAmount {
    data class Known(val value: Double) : SubscriptionAmount
    data object Unknown : SubscriptionAmount
}

enum class SubscriptionPeriod(val wire: String, val displayName: String) {
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    QUARTERLY("quarterly", "Quarterly"),
    YEARLY("yearly", "Yearly");

    companion object {
        fun fromWire(value: String): SubscriptionPeriod =
            entries.firstOrNull { it.wire == value } ?: error("Unknown subscription period: $value")
    }
}

enum class SubscriptionStatus(val wire: String) {
    ACTIVE("active"),
    CANCEL_REQUESTED("cancel_requested"),
    CANCELLED_CONFIRMED("cancelled_confirmed");

    companion object {
        fun fromWire(value: String): SubscriptionStatus =
            entries.firstOrNull { it.wire == value } ?: error("Unknown subscription status: $value")
    }
}

data class NewSubscription(
    val merchant: String,
    val amount: SubscriptionAmount,
    val period: SubscriptionPeriod,
    val nextRenewalOn: LocalDate,
    val sourceId: String? = null
)

data class UpcomingCharge(
    val subscriptionId: Long,
    val merchant: String,
    val amount: SubscriptionAmount,
    val chargeOn: LocalDate
)
