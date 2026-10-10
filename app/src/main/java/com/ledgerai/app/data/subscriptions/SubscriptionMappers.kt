package com.ledgerai.app.data.subscriptions

import com.ledgerai.app.domain.subscriptions.Subscription
import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import com.ledgerai.app.domain.subscriptions.SubscriptionStatus

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    userId = userId,
    merchant = merchant,
    amount = amount.toSubscriptionAmount(),
    period = SubscriptionPeriod.fromWire(period),
    nextRenewalOn = nextRenewalOn,
    status = SubscriptionStatus.fromWire(status),
    sourceId = sourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    remoteId = remoteId
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    userId = userId,
    merchant = merchant,
    amount = amount.toStoredAmount(),
    period = period.wire,
    nextRenewalOn = nextRenewalOn,
    status = status.wire,
    sourceId = sourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    remoteId = remoteId
)

fun Double?.toSubscriptionAmount(): SubscriptionAmount = when (this) {
    null -> SubscriptionAmount.Unknown
    else -> SubscriptionAmount.Known(this)
}

fun SubscriptionAmount.toStoredAmount(): Double? = when (this) {
    is SubscriptionAmount.Known -> value
    SubscriptionAmount.Unknown -> null
}
