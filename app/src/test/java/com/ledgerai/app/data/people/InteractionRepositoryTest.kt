package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Interaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InteractionRepositoryTest {

    private class Fixture(
        val people: PeopleRepository,
        val interactions: InteractionRepository,
        val store: InMemoryInteractionStore,
    )

    private fun fixture(): Fixture {
        val people = PeopleRepository(InMemoryPersonStore())
        val store = InMemoryInteractionStore()
        return Fixture(people, InteractionRepository(people, store), store)
    }

    @Test
    fun interactionWithoutPersonIsRejectedAndStoresNothing() = runBlocking {
        val fx = fixture()
        val day = LocalDate.of(2026, 10, 11)
        val missing = fx.interactions.addInteraction(
            userId = "u1",
            personId = null,
            occurredOn = day,
            where = "cafe",
            summary = "Talked about the role",
            sourceId = "voice-1",
        )
        val blank = fx.interactions.addInteraction(
            userId = "u1",
            personId = "  ",
            occurredOn = day,
            where = null,
            summary = "Talked about the role",
            sourceId = "voice-1",
        )

        assertTrue(missing.isFailure)
        assertEquals("An interaction must point at a person", missing.exceptionOrNull()?.message)
        assertTrue(blank.isFailure)
        assertEquals("An interaction must point at a person", blank.exceptionOrNull()?.message)
        assertTrue(fx.store.all().isEmpty())
        assertTrue(fx.people.listPeople("u1").isEmpty())
    }

    @Test
    fun interactionForActivePersonStoresSummaryAndSource() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val day = LocalDate.of(2026, 10, 11)
        val saved = fx.interactions.addInteraction(
            userId = "u1",
            personId = ada.id,
            occurredOn = day,
            where = null,
            summary = "Talked about the role",
            sourceId = "voice-1",
        )
        val noSource = fx.interactions.addInteraction(
            userId = "u1",
            personId = ada.id,
            occurredOn = day,
            where = "  ",
            summary = "Short hello",
            sourceId = null,
        )

        assertTrue(saved.isSuccess)
        val row = saved.getOrThrow()
        assertEquals(ada.id, row.personId)
        assertEquals("u1", row.userId)
        assertEquals(day, row.occurredOn)
        assertNull(row.where)
        assertEquals("Talked about the role", row.summary)
        assertEquals("voice-1", row.sourceId)
        assertNull(row.deletedAt)
        val unsourced = noSource.getOrThrow()
        assertEquals("Short hello", unsourced.summary)
        assertNull(unsourced.sourceId)
        assertNull(unsourced.where)
        assertEquals(
            listOf("Talked about the role", "Short hello"),
            fx.interactions.listInteractions("u1", ada.id).map { it.summary },
        )
        assertEquals(
            listOf("voice-1", null),
            fx.interactions.listInteractions("u1", ada.id).map { it.sourceId },
        )
    }

    @Test
    fun blankSummaryIsRejectedAndStoresNothing() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val result = fx.interactions.addInteraction(
            userId = "u1",
            personId = ada.id,
            occurredOn = LocalDate.of(2026, 10, 11),
            where = "cafe",
            summary = "   ",
            sourceId = "voice-1",
        )

        assertTrue(result.isFailure)
        assertEquals("Summary is required", result.exceptionOrNull()?.message)
        assertTrue(fx.store.all().isEmpty())
        assertTrue(fx.interactions.listInteractions("u1", ada.id).isEmpty())
        assertEquals(listOf("Ada"), fx.people.listPeople("u1").map { it.name })
    }

    @Test
    fun softDeletedInteractionsAreExcluded() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val day = LocalDate.of(2026, 10, 11)
        val kept = fx.interactions.addInteraction(
            userId = "u1",
            personId = ada.id,
            occurredOn = day,
            where = "cafe",
            summary = "Talked about the role",
            sourceId = "voice-1",
        ).getOrThrow()
        val dropped = fx.interactions.addInteraction(
            userId = "u1",
            personId = ada.id,
            occurredOn = day,
            where = null,
            summary = "Short hello",
            sourceId = "voice-2",
        ).getOrThrow()
        fx.interactions.deleteInteraction(userId = "u2", interactionId = kept.id)
        fx.interactions.deleteInteraction(userId = "u1", interactionId = dropped.id)
        fx.store.upsert(
            Interaction(
                id = "already-gone",
                userId = "u1",
                personId = ada.id,
                occurredOn = day,
                summary = "Old hello",
                sourceId = "old",
                updatedAt = 1L,
                deletedAt = 9L,
            ),
        )

        assertNull(fx.store.all().single { it.id == kept.id }.deletedAt)
        assertTrue(fx.store.all().single { it.id == dropped.id }.deletedAt != null)
        assertEquals("Short hello", fx.store.all().single { it.id == dropped.id }.summary)
        val listed = fx.interactions.listInteractions("u1", ada.id)
        assertEquals(listOf(kept.id), listed.map { it.id })
        assertEquals("Talked about the role", listed.single().summary)
        assertEquals("voice-1", listed.single().sourceId)
    }
}
