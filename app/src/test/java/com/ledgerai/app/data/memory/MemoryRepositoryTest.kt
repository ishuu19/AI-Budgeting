package com.ledgerai.app.data.memory

import com.ledgerai.app.data.people.InMemoryPersonStore
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.domain.memory.Memory
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.people.RecordVisibility
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {

    private class Fixture(
        val people: PeopleRepository,
        val memories: MemoryRepository,
        val store: InMemoryMemoryStore,
    )

    private fun fixture(): Fixture {
        val people = PeopleRepository(InMemoryPersonStore())
        val store = InMemoryMemoryStore()
        return Fixture(people, MemoryRepository(people, store), store)
    }

    @Test
    fun addMemoryWithoutPersonIsRejectedAndStoresNothing() = runBlocking {
        val fx = fixture()
        val missing = fx.memories.addMemory(
            userId = "u1",
            personId = null,
            text = "orphan fact",
            kind = MemoryKind.FACT,
            status = MemoryStatus.INFERRED,
            sourceType = "voice",
            sourceId = "utterance-1",
        )
        val blank = fx.memories.addMemory(
            userId = "u1",
            personId = "  ",
            text = "blank person",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )

        assertTrue(missing.isFailure)
        assertEquals("A memory must point at a person", missing.exceptionOrNull()?.message)
        assertTrue(blank.isFailure)
        assertEquals("A memory must point at a person", blank.exceptionOrNull()?.message)
        assertTrue(fx.store.all().isEmpty())
        assertTrue(fx.people.listPeople("u1").isEmpty())
    }

    @Test
    fun memoryAttachesToAnActivePersonWithTheGivenFields() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val saved = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "  Prefers tea  ",
            kind = MemoryKind.PREFERENCE,
            status = MemoryStatus.INFERRED,
            sourceType = " voice ",
            sourceId = "  ",
        )

        assertTrue(saved.isSuccess)
        val memory = saved.getOrThrow()
        assertEquals(ada.id, memory.personId)
        assertEquals("u1", memory.userId)
        assertEquals("Prefers tea", memory.text)
        assertEquals(MemoryKind.PREFERENCE, memory.kind)
        assertEquals(MemoryStatus.INFERRED, memory.status)
        assertEquals("voice", memory.sourceType)
        assertNull(memory.sourceId)
        assertEquals(RecordVisibility.PRIVATE, memory.visibility)
        assertNull(memory.deletedAt)
        assertEquals(memory, fx.store.all().single())
        assertEquals(listOf(memory.id), fx.memories.listMemories("u1", ada.id).map { it.id })
    }

    @Test
    fun softDeletedMemoriesAndOtherUsersRowsAreExcluded() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val grace = fx.people.addPerson(userId = "u1", name = "Grace", org = null, role = null, notes = "")
        val kept = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "Born in London",
            kind = MemoryKind.FACT,
            status = MemoryStatus.INFERRED,
            sourceType = "voice",
            sourceId = "v1",
        ).getOrThrow()
        val dropped = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "Prefers tea",
            kind = MemoryKind.PREFERENCE,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        ).getOrThrow()
        fx.memories.softDelete(userId = "u2", memoryId = kept.id)
        assertNull(fx.store.all().single { it.id == kept.id }.deletedAt)
        fx.memories.softDelete(userId = "u1", memoryId = dropped.id)
        fx.memories.softDelete(userId = "u1", memoryId = "missing")
        fx.store.upsert(
            storedMemory(
                id = "other-user",
                userId = "u2",
                personId = ada.id,
                text = "Not Ada's note",
            ),
        )
        fx.store.upsert(
            storedMemory(
                id = "already-gone",
                userId = "u1",
                personId = ada.id,
                text = "Old fact",
                deletedAt = 9L,
            ),
        )
        fx.memories.addMemory(
            userId = "u1",
            personId = grace.id,
            text = "Wrote COBOL",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )

        val listed = fx.memories.listMemories("u1", ada.id)
        assertEquals(listOf("Born in London"), listed.map { it.text })
        assertEquals(ada.id, listed.single().personId)
        assertEquals(kept.id, listed.single().id)
        assertEquals(MemoryStatus.INFERRED, listed.single().status)
        assertEquals("voice", listed.single().sourceType)
        assertEquals("v1", listed.single().sourceId)
        val deleted = fx.store.all().single { it.id == dropped.id }
        assertTrue(deleted.deletedAt != null)
        assertTrue(fx.store.all().none { it.id == "missing" })
        assertEquals(listOf("Wrote COBOL"), fx.memories.listMemories("u1", grace.id).map { it.text })
    }

    @Test
    fun rejectedDraftWritesNoMemory() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val blankText = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "   ",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )
        val blankSource = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "A real fact",
            kind = MemoryKind.GOAL,
            status = MemoryStatus.INFERRED,
            sourceType = " ",
            sourceId = "should-not-stick",
        )
        val missingPerson = fx.memories.addMemory(
            userId = "u1",
            personId = "no-such-person",
            text = "Invented about nobody",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )
        val otherUser = fx.memories.addMemory(
            userId = "u2",
            personId = ada.id,
            text = "Borrowed person",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )

        assertEquals("Memory text is required", blankText.exceptionOrNull()?.message)
        assertEquals("Source type is required", blankSource.exceptionOrNull()?.message)
        assertEquals("Person is not active", missingPerson.exceptionOrNull()?.message)
        assertEquals("Person is not active", otherUser.exceptionOrNull()?.message)
        assertTrue(listOf(blankText, blankSource, missingPerson, otherUser).all { it.isFailure })
        assertTrue(fx.store.all().isEmpty())
        assertEquals(listOf("Ada"), fx.people.listPeople("u1").map { it.name })
        assertTrue(fx.memories.listMemories("u1", ada.id).isEmpty())
    }

    @Test
    fun memoryForDeletedPersonIsRejectedAndExistingRowsStayHidden() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val saved = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "Prefers tea",
            kind = MemoryKind.PREFERENCE,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        ).getOrThrow()
        fx.people.deletePerson(userId = "u1", personId = ada.id)
        val result = fx.memories.addMemory(
            userId = "u1",
            personId = ada.id,
            text = "still here",
            kind = MemoryKind.FACT,
            status = MemoryStatus.CONFIRMED,
            sourceType = "manual",
            sourceId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("Person is not active", result.exceptionOrNull()?.message)
        assertEquals(listOf(saved.text), fx.store.all().map { it.text })
        assertNull(fx.store.all().single().deletedAt)
        assertTrue(fx.memories.listMemories("u1", ada.id).isEmpty())
        assertNull(fx.people.findActive(userId = "u1", personId = ada.id))
        assertTrue(fx.people.listPeople("u1").isEmpty())
    }

    private fun storedMemory(
        id: String,
        userId: String,
        personId: String,
        text: String,
        deletedAt: Long? = null,
    ) = Memory(
        id = id,
        userId = userId,
        personId = personId,
        text = text,
        kind = MemoryKind.FACT,
        status = MemoryStatus.CONFIRMED,
        sourceType = "manual",
        sourceId = id,
        visibility = RecordVisibility.PRIVATE,
        updatedAt = 1L,
        deletedAt = deletedAt,
    )
}
