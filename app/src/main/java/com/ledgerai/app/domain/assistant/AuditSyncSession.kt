package com.ledgerai.app.domain.assistant

data class AuditSyncPlan(
    val actionUpserts: List<ActionSyncRecord>,
    val eventUpserts: List<EventSyncRecord>,
    val actionsToPush: List<ActionSyncRecord>,
    val eventsToPush: List<EventSyncRecord>,
)

interface AuditSyncStore {
    fun loadActions(): List<ActionSyncRecord>
    fun loadEvents(): List<EventSyncRecord>
    fun findActionByRemoteId(remoteId: String): ActionSyncRecord?
    fun findAction(inputRef: String, actionIndex: Int): ActionSyncRecord?
    fun findEventBySourceAction(
        sourceLocalActionId: Long?,
        sourceRemoteActionId: String?,
    ): EventSyncRecord?
    fun saveActions(rows: List<ActionSyncRecord>)
    fun saveEvents(rows: List<EventSyncRecord>)
}

/**
 * Plans a pull of assistant audit rows, then which local rows to push.
 * Actions are applied before events. Last write wins, and a tie goes to remote.
 */
class AuditSyncSession {
    fun plan(
        localActions: List<ActionSyncRecord>,
        localEvents: List<EventSyncRecord>,
        remoteActions: List<ActionRemotePayload>,
        remoteEvents: List<EventRemotePayload>,
        signedInUserId: String,
        sinceMs: Long,
        newRemoteId: () -> String,
    ): AuditSyncPlan {
        val actions = localActions.toMutableList()
        val actionUpserts = mutableListOf<ActionSyncRecord>()
        for (remote in remoteActions) {
            applyAction(actions, actionUpserts, remote)
        }

        val events = localEvents.toMutableList()
        val eventUpserts = mutableListOf<EventSyncRecord>()
        for (remote in remoteEvents) {
            applyEvent(actions, events, eventUpserts, remote)
        }

        if (signedInUserId.isBlank()) {
            return AuditSyncPlan(
                actionUpserts = actionUpserts.toList(),
                eventUpserts = eventUpserts.toList(),
                actionsToPush = emptyList(),
                eventsToPush = emptyList(),
            )
        }

        val actionsToPush = pushActions(actions, actionUpserts, signedInUserId, sinceMs, newRemoteId)
        val eventsToPush = pushEvents(actions, events, eventUpserts, signedInUserId, sinceMs, newRemoteId)
        return AuditSyncPlan(
            actionUpserts = actionUpserts.toList(),
            eventUpserts = eventUpserts.toList(),
            actionsToPush = actionsToPush,
            eventsToPush = eventsToPush,
        )
    }

    private fun applyAction(
        actions: MutableList<ActionSyncRecord>,
        upserts: MutableList<ActionSyncRecord>,
        remote: ActionRemotePayload,
    ) {
        val index = findActionIndex(
            actions,
            remote.id,
            remote.inputRef ?: "",
            remote.actionIndex ?: 0,
        )
        val local = index.takeIf { it >= 0 }?.let { actions[it] }
        val merged = mergeAction(local, remote)
        if (index >= 0) {
            if (merged != local) {
                actions[index] = merged
                putAction(upserts, merged)
            }
        } else {
            actions.add(merged)
            putAction(upserts, merged)
        }
    }

    private fun mergeAction(local: ActionSyncRecord?, remote: ActionRemotePayload): ActionSyncRecord {
        val fromRemote = AuditSyncMapper.actionFromRemote(remote, local?.localId ?: 0L)
        if (local == null || fromRemote.updatedAt >= local.updatedAt) return fromRemote
        if (local.remoteId == null) return local.copy(remoteId = fromRemote.remoteId)
        return local
    }

    private fun applyEvent(
        actions: List<ActionSyncRecord>,
        events: MutableList<EventSyncRecord>,
        upserts: MutableList<EventSyncRecord>,
        remote: EventRemotePayload,
    ) {
        val parent = findActionByRemoteId(actions, remote.sourceActionId)
        val sourceLocalActionId = parent?.localId
        val index = findEventIndex(events, sourceLocalActionId, remote.sourceActionId, remote.id)
        val local = index.takeIf { it >= 0 }?.let { events[it] }
        val merged = mergeEvent(local, remote, sourceLocalActionId)
        if (index >= 0) {
            if (merged != local) {
                events[index] = merged
                putEvent(upserts, merged)
            }
        } else {
            events.add(merged)
            putEvent(upserts, merged)
        }
    }

    private fun mergeEvent(
        local: EventSyncRecord?,
        remote: EventRemotePayload,
        sourceLocalActionId: Long?,
    ): EventSyncRecord {
        val fromRemote = AuditSyncMapper.eventFromRemote(
            remote,
            local?.localId ?: 0L,
            sourceLocalActionId,
        )
        if (local == null || fromRemote.updatedAt >= local.updatedAt) return fromRemote
        if (local.remoteId == null) return local.copy(remoteId = fromRemote.remoteId)
        return local
    }

    private fun pushActions(
        actions: MutableList<ActionSyncRecord>,
        upserts: MutableList<ActionSyncRecord>,
        signedInUserId: String,
        sinceMs: Long,
        newRemoteId: () -> String,
    ): List<ActionSyncRecord> {
        val pushed = mutableListOf<ActionSyncRecord>()
        for (index in actions.indices) {
            val row = actions[index]
            if (!needsPush(row.updatedAt, row.remoteId, sinceMs)) continue
            val withId = row.copy(
                remoteId = row.remoteId ?: newRemoteId(),
                userId = signedInUserId,
            )
            actions[index] = withId
            pushed.add(withId)
            if (withId != row) putAction(upserts, withId)
        }
        return pushed
    }

    private fun pushEvents(
        actions: List<ActionSyncRecord>,
        events: MutableList<EventSyncRecord>,
        upserts: MutableList<EventSyncRecord>,
        signedInUserId: String,
        sinceMs: Long,
        newRemoteId: () -> String,
    ): List<EventSyncRecord> {
        val pushed = mutableListOf<EventSyncRecord>()
        for (index in events.indices) {
            val row = events[index]
            if (!needsPush(row.updatedAt, row.remoteId, sinceMs)) continue
            val parent = parentAction(actions, row)
            val stored = row.copy(
                remoteId = row.remoteId ?: newRemoteId(),
                userId = signedInUserId,
                sourceRemoteActionId = parent?.remoteId ?: row.sourceRemoteActionId,
            )
            events[index] = stored
            pushed.add(stored.copy(sourceRemoteActionId = parent?.remoteId))
            if (stored != row) putEvent(upserts, stored)
        }
        return pushed
    }

    private fun needsPush(updatedAt: Long, remoteId: String?, sinceMs: Long): Boolean =
        updatedAt > sinceMs || remoteId == null

    private fun parentAction(actions: List<ActionSyncRecord>, event: EventSyncRecord): ActionSyncRecord? {
        val localId = event.sourceLocalActionId
        if (localId != null && localId != 0L) {
            actions.find { it.localId == localId }?.let { return it }
        }
        return findActionByRemoteId(actions, event.sourceRemoteActionId)
    }

    private fun findActionIndex(
        actions: List<ActionSyncRecord>,
        remoteId: String?,
        inputRef: String,
        actionIndex: Int,
    ): Int {
        if (!remoteId.isNullOrBlank()) {
            val byRemote = actions.indexOfFirst { it.remoteId == remoteId }
            if (byRemote >= 0) return byRemote
        }
        return actions.indexOfFirst { it.inputRef == inputRef && it.actionIndex == actionIndex }
    }

    private fun findActionByRemoteId(actions: List<ActionSyncRecord>, remoteId: String?): ActionSyncRecord? {
        if (remoteId.isNullOrBlank()) return null
        return actions.find { it.remoteId == remoteId }
    }

    private fun findEventIndex(
        events: List<EventSyncRecord>,
        sourceLocalActionId: Long?,
        sourceRemoteActionId: String?,
        remoteId: String?,
    ): Int {
        if (sourceLocalActionId != null && sourceLocalActionId != 0L) {
            val byLocal = events.indexOfFirst { it.sourceLocalActionId == sourceLocalActionId }
            if (byLocal >= 0) return byLocal
        }
        if (!sourceRemoteActionId.isNullOrBlank()) {
            val byParent = events.indexOfFirst { it.sourceRemoteActionId == sourceRemoteActionId }
            if (byParent >= 0) return byParent
        }
        if (!remoteId.isNullOrBlank()) {
            val byRemote = events.indexOfFirst { it.remoteId == remoteId }
            if (byRemote >= 0) return byRemote
        }
        return -1
    }

    private fun putAction(rows: MutableList<ActionSyncRecord>, row: ActionSyncRecord) {
        val index = rows.indexOfFirst { it.inputRef == row.inputRef && it.actionIndex == row.actionIndex }
        if (index >= 0) rows[index] = row else rows.add(row)
    }

    private fun putEvent(rows: MutableList<EventSyncRecord>, row: EventSyncRecord) {
        val index = eventUpsertIndex(rows, row)
        if (index >= 0) rows[index] = row else rows.add(row)
    }

    private fun eventUpsertIndex(rows: List<EventSyncRecord>, row: EventSyncRecord): Int {
        if (row.localId != 0L) {
            val byLocal = rows.indexOfFirst { it.localId == row.localId }
            if (byLocal >= 0) return byLocal
        }
        val sourceLocal = row.sourceLocalActionId
        if (sourceLocal != null && sourceLocal != 0L) {
            val bySource = rows.indexOfFirst { it.sourceLocalActionId == sourceLocal }
            if (bySource >= 0) return bySource
        }
        val sourceRemote = row.sourceRemoteActionId
        if (!sourceRemote.isNullOrBlank()) {
            val byParent = rows.indexOfFirst { it.sourceRemoteActionId == sourceRemote }
            if (byParent >= 0) return byParent
        }
        val remoteId = row.remoteId
        if (!remoteId.isNullOrBlank()) {
            val byRemote = rows.indexOfFirst { it.remoteId == remoteId }
            if (byRemote >= 0) return byRemote
        }
        return -1
    }
}
