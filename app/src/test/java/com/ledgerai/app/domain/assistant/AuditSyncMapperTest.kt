package com.ledgerai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditSyncMapperTest {

    @Test
    fun action_roundTrip_keepsAppliedAtDeletedAtAndAmountText() {
        val plan = """{"merchant":"Lidl","amount":"12.50"}"""
        val original = ActionSyncRecord(
            localId = 7L,
            remoteId = "act-7",
            userId = "user-1",
            inputRef = "voice:9",
            actionIndex = 2,
            planJson = plan,
            status = "applied",
            risk = "high",
            appliedAt = 1_728_388_800_123L,
            error = "none",
            updatedAt = 1_728_388_900_456L,
            deletedAt = 1_728_389_000_789L,
        )

        val remote = AuditSyncMapper.actionToRemote(original, pushUserId = "user-1")
        val back = AuditSyncMapper.actionFromRemote(remote, existingLocalId = 7L)

        assertEquals(original, back)
        assertEquals(plan, remote.planJson)
        assertEquals(plan, back.planJson)
        assertTrue(back.planJson.contains("\"12.50\""))
        assertEquals("applied", back.status)
    }

    @Test
    fun event_roundTrip_keepsDeletedAtAndAmountText() {
        val body = """{"type":"item.added","amount":"3.00"}"""
        val original = EventSyncRecord(
            localId = 4L,
            remoteId = "evt-4",
            userId = "user-1",
            sourceLocalActionId = 7L,
            sourceRemoteActionId = "act-7",
            payload = body,
            updatedAt = 1_728_388_900_456L,
            deletedAt = 1_728_389_000_789L,
        )

        val remote = AuditSyncMapper.eventToRemote(
            original,
            pushUserId = "user-1",
            parentRemoteId = "act-7",
        )
        val back = AuditSyncMapper.eventFromRemote(
            remote,
            existingLocalId = 4L,
            sourceLocalActionId = 7L,
        )

        assertEquals(original, back)
        assertEquals("act-7", remote.sourceActionId)
        assertEquals(body, remote.payload)
        assertEquals(body, back.payload)
    }

    @Test
    fun nullAppliedAtAndDeletedAt_stayNull() {
        val action = ActionSyncRecord(
            localId = 1L,
            remoteId = null,
            userId = null,
            inputRef = "in",
            actionIndex = 0,
            planJson = "{}",
            status = "proposed",
            risk = "low",
            appliedAt = null,
            error = "",
            updatedAt = 1_000L,
            deletedAt = null,
        )
        val pushed = AuditSyncMapper.actionToRemote(action, pushUserId = null)
        assertNull(pushed.id)
        assertNull(pushed.userId)
        assertNull(pushed.appliedAt)
        assertNull(pushed.deletedAt)

        val fromNulls = AuditSyncMapper.actionFromRemote(
            ActionRemotePayload(
                id = null,
                userId = null,
                inputRef = null,
                actionIndex = null,
                planJson = null,
                status = "approved",
                risk = "medium",
                appliedAt = null,
                error = null,
                updatedAt = "1970-01-01T00:00:01Z",
                deletedAt = null,
            ),
            existingLocalId = 1L,
        )
        assertEquals(1L, fromNulls.localId)
        assertNull(fromNulls.remoteId)
        assertNull(fromNulls.userId)
        assertNull(fromNulls.appliedAt)
        assertNull(fromNulls.deletedAt)
        assertEquals("", fromNulls.inputRef)
        assertEquals(0, fromNulls.actionIndex)

        val event = EventSyncRecord(
            localId = 2L,
            remoteId = null,
            userId = null,
            sourceLocalActionId = null,
            sourceRemoteActionId = null,
            payload = "{}",
            updatedAt = 1_000L,
            deletedAt = null,
        )
        val eventPushed = AuditSyncMapper.eventToRemote(event, pushUserId = null, parentRemoteId = null)
        assertNull(eventPushed.id)
        assertNull(eventPushed.userId)
        assertNull(eventPushed.sourceActionId)
        assertNull(eventPushed.deletedAt)

        val eventBack = AuditSyncMapper.eventFromRemote(
            EventRemotePayload(
                id = null,
                userId = null,
                sourceActionId = null,
                payload = null,
                updatedAt = "1970-01-01T00:00:01Z",
                deletedAt = null,
            ),
            existingLocalId = 2L,
            sourceLocalActionId = null,
        )
        assertNull(eventBack.remoteId)
        assertNull(eventBack.userId)
        assertNull(eventBack.sourceLocalActionId)
        assertNull(eventBack.sourceRemoteActionId)
        assertNull(eventBack.deletedAt)
        assertEquals("", eventBack.payload)
    }

    @Test
    fun blankStatus_becomesProposed_andAppliedOnlyWhenPayloadSaysApplied() {
        val blank = AuditSyncMapper.actionFromRemote(
            actionPayload(status = "  ", appliedAt = "2024-10-08T12:00:00Z"),
            existingLocalId = 3L,
        )
        assertEquals("proposed", blank.status)
        assertEquals(1_728_388_800_000L, blank.appliedAt)

        val appliedWithoutTime = AuditSyncMapper.actionFromRemote(
            actionPayload(status = "applied", appliedAt = null),
            existingLocalId = 3L,
        )
        assertEquals("applied", appliedWithoutTime.status)
        assertNull(appliedWithoutTime.appliedAt)

        val approved = AuditSyncMapper.actionFromRemote(
            actionPayload(status = "approved", appliedAt = ""),
            existingLocalId = 3L,
        )
        assertEquals("approved", approved.status)
        assertNull(approved.appliedAt)

        val pushed = AuditSyncMapper.actionToRemote(
            sampleAction(status = "   ", appliedAt = 1_728_388_800_000L),
            pushUserId = "user-1",
        )
        assertEquals("proposed", pushed.status)
        assertEquals("2024-10-08T12:00:00Z", pushed.appliedAt)
    }

    @Test
    fun planJson_copiedVerbatimIncludingAmount() {
        val plan = """{"userId":"from-plan","amount":"19.99"}"""
        val remote = AuditSyncMapper.actionToRemote(
            sampleAction(planJson = plan, userId = "stored-user"),
            pushUserId = "push-user",
        )
        assertEquals("push-user", remote.userId)
        assertEquals(plan, remote.planJson)

        val copied = AuditSyncMapper.actionFromRemote(
            actionPayload(planJson = plan, userId = "remote-user"),
            existingLocalId = 5L,
        )
        assertEquals(plan, copied.planJson)
        assertEquals("remote-user", copied.userId)
        assertTrue(copied.planJson.contains("\"19.99\""))

        val blankPlan = AuditSyncMapper.actionFromRemote(
            actionPayload(planJson = "   "),
            existingLocalId = 5L,
        )
        assertEquals("{}", blankPlan.planJson)
        assertEquals("low", blankPlan.risk)
        assertEquals("", blankPlan.error)
    }

    @Test
    fun eventParentId_passedThroughOrNull() {
        val record = EventSyncRecord(
            localId = 8L,
            remoteId = "evt",
            userId = "stored",
            sourceLocalActionId = 1L,
            sourceRemoteActionId = "ignored-on-push",
            payload = """{"amount":"1.00"}""",
            updatedAt = 10L,
            deletedAt = null,
        )

        val withParent = AuditSyncMapper.eventToRemote(
            record,
            pushUserId = "push-user",
            parentRemoteId = "parent-9",
        )
        assertEquals("parent-9", withParent.sourceActionId)
        assertEquals("push-user", withParent.userId)

        val withoutParent = AuditSyncMapper.eventToRemote(
            record,
            pushUserId = "push-user",
            parentRemoteId = null,
        )
        assertNull(withoutParent.sourceActionId)

        val fromParent = AuditSyncMapper.eventFromRemote(
            EventRemotePayload(
                id = "evt",
                userId = "remote-user",
                sourceActionId = "parent-9",
                payload = """{"amount":"1.00"}""",
                updatedAt = "1970-01-01T00:00:00.010Z",
                deletedAt = null,
            ),
            existingLocalId = 8L,
            sourceLocalActionId = 1L,
        )
        assertEquals(1L, fromParent.sourceLocalActionId)
        assertEquals("parent-9", fromParent.sourceRemoteActionId)
        assertEquals("""{"amount":"1.00"}""", fromParent.payload)

        val fromNull = AuditSyncMapper.eventFromRemote(
            EventRemotePayload(
                id = "evt",
                userId = null,
                sourceActionId = null,
                payload = "{}",
                updatedAt = null,
                deletedAt = "  ",
            ),
            existingLocalId = 8L,
            sourceLocalActionId = null,
        )
        assertNull(fromNull.sourceLocalActionId)
        assertNull(fromNull.sourceRemoteActionId)
        assertNull(fromNull.deletedAt)
        assertEquals(0L, fromNull.updatedAt)
    }

    @Test
    fun blankTimestamp_doesNotBecomeNow() {
        val action = AuditSyncMapper.actionFromRemote(
            actionPayload(
                appliedAt = " ",
                updatedAt = "",
                deletedAt = null,
            ),
            existingLocalId = 9L,
        )
        assertEquals(0L, action.updatedAt)
        assertNull(action.appliedAt)
        assertNull(action.deletedAt)

        val event = AuditSyncMapper.eventFromRemote(
            EventRemotePayload(
                id = null,
                userId = null,
                sourceActionId = null,
                payload = "ok",
                updatedAt = "   ",
                deletedAt = "",
            ),
            existingLocalId = 9L,
            sourceLocalActionId = null,
        )
        assertEquals(0L, event.updatedAt)
        assertNull(event.deletedAt)

        val pushed = AuditSyncMapper.actionToRemote(
            sampleAction(appliedAt = null, deletedAt = null, updatedAt = 0L),
            pushUserId = "user-1",
        )
        assertNull(pushed.appliedAt)
        assertNull(pushed.deletedAt)
        assertEquals("1970-01-01T00:00:00Z", pushed.updatedAt)
    }

    private fun sampleAction(
        planJson: String = "{}",
        status: String = "proposed",
        userId: String? = "user-1",
        appliedAt: Long? = null,
        deletedAt: Long? = null,
        updatedAt: Long = 50L,
    ) = ActionSyncRecord(
        localId = 5L,
        remoteId = "act-5",
        userId = userId,
        inputRef = "voice",
        actionIndex = 1,
        planJson = planJson,
        status = status,
        risk = "low",
        appliedAt = appliedAt,
        error = "",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun actionPayload(
        status: String? = "proposed",
        planJson: String? = "{}",
        userId: String? = null,
        appliedAt: String? = null,
        updatedAt: String? = "1970-01-01T00:00:00.050Z",
        deletedAt: String? = null,
        risk: String? = " ",
        error: String? = " ",
    ) = ActionRemotePayload(
        id = "act-5",
        userId = userId,
        inputRef = "voice",
        actionIndex = 1,
        planJson = planJson,
        status = status,
        risk = risk,
        appliedAt = appliedAt,
        error = error,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}
