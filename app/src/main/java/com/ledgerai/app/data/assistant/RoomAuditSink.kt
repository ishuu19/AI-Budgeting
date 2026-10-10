package com.ledgerai.app.data.assistant

import com.google.gson.Gson
import com.ledgerai.app.data.local.room.AssistantActionEntity
import com.ledgerai.app.data.local.room.AssistantAuditDao
import com.ledgerai.app.data.local.room.EventLogEntity
import com.ledgerai.app.domain.assistant.Risk

/**
 * Room [AuditSink]. A row is written for every executor result.
 * [event_log] is written only after a result is [ActionStatus.APPLIED], and only once per action row.
 */
class RoomAuditSink(
    private val dao: AssistantAuditDao,
    private val gson: Gson,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AuditSink {

    override suspend fun find(inputRef: String, index: Int): ActionResult? =
        dao.findAction(inputRef, index)?.toResult()

    override suspend fun record(inputRef: String, result: ActionResult) {
        val now = nowMs()
        val existing = dao.findAction(inputRef, result.index)
        val failed = result.status == ActionStatus.FAILED || result.status == ActionStatus.REJECTED
        val entity = AssistantActionEntity(
            id = existing?.id ?: 0,
            remoteId = existing?.remoteId,
            userId = existing?.userId,
            inputRef = inputRef,
            actionIndex = result.index,
            planJson = gson.toJson(StoredPlan(result.type, result.message)),
            risk = result.risk?.name?.lowercase().orEmpty(),
            status = result.status.toSql(),
            error = if (failed) result.message.take(MAX_ERROR) else "",
            appliedAt = if (result.status == ActionStatus.APPLIED) existing?.appliedAt ?: now else null,
            updatedAt = now,
        )
        val id = if (existing == null) {
            dao.insertAction(entity)
        } else {
            dao.updateAction(entity)
            existing.id
        }
        if (result.status == ActionStatus.APPLIED && dao.findEventByAction(id) == null) {
            dao.insertEvent(
                EventLogEntity(
                    type = result.type,
                    payload = gson.toJson(
                        mapOf(
                            "message" to result.message,
                            "input_ref" to inputRef,
                            "index" to result.index,
                        )
                    ),
                    sourceActionId = id,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    private fun AssistantActionEntity.toResult(): ActionResult {
        val stored = runCatching { gson.fromJson(planJson, StoredPlan::class.java) }.getOrNull()
        return ActionResult(
            index = actionIndex,
            type = stored?.type.orEmpty(),
            status = status.toActionStatus(),
            risk = risk.toRiskOrNull(),
            message = stored?.message?.takeIf { it.isNotEmpty() } ?: error,
        )
    }

    private data class StoredPlan(val type: String? = null, val message: String? = null)

    private fun ActionStatus.toSql(): String = when (this) {
        ActionStatus.APPLIED -> "applied"
        ActionStatus.NEEDS_APPROVAL -> "proposed"
        ActionStatus.REJECTED -> "rejected"
        ActionStatus.FAILED -> "failed"
    }

    private fun String.toActionStatus(): ActionStatus = when (this) {
        "applied" -> ActionStatus.APPLIED
        "rejected" -> ActionStatus.REJECTED
        "failed" -> ActionStatus.FAILED
        else -> ActionStatus.NEEDS_APPROVAL
    }

    private fun String.toRiskOrNull(): Risk? =
        Risk.entries.find { it.name.equals(this, ignoreCase = true) }

    private companion object {
        const val MAX_ERROR = 2000
    }
}
