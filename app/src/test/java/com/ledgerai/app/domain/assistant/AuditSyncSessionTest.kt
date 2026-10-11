package com.ledgerai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditSyncSessionTest {

    @Test
    fun remoteNewer_replacesLocal() {
        val store = InMemoryAuditSyncStore()
        store.saveActions(
            listOf(
                action(
                    localId = 5L,
                    remoteId = "act-5",
                    planJson = """{"amount":"1.00"}""",
                    status = "proposed",
                    risk = "low",
                    updatedAt = 1_000L,
                ),
            ),
        )

        val plan = plan(
            store,
            remoteActions = listOf(
                actionPayload(
                    id = "act-5",
                    planJson = """{"merchant":"Lidl","amount":"4.50"}""",
                    status = "applied",
                    risk = "high",
                    appliedAt = "1970-01-01T00:00:02Z",
                    updatedAt = "1970-01-01T00:00:02Z",
                ),
            ),
        )

        val row = plan.actionUpserts.single()
        assertEquals(5L, row.localId)
        assertEquals("act-5", row.remoteId)
        assertEquals("""{"merchant":"Lidl","amount":"4.50"}""", row.planJson)
        assertEquals("applied", row.status)
        assertEquals("high", row.risk)
        assertEquals(2_000L, row.appliedAt)
        assertEquals(2_000L, row.updatedAt)
        assertTrue(plan.actionsToPush.isEmpty())
        assertTrue(plan.eventsToPush.isEmpty())
    }

    @Test
    fun localNewerWithNullRemoteId_adoptsServerIdAndKeepsLocalFields() {
        val store = InMemoryAuditSyncStore()
        val local = action(
            localId = 7L,
            remoteId = null,
            userId = "local-user",
            planJson = """{"amount":"3.00"}""",
            status = "applied",
            risk = "medium",
            appliedAt = 5_000L,
            error = "kept",
            updatedAt = 5_000L,
        )
        store.saveActions(listOf(local))

        val plan = plan(
            store,
            remoteActions = listOf(
                actionPayload(
                    id = "server-uuid",
                    planJson = """{"amount":"9.00"}""",
                    status = "proposed",
                    risk = "low",
                    updatedAt = "1970-01-01T00:00:01Z",
                ),
            ),
        )

        val row = plan.actionUpserts.single()
        assertEquals(7L, row.localId)
        assertEquals("server-uuid", row.remoteId)
        assertEquals(local.userId, row.userId)
        assertEquals(local.planJson, row.planJson)
        assertEquals(local.status, row.status)
        assertEquals(local.risk, row.risk)
        assertEquals(local.appliedAt, row.appliedAt)
        assertEquals(local.error, row.error)
        assertEquals(local.updatedAt, row.updatedAt)
        assertEquals(local.deletedAt, row.deletedAt)
        assertTrue(plan.actionsToPush.isEmpty())
    }

    @Test
    fun duplicateInputRefAndActionIndex_doesNotCreateSecondAction() {
        val store = InMemoryAuditSyncStore()
        store.saveActions(
            listOf(
                action(
                    localId = 3L,
                    remoteId = null,
                    inputRef = "voice:1",
                    actionIndex = 1,
                    planJson = """{"amount":"0.00"}""",
                    updatedAt = 1_000L,
                    deletedAt = 40L,
                ),
            ),
        )

        val plan = plan(
            store,
            remoteActions = listOf(
                actionPayload(
                    id = "remote-a",
                    inputRef = "voice:1",
                    actionIndex = 1,
                    planJson = """{"amount":"1.00"}""",
                    updatedAt = "1970-01-01T00:00:02Z",
                ),
                actionPayload(
                    id = "remote-b",
                    inputRef = "voice:1",
                    actionIndex = 1,
                    planJson = """{"amount":"2.00"}""",
                    updatedAt = "1970-01-01T00:00:03Z",
                ),
            ),
        )

        val row = plan.actionUpserts.single()
        assertEquals(3L, row.localId)
        assertEquals("remote-b", row.remoteId)
        assertEquals("voice:1", row.inputRef)
        assertEquals(1, row.actionIndex)
        assertEquals("""{"amount":"2.00"}""", row.planJson)
        store.saveActions(plan.actionUpserts)
        assertEquals(1, store.loadActions().size)
        assertEquals(3L, store.findAction("voice:1", 1)?.localId)
    }

    @Test
    fun eventWithMissingParent_keepsNullLocalActionIdAndDoesNotCreateAction() {
        val store = InMemoryAuditSyncStore()
        val payload = """{"amount":"3.00"}"""

        val plan = plan(
            store,
            remoteEvents = listOf(
                eventPayload(
                    id = "evt-missing",
                    sourceActionId = "missing-parent",
                    payload = payload,
                    updatedAt = "1970-01-01T00:00:02Z",
                ),
            ),
        )

        assertTrue(plan.actionUpserts.isEmpty())
        assertTrue(store.loadActions().isEmpty())
        val row = plan.eventUpserts.single()
        assertNull(row.sourceLocalActionId)
        assertEquals("missing-parent", row.sourceRemoteActionId)
        assertEquals(payload, row.payload)
        assertEquals("evt-missing", row.remoteId)
    }

    @Test
    fun blankUserId_pushesNothing() {
        val store = InMemoryAuditSyncStore()
        store.saveActions(
            listOf(
                action(
                    localId = 1L,
                    remoteId = null,
                    updatedAt = 100L,
                ),
            ),
        )
        store.saveEvents(
            listOf(
                event(
                    localId = 2L,
                    remoteId = "evt-2",
                    sourceLocalActionId = 1L,
                    updatedAt = 100L,
                ),
            ),
        )
        var idsIssued = 0

        val plan = plan(
            store,
            signedInUserId = "   ",
            sinceMs = 0L,
            newRemoteId = {
                idsIssued += 1
                "should-not-run"
            },
        )

        assertTrue(plan.actionsToPush.isEmpty())
        assertTrue(plan.eventsToPush.isEmpty())
        assertEquals(0, idsIssued)
    }

    @Test
    fun secondRemoteEventForSameAction_doesNotCreateSecondLocalEvent() {
        val store = InMemoryAuditSyncStore()
        store.saveActions(
            listOf(
                action(
                    localId = 4L,
                    remoteId = "act-4",
                    updatedAt = 1_000L,
                ),
            ),
        )
        store.saveEvents(
            listOf(
                event(
                    localId = 9L,
                    remoteId = "evt-old",
                    sourceLocalActionId = 4L,
                    sourceRemoteActionId = "act-4",
                    payload = """{"amount":"1.00"}""",
                    updatedAt = 1_000L,
                ),
            ),
        )

        val plan = plan(
            store,
            remoteEvents = listOf(
                eventPayload(
                    id = "evt-new-1",
                    sourceActionId = "act-4",
                    payload = """{"amount":"2.00"}""",
                    updatedAt = "1970-01-01T00:00:02Z",
                ),
                eventPayload(
                    id = "evt-new-2",
                    sourceActionId = "act-4",
                    payload = """{"amount":"8.00"}""",
                    updatedAt = "1970-01-01T00:00:03Z",
                ),
            ),
        )

        assertTrue(plan.actionUpserts.isEmpty())
        val row = plan.eventUpserts.single()
        assertEquals(9L, row.localId)
        assertEquals(4L, row.sourceLocalActionId)
        assertEquals("act-4", row.sourceRemoteActionId)
        assertEquals("""{"amount":"8.00"}""", row.payload)
        assertEquals("evt-new-2", row.remoteId)
        store.saveEvents(plan.eventUpserts)
        assertEquals(1, store.loadEvents().size)
        assertEquals(9L, store.findEventBySourceAction(4L, "act-4")?.localId)
    }

    private fun plan(
        store: InMemoryAuditSyncStore,
        remoteActions: List<ActionRemotePayload> = emptyList(),
        remoteEvents: List<EventRemotePayload> = emptyList(),
        signedInUserId: String = "user-1",
        sinceMs: Long = Long.MAX_VALUE,
        newRemoteId: () -> String = { error("newRemoteId") },
    ) = AuditSyncSession().plan(
        localActions = store.loadActions(),
        localEvents = store.loadEvents(),
        remoteActions = remoteActions,
        remoteEvents = remoteEvents,
        signedInUserId = signedInUserId,
        sinceMs = sinceMs,
        newRemoteId = newRemoteId,
    )

    private fun action(
        localId: Long,
        remoteId: String?,
        userId: String? = "user-1",
        inputRef: String = "voice:1",
        actionIndex: Int = 0,
        planJson: String = """{"amount":"1.00"}""",
        status: String = "proposed",
        risk: String = "low",
        appliedAt: Long? = null,
        error: String = "",
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = ActionSyncRecord(
        localId = localId,
        remoteId = remoteId,
        userId = userId,
        inputRef = inputRef,
        actionIndex = actionIndex,
        planJson = planJson,
        status = status,
        risk = risk,
        appliedAt = appliedAt,
        error = error,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun event(
        localId: Long,
        remoteId: String?,
        sourceLocalActionId: Long?,
        sourceRemoteActionId: String? = null,
        payload: String = """{"amount":"1.00"}""",
        updatedAt: Long = 0L,
        deletedAt: Long? = null,
    ) = EventSyncRecord(
        localId = localId,
        remoteId = remoteId,
        userId = "user-1",
        sourceLocalActionId = sourceLocalActionId,
        sourceRemoteActionId = sourceRemoteActionId,
        payload = payload,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun actionPayload(
        id: String,
        inputRef: String = "voice:1",
        actionIndex: Int = 0,
        planJson: String,
        status: String = "applied",
        risk: String = "low",
        appliedAt: String? = null,
        updatedAt: String,
    ) = ActionRemotePayload(
        id = id,
        userId = "remote-user",
        inputRef = inputRef,
        actionIndex = actionIndex,
        planJson = planJson,
        status = status,
        risk = risk,
        appliedAt = appliedAt,
        error = "",
        updatedAt = updatedAt,
        deletedAt = null,
    )

    private fun eventPayload(
        id: String,
        sourceActionId: String,
        payload: String,
        updatedAt: String,
    ) = EventRemotePayload(
        id = id,
        userId = "remote-user",
        sourceActionId = sourceActionId,
        payload = payload,
        updatedAt = updatedAt,
        deletedAt = null,
    )
}

/**
 * Test double. Soft-deleted rows stay visible so a pair match can update them.
 */
private class InMemoryAuditSyncStore : AuditSyncStore {
    private val actions = mutableListOf<ActionSyncRecord>()
    private val events = mutableListOf<EventSyncRecord>()

    override fun loadActions(): List<ActionSyncRecord> = actions.toList()

    override fun loadEvents(): List<EventSyncRecord> = events.toList()

    override fun findActionByRemoteId(remoteId: String): ActionSyncRecord? =
        actions.find { it.remoteId == remoteId }

    override fun findAction(inputRef: String, actionIndex: Int): ActionSyncRecord? =
        actions.find { it.inputRef == inputRef && it.actionIndex == actionIndex }

    override fun findEventBySourceAction(
        sourceLocalActionId: Long?,
        sourceRemoteActionId: String?,
    ): EventSyncRecord? = events.find { event ->
        (sourceLocalActionId != null && event.sourceLocalActionId == sourceLocalActionId) ||
            (sourceRemoteActionId != null && event.sourceRemoteActionId == sourceRemoteActionId)
    }

    override fun saveActions(rows: List<ActionSyncRecord>) {
        for (row in rows) {
            val index = actions.indexOfFirst { existing ->
                (existing.inputRef == row.inputRef && existing.actionIndex == row.actionIndex) ||
                    (row.localId != 0L && existing.localId == row.localId) ||
                    (!row.remoteId.isNullOrBlank() && existing.remoteId == row.remoteId)
            }
            if (index >= 0) actions[index] = row else actions.add(row)
        }
    }

    override fun saveEvents(rows: List<EventSyncRecord>) {
        for (row in rows) {
            val index = events.indexOfFirst { existing ->
                (row.localId != 0L && existing.localId == row.localId) ||
                    (row.sourceLocalActionId != null &&
                        row.sourceLocalActionId != 0L &&
                        existing.sourceLocalActionId == row.sourceLocalActionId) ||
                    (!row.sourceRemoteActionId.isNullOrBlank() &&
                        existing.sourceRemoteActionId == row.sourceRemoteActionId) ||
                    (!row.remoteId.isNullOrBlank() && existing.remoteId == row.remoteId)
            }
            if (index >= 0) events[index] = row else events.add(row)
        }
    }
}
