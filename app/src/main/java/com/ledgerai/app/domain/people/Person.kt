package com.ledgerai.app.domain.people

/**
 * Shared person identity from data-model.md `people`
 * (id, user_id, name, org?, role?, notes, visibility), plus soft-delete timestamps.
 * Private by default. There is no household-share control on this type.
 */
data class Person(
    val id: String,
    val userId: String,
    val name: String,
    val org: String? = null,
    val role: String? = null,
    val notes: String = "",
    val visibility: RecordVisibility = RecordVisibility.PRIVATE,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** data-model.md memory visibility tokens: private | household. People use the same field. */
enum class RecordVisibility {
    PRIVATE,
    HOUSEHOLD,
}
