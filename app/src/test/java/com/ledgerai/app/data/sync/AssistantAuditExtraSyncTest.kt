package com.ledgerai.app.data.sync

import com.ledgerai.app.domain.assistant.ActionRemotePayload
import com.ledgerai.app.domain.assistant.ActionSyncRecord
import com.ledgerai.app.domain.assistant.AuditSyncSession
import com.ledgerai.app.domain.assistant.AuditSyncStore
import com.ledgerai.app.domain.assistant.EventRemotePayload
import com.ledgerai.app.domain.assistant.EventSyncRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAuditExtraSyncTest {

    @Test
    fun pullThenPush_actionsBeforeEvents() = runBlocking {
        val remote = FakeAuditRemote()
        val store = MemoryAuditStore(
            actions = listOf(localAction(remoteId = null)),
            events = listOf(localEvent(sourceRemoteActionId = null)),
        )
        val sync = handler(store, remote, userId = "user-1", since = 0L)

        sync.sync(
            bearer = "bearer",
            apiKey = "key",
            userId = "other-user",
            sinceMs = 9_000_000_000L,
            filter = "updated_at=gt.0",
        )

        assertEquals(
            listOf("pullActions", "pullEvents", "upsertActions", "upsertEvents"),
            remote.calls,
        )
        assertEquals(listOf("user-1", "user-1"), remote.pulledUserIds)
        val pushedActionId = remote.upsertedActions.single().id
        if (!pushedActionId.isNullOrBlank()) {
            assertEquals(pushedActionId, remote.upsertedEvents.single().sourceActionId)
        }
    }

    @Test
    fun emptyRemoteAndEmptyLocal_makesNoUpsertCalls() = runBlocking {
        val remote = FakeAuditRemote()
        val store = MemoryAuditStore()
        val sync = handler(store, remote, userId = "user-1")

        sync.sync(bearer = "", apiKey = "", userId = "user-1", sinceMs = 0L, filter = "")

        assertEquals(listOf("pullActions", "pullEvents"), remote.calls)
        assertTrue(store.calls.isEmpty())
        assertTrue(remote.upsertedActions.isEmpty())
        assertTrue(remote.upsertedEvents.isEmpty())
    }

    @Test
    fun blankUserId_stillPullsAndDoesNotPush() = runBlocking {
        val remote = FakeAuditRemote(
            actions = listOf(remoteAction(id = "remote-action")),
            events = listOf(remoteEvent(id = "remote-event", sourceActionId = "remote-action")),
        )
        val store = MemoryAuditStore(
            actions = listOf(localAction(status = "applied")),
            events = listOf(localEvent(sourceRemoteActionId = "remote-action")),
        )
        val sync = handler(store, remote, userId = "  ")

        sync.sync(
            bearer = "bearer",
            apiKey = "key",
            userId = "user-1",
            sinceMs = 0L,
            filter = "",
        )

        assertEquals(listOf("pullActions", "pullEvents"), remote.calls)
        assertEquals(listOf("  ", "  "), remote.pulledUserIds)
        assertTrue(remote.upsertedActions.isEmpty())
        assertTrue(remote.upsertedEvents.isEmpty())
    }

    private fun handler(
        store: AuditSyncStore,
        remote: AuditRemote,
        userId: String,
        since: Long = 0L,
    ): AssistantAuditExtraSync {
        var next = 0
        return AssistantAuditExtraSync(
            store = store,
            remote = remote,
            session = AuditSyncSession(),
            newRemoteId = { "generated-${++next}" },
            signedInUserId = { userId },
            sinceMs = { since },
        )
    }

    private fun localAction(
        remoteId: String? = "local-action-remote",
        status: String = "proposed",
    ) = ActionSyncRecord(
        localId = 1L,
        remoteId = remoteId,
        userId = "user-1",
        inputRef = "voice:1",
        actionIndex = 0,
        planJson = """{"amount":"12.50"}""",
        status = status,
        risk = "low",
        appliedAt = if (status == "applied") 1_700_000_000_000L else null,
        error = "",
        updatedAt = 5_000L,
        deletedAt = null,
    )

    private fun localEvent(sourceRemoteActionId: String?) = EventSyncRecord(
        localId = 2L,
        remoteId = null,
        userId = "user-1",
        sourceLocalActionId = 1L,
        sourceRemoteActionId = sourceRemoteActionId,
        payload = """{"amount":"12.50"}""",
        updatedAt = 5_000L,
        deletedAt = null,
    )

    private fun remoteAction(id: String) = ActionRemotePayload(
        id = id,
        userId = "user-1",
        inputRef = "voice:1",
        actionIndex = 0,
        planJson = """{"amount":"12.50"}""",
        status = "proposed",
        risk = "low",
        appliedAt = null,
        error = "",
        updatedAt = "2026-01-01T00:00:00Z",
        deletedAt = null,
    )

    private fun remoteEvent(id: String, sourceActionId: String) = EventRemotePayload(
        id = id,
        userId = "user-1",
        sourceActionId = sourceActionId,
        payload = """{"amount":"12.50"}""",
        updatedAt = "2026-01-01T00:00:00Z",
        deletedAt = null,
    )
}

private class FakeAuditRemote(
    private val actions: List<ActionRemotePayload> = emptyList(),
    private val events: List<EventRemotePayload> = emptyList(),
) : AuditRemote {
    val calls = mutableListOf<String>()
    val pulledUserIds = mutableListOf<String>()
    var upsertedActions: List<ActionRemotePayload> = emptyList()
    var upsertedEvents: List<EventRemotePayload> = emptyList()

    override suspend fun pullActions(userId: String): List<ActionRemotePayload> {
        calls += "pullActions"
        pulledUserIds += userId
        return actions
    }

    override suspend fun pullEvents(userId: String): List<EventRemotePayload> {
        calls += "pullEvents"
        pulledUserIds += userId
        return events
    }

    override suspend fun upsertActions(rows: List<ActionRemotePayload>) {
        calls += "upsertActions"
        upsertedActions = rows
    }

    override suspend fun upsertEvents(rows: List<EventRemotePayload>) {
        calls += "upsertEvents"
        upsertedEvents = rows
    }
}

private class MemoryAuditStore(
    actions: List<ActionSyncRecord> = emptyList(),
    events: List<EventSyncRecord> = emptyList(),
) : AuditSyncStore {
    private val actions = actions.toMutableList()
    private val events = events.toMutableList()
    val calls = mutableListOf<String>()

    override fun loadActions(): List<ActionSyncRecord> = actions.toList()

    override fun loadEvents(): List<EventSyncRecord> = events.toList()

    override fun findActionByRemoteId(remoteId: String): ActionSyncRecord? =
        actions.find { it.remoteId == remoteId }

    override fun findAction(inputRef: String, actionIndex: Int): ActionSyncRecord? =
        actions.find { it.inputRef == inputRef && it.actionIndex == actionIndex }

    override fun findEventBySourceAction(
        sourceLocalActionId: Long?,
        sourceRemoteActionId: String?,
    ): EventSyncRecord? {
        if (sourceLocalActionId != null && sourceLocalActionId != 0L) {
            events.find { it.sourceLocalActionId == sourceLocalActionId }?.let { return it }
        }
        if (!sourceRemoteActionId.isNullOrBlank()) {
            return events.find { it.sourceRemoteActionId == sourceRemoteActionId }
        }
        return null
    }

    override fun saveActions(rows: List<ActionSyncRecord>) {
        calls += "saveActions"
        rows.forEach { row ->
            val index = actions.indexOfFirst { it.localId == row.localId && row.localId != 0L }
            if (index >= 0) actions[index] = row else actions += row
        }
    }

    override fun saveEvents(rows: List<EventSyncRecord>) {
        calls += "saveEvents"
        rows.forEach { row ->
            val index = events.indexOfFirst { it.localId == row.localId && row.localId != 0L }
            if (index >= 0) events[index] = row else events += row
        }
    }
}
