package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Interaction
import java.time.LocalDate
import java.util.UUID

interface InteractionStore {
    suspend fun upsert(interaction: Interaction)
    suspend fun all(): List<Interaction>
}

class InMemoryInteractionStore : InteractionStore {
    private val rows = mutableListOf<Interaction>()

    override suspend fun upsert(interaction: Interaction) {
        val index = rows.indexOfFirst { it.id == interaction.id }
        if (index >= 0) rows[index] = interaction else rows.add(interaction)
    }

    override suspend fun all(): List<Interaction> = rows.toList()
}

class InteractionRepository(
    private val people: PeopleRepository,
    private val store: InteractionStore,
) {
    suspend fun addInteraction(
        userId: String,
        personId: String?,
        occurredOn: LocalDate,
        where: String?,
        summary: String,
        sourceId: String?,
    ): Result<Interaction> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User is required"))
        }
        val linkedId = personId?.trim().orEmpty()
        if (linkedId.isEmpty()) {
            return Result.failure(IllegalArgumentException("An interaction must point at a person"))
        }
        val cleaned = summary.trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Summary is required"))
        }
        if (people.findActive(userId, linkedId) == null) {
            return Result.failure(IllegalArgumentException("Person is not active"))
        }
        val now = System.currentTimeMillis()
        val interaction = Interaction(
            id = UUID.randomUUID().toString(),
            userId = userId,
            personId = linkedId,
            occurredOn = occurredOn,
            where = where?.trim()?.ifBlank { null },
            summary = cleaned,
            sourceId = sourceId?.trim()?.ifBlank { null },
            updatedAt = now,
            deletedAt = null,
        )
        store.upsert(interaction)
        return Result.success(interaction)
    }

    suspend fun listInteractions(userId: String, personId: String): List<Interaction> {
        if (people.findActive(userId, personId) == null) return emptyList()
        return store.all().filter {
            it.userId == userId && it.personId == personId && it.deletedAt == null
        }
    }

    suspend fun deleteInteraction(userId: String, interactionId: String) {
        val existing = store.all().find {
            it.id == interactionId && it.userId == userId && it.deletedAt == null
        } ?: return
        val now = System.currentTimeMillis()
        store.upsert(existing.copy(deletedAt = now, updatedAt = now))
    }
}

class RoomInteractionStore(private val dao: InteractionDao) : InteractionStore {
    override suspend fun upsert(interaction: Interaction) {
        dao.upsert(interaction.toEntity())
    }

    override suspend fun all(): List<Interaction> = dao.listAll().map { it.toDomain() }
}
