package com.ledgerai.app.domain.people

import java.time.LocalDate

/**
 * `interactions` from data-model.md
 * (id, person_id, occurred_on, where?, summary, source_id), plus soft-delete timestamps.
 *
 * [userId] is the owner. The sketch has no user column; the row is still user-scoped
 * through the person it points at. There is no visibility column on this type.
 */
data class Interaction(
    val id: String,
    val userId: String,
    val personId: String,
    val occurredOn: LocalDate,
    val where: String? = null,
    val summary: String,
    val sourceId: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
