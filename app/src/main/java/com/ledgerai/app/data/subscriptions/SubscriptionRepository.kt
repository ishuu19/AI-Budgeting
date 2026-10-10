package com.ledgerai.app.data.subscriptions

import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.Subscription
import com.ledgerai.app.domain.subscriptions.SubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local subscription records.
 *
 * [markCancelled] sets status to cancel_requested. It does not contact a merchant
 * and must not be reported as a confirmed cancellation.
 *
 * Reminder scheduling is an integrator step: use
 * [com.ledgerai.app.domain.subscriptions.SubscriptionCharges.nextChargeDate]
 * from a future worker. This repository does not schedule WorkManager jobs.
 */
class SubscriptionRepository(
    private val dao: SubscriptionDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun add(userId: String?, draft: NewSubscription): Long {
        val merchant = draft.merchant.trim()
        require(merchant.isNotEmpty()) { "merchant is required" }
        return dao.insert(
            Subscription(
                userId = userId,
                merchant = merchant,
                amount = draft.amount,
                period = draft.period,
                nextRenewalOn = draft.nextRenewalOn,
                status = SubscriptionStatus.ACTIVE,
                sourceId = draft.sourceId,
                updatedAt = clock(),
                deletedAt = null
            ).toEntity()
        )
    }

    suspend fun listActive(userId: String?): List<Subscription> =
        dao.listActive(userId).map { it.toDomain() }

    fun observeActive(userId: String?): Flow<List<Subscription>> =
        dao.observeActive(userId).map { rows -> rows.map { it.toDomain() } }

    suspend fun markCancelled(id: Long, userId: String?): LocalCancelResult {
        val updated = dao.updateStatus(
            id = id,
            userId = userId,
            status = SubscriptionStatus.CANCEL_REQUESTED.wire,
            now = clock()
        )
        return LocalCancelResult(
            updated = updated > 0,
            status = SubscriptionStatus.CANCEL_REQUESTED
        )
    }
}

class LocalCancelResult(
    val updated: Boolean,
    val status: SubscriptionStatus
) {
    /** Always false. This slice never contacts a merchant. */
    val merchantContacted: Boolean = false
}
