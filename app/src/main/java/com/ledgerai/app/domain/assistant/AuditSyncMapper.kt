package com.ledgerai.app.domain.assistant

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ActionSyncRecord(
    val localId: Long,
    val remoteId: String?,
    val userId: String?,
    val inputRef: String,
    val actionIndex: Int,
    val planJson: String,
    val status: String,
    val risk: String,
    val appliedAt: Long?,
    val error: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class EventSyncRecord(
    val localId: Long,
    val remoteId: String?,
    val userId: String?,
    val sourceLocalActionId: Long?,
    val sourceRemoteActionId: String?,
    val payload: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

data class ActionRemotePayload(
    val id: String?,
    val userId: String?,
    val inputRef: String?,
    val actionIndex: Int?,
    val planJson: String?,
    val status: String?,
    val risk: String?,
    val appliedAt: String?,
    val error: String?,
    val updatedAt: String?,
    val deletedAt: String?,
)

data class EventRemotePayload(
    val id: String?,
    val userId: String?,
    val sourceActionId: String?,
    val payload: String?,
    val updatedAt: String?,
    val deletedAt: String?,
)

/**
 * Maps assistant audit rows to and from wire payloads.
 * Blank timestamps become 0 or null. Ids are copied, never generated.
 */
object AuditSyncMapper {

    fun actionToRemote(record: ActionSyncRecord, pushUserId: String?): ActionRemotePayload =
        ActionRemotePayload(
            id = record.remoteId,
            userId = pushUserId,
            inputRef = record.inputRef,
            actionIndex = record.actionIndex,
            planJson = planOrEmpty(record.planJson),
            status = statusOrProposed(record.status),
            risk = riskOrLow(record.risk),
            appliedAt = record.appliedAt?.let(::millisToIso),
            error = errorOrBlank(record.error),
            updatedAt = millisToIso(record.updatedAt),
            deletedAt = record.deletedAt?.let(::millisToIso),
        )

    fun actionFromRemote(payload: ActionRemotePayload, existingLocalId: Long): ActionSyncRecord =
        ActionSyncRecord(
            localId = existingLocalId,
            remoteId = payload.id,
            userId = payload.userId,
            inputRef = payload.inputRef ?: "",
            actionIndex = payload.actionIndex ?: 0,
            planJson = planOrEmpty(payload.planJson),
            status = statusOrProposed(payload.status),
            risk = riskOrLow(payload.risk),
            appliedAt = optionalMillis(payload.appliedAt),
            error = errorOrBlank(payload.error),
            updatedAt = requiredMillis(payload.updatedAt),
            deletedAt = optionalMillis(payload.deletedAt),
        )

    fun eventToRemote(
        record: EventSyncRecord,
        pushUserId: String?,
        parentRemoteId: String?,
    ): EventRemotePayload =
        EventRemotePayload(
            id = record.remoteId,
            userId = pushUserId,
            sourceActionId = parentRemoteId,
            payload = record.payload,
            updatedAt = millisToIso(record.updatedAt),
            deletedAt = record.deletedAt?.let(::millisToIso),
        )

    fun eventFromRemote(
        payload: EventRemotePayload,
        existingLocalId: Long,
        sourceLocalActionId: Long?,
    ): EventSyncRecord =
        EventSyncRecord(
            localId = existingLocalId,
            remoteId = payload.id,
            userId = payload.userId,
            sourceLocalActionId = sourceLocalActionId,
            sourceRemoteActionId = payload.sourceActionId,
            payload = payload.payload ?: "",
            updatedAt = requiredMillis(payload.updatedAt),
            deletedAt = optionalMillis(payload.deletedAt),
        )

    private fun planOrEmpty(value: String?): String =
        if (value.isNullOrBlank()) "{}" else value

    private fun statusOrProposed(value: String?): String =
        if (value.isNullOrBlank()) "proposed" else value

    private fun riskOrLow(value: String?): String =
        if (value.isNullOrBlank()) "low" else value

    private fun errorOrBlank(value: String?): String =
        if (value.isNullOrBlank()) "" else value

    private fun millisToIso(ms: Long): String =
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))

    private fun requiredMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return parseMillis(value) ?: 0L
    }

    private fun optionalMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return parseMillis(value)
    }

    private fun parseMillis(value: String): Long? =
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
}
