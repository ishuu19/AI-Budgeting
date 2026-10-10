package com.ledgerai.app.data.memory

import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.domain.memory.Memory
import com.ledgerai.app.domain.memory.MemoryCheck
import com.ledgerai.app.domain.memory.MemoryDraft
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryRules
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.people.RecordVisibility
import java.util.UUID

interface MemoryStore {
    suspend fun upsert(memory: Memory)
    suspend fun all(): List<Memory>
}

class InMemoryMemoryStore : MemoryStore {
    private val rows = mutableListOf<Memory>()

    override suspend fun upsert(memory: Memory) {
        val index = rows.indexOfFirst { it.id == memory.id }
        if (index >= 0) rows[index] = memory else rows.add(memory)
    }

    override suspend fun all(): List<Memory> = rows.toList()
}

class MemoryRepository(
    private val people: PeopleRepository,
    private val store: MemoryStore,
) {
    suspend fun addMemory(
        userId: String,
        personId: String?,
        text: String,
        kind: MemoryKind,
        status: MemoryStatus,
        sourceType: String,
        sourceId: String?,
    ): Result<Memory> {
        val draft = MemoryDraft(
            personId = personId,
            text = text,
            kind = kind,
            status = status,
            sourceType = sourceType,
            sourceId = sourceId,
        )
        val check = MemoryRules.validate(draft)
        if (check is MemoryCheck.Rejected) {
            return Result.failure(IllegalArgumentException(check.reason))
        }
        val linkedId = personId?.trim().orEmpty()
        if (people.findActive(userId, linkedId) == null) {
            return Result.failure(IllegalArgumentException("Person is not active"))
        }
        val now = System.currentTimeMillis()
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            userId = userId,
            personId = linkedId,
            text = text.trim(),
            kind = kind,
            status = status,
            sourceType = sourceType.trim(),
            sourceId = sourceId?.trim()?.ifBlank { null },
            visibility = RecordVisibility.PRIVATE,
            updatedAt = now,
            deletedAt = null,
        )
        store.upsert(memory)
        return Result.success(memory)
    }

    suspend fun listMemories(userId: String, personId: String): List<Memory> {
        if (people.findActive(userId, personId) == null) return emptyList()
        return store.all().filter {
            it.userId == userId && it.personId == personId && it.deletedAt == null
        }
    }

    suspend fun softDelete(userId: String, memoryId: String) {
        val existing = store.all().find {
            it.id == memoryId && it.userId == userId && it.deletedAt == null
        } ?: return
        val now = System.currentTimeMillis()
        store.upsert(existing.copy(deletedAt = now, updatedAt = now))
    }
}

class RoomMemoryStore(private val dao: MemoryDao) : MemoryStore {
    override suspend fun upsert(memory: Memory) {
        dao.upsert(memory.toEntity())
    }

    override suspend fun all(): List<Memory> = dao.listAll().map { it.toDomain() }
}
