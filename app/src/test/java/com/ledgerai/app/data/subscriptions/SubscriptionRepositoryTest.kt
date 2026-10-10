package com.ledgerai.app.data.subscriptions

import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import com.ledgerai.app.domain.subscriptions.SubscriptionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionRepositoryTest {

    private val dao = MemorySubscriptionDao()
    private var now = 1_000L
    private val repository = SubscriptionRepository(dao, clock = { now })

    @Test
    fun add_storesUnknownAmountAsNullNotZero() = runBlocking {
        repository.add(
            "user-1",
            NewSubscription(
                merchant = "  Netflix  ",
                amount = SubscriptionAmount.Unknown,
                period = SubscriptionPeriod.MONTHLY,
                nextRenewalOn = LocalDate.of(2026, 11, 1),
                sourceId = "manual"
            )
        )
        val stored = dao.rows.single()
        assertNull(stored.amount)
        assertEquals("Netflix", stored.merchant)
        assertEquals("active", stored.status)
        assertEquals("user-1", stored.userId)
        assertEquals("manual", stored.sourceId)
        val listed = repository.listActive("user-1").single()
        assertEquals(SubscriptionAmount.Unknown, listed.amount)
        assertTrue(listed.amount != SubscriptionAmount.Known(0.0))
    }

    @Test
    fun listActive_keepsKnownZeroAndHidesOtherUsers() = runBlocking {
        repository.add("user-1", draft(SubscriptionAmount.Known(0.0), "Free trial"))
        repository.add("user-2", draft(SubscriptionAmount.Known(9.0), "Other"))
        val mine = repository.listActive("user-1")
        assertEquals(listOf("Free trial"), mine.map { it.merchant })
        assertEquals(SubscriptionAmount.Known(0.0), mine.single().amount)
    }

    @Test
    fun markCancelled_setsCancelRequestedAndDoesNotContactTheMerchant() = runBlocking {
        val id = repository.add("user-1", draft(SubscriptionAmount.Known(15.99), "Spotify"))
        val result = repository.markCancelled(id, "user-1")
        assertTrue(result.updated)
        assertFalse(result.merchantContacted)
        assertEquals(SubscriptionStatus.CANCEL_REQUESTED, result.status)
        assertEquals("cancel_requested", dao.rows.single().status)
        assertNull(dao.rows.single().deletedAt)
        assertTrue(repository.listActive("user-1").isEmpty())
    }

    @Test
    fun markCancelled_doesNotChangeAnotherUsersRow() = runBlocking {
        val id = repository.add("user-1", draft(SubscriptionAmount.Unknown, "iCloud"))
        val result = repository.markCancelled(id, "user-2")
        assertFalse(result.updated)
        assertFalse(result.merchantContacted)
        assertEquals("active", dao.rows.single().status)
    }

    private fun draft(amount: SubscriptionAmount, merchant: String) = NewSubscription(
        merchant = merchant,
        amount = amount,
        period = SubscriptionPeriod.YEARLY,
        nextRenewalOn = LocalDate.of(2026, 12, 1)
    )
}

private class MemorySubscriptionDao : SubscriptionDao {
    val rows = mutableListOf<SubscriptionEntity>()
    private val ticks = MutableStateFlow(0)
    private var nextId = 1L

    override suspend fun insert(entity: SubscriptionEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        ticks.value += 1
        return id
    }

    override suspend fun listActive(userId: String?): List<SubscriptionEntity> =
        rows.filter { it.deletedAt == null && it.status == "active" && it.userId == userId }
            .sortedWith(compareBy({ it.nextRenewalOn }, { it.id }))

    override fun observeActive(userId: String?): Flow<List<SubscriptionEntity>> =
        ticks.map { listActive(userId) }

    override suspend fun updateStatus(id: Long, userId: String?, status: String, now: Long): Int {
        val index = rows.indexOfFirst {
            it.id == id && it.deletedAt == null && it.status == "active" && it.userId == userId
        }
        if (index < 0) return 0
        rows[index] = rows[index].copy(status = status, updatedAt = now)
        ticks.value += 1
        return 1
    }
}
