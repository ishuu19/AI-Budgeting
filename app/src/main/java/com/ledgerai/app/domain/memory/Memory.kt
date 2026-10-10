package com.ledgerai.app.domain.memory

import com.ledgerai.app.domain.people.RecordVisibility

/**
 * A memory from data-model.md `memories`
 * (id, user_id, text, kind, status, source_type, source_id, visibility),
 * plus [personId] so it points at the shared person, and soft-delete timestamps.
 *
 * The sketch has no person column and does not say a memory may omit one.
 * [personId] is required. See [MemoryRules].
 */
data class Memory(
    val id: String,
    val userId: String,
    val personId: String,
    val text: String,
    val kind: MemoryKind,
    val status: MemoryStatus,
    val sourceType: String,
    val sourceId: String?,
    val visibility: RecordVisibility = RecordVisibility.PRIVATE,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** fact | preference | goal */
enum class MemoryKind {
    FACT,
    PREFERENCE,
    GOAL,
}

/** confirmed | inferred */
enum class MemoryStatus {
    CONFIRMED,
    INFERRED,
}

data class MemoryDraft(
    val personId: String?,
    val text: String,
    val kind: MemoryKind,
    val status: MemoryStatus,
    val sourceType: String,
    val sourceId: String?,
)
