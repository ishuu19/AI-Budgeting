package com.ledgerai.app.data.sync

import com.ledgerai.app.domain.assistant.ActionRemotePayload
import com.ledgerai.app.domain.assistant.ActionSyncRecord
import com.ledgerai.app.domain.assistant.AuditSyncMapper
import com.ledgerai.app.domain.assistant.AuditSyncSession
import com.ledgerai.app.domain.assistant.AuditSyncStore
import com.ledgerai.app.domain.assistant.EventRemotePayload
import com.ledgerai.app.domain.assistant.EventSyncRecord

interface AuditRemote {
    suspend fun pullActions(userId: String): List<ActionRemotePayload>
    suspend fun pullEvents(userId: String): List<EventRemotePayload>
    suspend fun upsertActions(rows: List<ActionRemotePayload>)
    suspend fun upsertEvents(rows: List<EventRemotePayload>)
}

/**
 * Pulls assistant audit rows, lets [AuditSyncSession] decide the merge, then pushes.
 * A blank signed-in user still pulls and does not push.
 */
class AssistantAuditExtraSync(
    private val store: AuditSyncStore,
    private val remote: AuditRemote,
    private val session: AuditSyncSession,
    private val newRemoteId: () -> String,
    private val signedInUserId: () -> String,
    private val sinceMs: () -> Long,
) : ExtraSync {

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String,
    ) {
        val signedIn = signedInUserId()
        val remoteActions = remote.pullActions(signedIn)
        val remoteEvents = remote.pullEvents(signedIn)
        val plan = session.plan(
            store.loadActions(),
            store.loadEvents(),
            remoteActions,
            remoteEvents,
            signedIn,
            this.sinceMs(),
            newRemoteId,
        )
        if (plan.actionUpserts.isNotEmpty()) {
            store.saveActions(plan.actionUpserts)
        }
        if (plan.eventUpserts.isNotEmpty()) {
            store.saveEvents(plan.eventUpserts)
        }
        if (signedIn.isBlank()) return
        if (plan.actionsToPush.isNotEmpty()) {
            remote.upsertActions(
                plan.actionsToPush.map { action ->
                    AuditSyncMapper.actionToRemote(action, pushUserId = signedIn)
                },
            )
        }
        if (plan.eventsToPush.isNotEmpty()) {
            remote.upsertEvents(
                plan.eventsToPush.map { event ->
                    AuditSyncMapper.eventToRemote(
                        event,
                        pushUserId = signedIn,
                        parentRemoteId = parentRemoteId(event, plan.actionsToPush),
                    )
                },
            )
        }
    }

    private fun parentRemoteId(
        event: EventSyncRecord,
        pushedActions: List<ActionSyncRecord>,
    ): String? {
        val localId = event.sourceLocalActionId
        val seen = if (localId != null && localId != 0L) {
            pushedActions.firstOrNull { it.localId == localId }?.remoteId
        } else {
            null
        }
        if (!seen.isNullOrBlank()) return seen
        val existing = event.sourceRemoteActionId
        if (!existing.isNullOrBlank()) return existing
        return null
    }
}
