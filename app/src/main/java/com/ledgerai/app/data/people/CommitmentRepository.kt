package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Commitment
import java.time.LocalDate
import java.util.UUID

interface CommitmentStore {
    suspend fun upsert(commitment: Commitment)
    suspend fun all(): List<Commitment>
}

class InMemoryCommitmentStore : CommitmentStore {
    private val rows = mutableListOf<Commitment>()

    override suspend fun upsert(commitment: Commitment) {
        val index = rows.indexOfFirst { it.id == commitment.id }
        if (index >= 0) rows[index] = commitment else rows.add(commitment)
    }

    override suspend fun all(): List<Commitment> = rows.toList()
}

class CommitmentRepository(
    private val people: PeopleRepository,
    private val store: CommitmentStore,
) {
    suspend fun addCommitment(
        userId: String,
        personId: String?,
        eventId: String?,
        text: String,
        dueOn: LocalDate?,
        status: String,
    ): Result<Commitment> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User is required"))
        }
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Commitment text is required"))
        }
        val cleanedStatus = status.trim()
        if (cleanedStatus.isEmpty()) {
            return Result.failure(IllegalArgumentException("Status is required"))
        }
        val linkedId = personId?.trim()?.ifBlank { null }
        if (linkedId != null && people.findActive(userId, linkedId) == null) {
            return Result.failure(IllegalArgumentException("Person is not active"))
        }
        val now = System.currentTimeMillis()
        val commitment = Commitment(
            id = UUID.randomUUID().toString(),
            userId = userId,
            personId = linkedId,
            eventId = eventId?.trim()?.ifBlank { null },
            text = cleaned,
            dueOn = dueOn,
            status = cleanedStatus,
            updatedAt = now,
            deletedAt = null,
        )
        store.upsert(commitment)
        return Result.success(commitment)
    }

    suspend fun listCommitments(userId: String): List<Commitment> {
        val activePeople = people.listPeople(userId).map { it.id }.toSet()
        return store.all().filter { row ->
            row.userId == userId &&
                row.deletedAt == null &&
                (row.personId == null || row.personId in activePeople)
        }
    }

    suspend fun listCommitments(userId: String, personId: String): List<Commitment> {
        if (people.findActive(userId, personId) == null) return emptyList()
        return store.all().filter {
            it.userId == userId && it.personId == personId && it.deletedAt == null
        }
    }

    suspend fun deleteCommitment(userId: String, commitmentId: String) {
        val existing = store.all().find {
            it.id == commitmentId && it.userId == userId && it.deletedAt == null
        } ?: return
        val now = System.currentTimeMillis()
        store.upsert(existing.copy(deletedAt = now, updatedAt = now))
    }
}

class RoomCommitmentStore(private val dao: CommitmentDao) : CommitmentStore {
    override suspend fun upsert(commitment: Commitment) {
        dao.upsert(commitment.toEntity())
    }

    override suspend fun all(): List<Commitment> = dao.listAll().map { it.toDomain() }
}
