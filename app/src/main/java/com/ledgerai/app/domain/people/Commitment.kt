package com.ledgerai.app.domain.people

import java.time.LocalDate

/**
 * `commitments` from data-model.md
 * (id, person_id?, event_id?, text, due_on?, status), plus soft-delete timestamps.
 *
 * [userId] is the owner. The sketch has no user column. [personId] stays optional.
 * When set, it points at the same person identity as memories. [status] is stored
 * as given: the data model names the column and does not list values.
 * There is no visibility column on this type.
 */
data class Commitment(
    val id: String,
    val userId: String,
    val personId: String?,
    val eventId: String?,
    val text: String,
    val dueOn: LocalDate?,
    val status: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
