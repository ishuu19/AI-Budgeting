package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Commitment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommitmentRepositoryTest {

    private class Fixture(
        val people: PeopleRepository,
        val commitments: CommitmentRepository,
        val store: InMemoryCommitmentStore,
    )

    private fun fixture(): Fixture {
        val people = PeopleRepository(InMemoryPersonStore())
        val store = InMemoryCommitmentStore()
        return Fixture(people, CommitmentRepository(people, store), store)
    }

    @Test
    fun commitmentMayOmitPersonId() = runBlocking {
        val fx = fixture()
        val omitted = fx.commitments.addCommitment(
            userId = "u1",
            personId = null,
            eventId = null,
            text = "Send the follow-up",
            dueOn = null,
            status = "open",
        )
        val blankPerson = fx.commitments.addCommitment(
            userId = "u1",
            personId = "  ",
            eventId = "  ",
            text = " Call Friday ",
            dueOn = null,
            status = " open ",
        )

        assertTrue(omitted.isSuccess)
        val row = omitted.getOrThrow()
        assertNull(row.personId)
        assertNull(row.eventId)
        assertEquals("Send the follow-up", row.text)
        assertNull(row.dueOn)
        assertEquals("open", row.status)
        assertNull(row.deletedAt)
        val blank = blankPerson.getOrThrow()
        assertNull(blank.personId)
        assertNull(blank.eventId)
        assertEquals("Call Friday", blank.text)
        assertEquals("open", blank.status)
        assertEquals(
            listOf("Send the follow-up", "Call Friday"),
            fx.commitments.listCommitments("u1").map { it.text },
        )
        assertTrue(fx.people.listPeople("u1").isEmpty())
    }

    @Test
    fun softDeletedCommitmentsAreExcluded() = runBlocking {
        val fx = fixture()
        val ada = fx.people.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        val kept = fx.commitments.addCommitment(
            userId = "u1",
            personId = ada.id,
            eventId = null,
            text = "Send the follow-up",
            dueOn = null,
            status = "open",
        ).getOrThrow()
        val dropped = fx.commitments.addCommitment(
            userId = "u1",
            personId = null,
            eventId = null,
            text = "Call Friday",
            dueOn = LocalDate.of(2026, 12, 1),
            status = "open",
        ).getOrThrow()
        fx.commitments.deleteCommitment(userId = "u2", commitmentId = kept.id)
        fx.commitments.deleteCommitment(userId = "u1", commitmentId = dropped.id)
        fx.store.upsert(
            Commitment(
                id = "already-gone",
                userId = "u1",
                personId = ada.id,
                eventId = null,
                text = "Old promise",
                dueOn = LocalDate.of(2026, 1, 2),
                status = "done",
                updatedAt = 1L,
                deletedAt = 9L,
            ),
        )

        assertNull(fx.store.all().single { it.id == kept.id }.deletedAt)
        assertTrue(fx.store.all().single { it.id == dropped.id }.deletedAt != null)
        assertEquals(LocalDate.of(2026, 12, 1), fx.store.all().single { it.id == dropped.id }.dueOn)
        assertEquals(listOf("Send the follow-up"), fx.commitments.listCommitments("u1").map { it.text })
        assertEquals(listOf(kept.id), fx.commitments.listCommitments("u1", ada.id).map { it.id })
        assertNull(fx.commitments.listCommitments("u1").single().dueOn)
    }

    @Test
    fun nothingInventsADueDate() = runBlocking {
        val fx = fixture()
        val rejected = fx.commitments.addCommitment(
            userId = "u1",
            personId = null,
            eventId = null,
            text = " ",
            dueOn = null,
            status = "open",
        )
        assertTrue(rejected.isFailure)
        assertEquals("Commitment text is required", rejected.exceptionOrNull()?.message)
        assertTrue(fx.store.all().isEmpty())

        val day = LocalDate.of(2026, 12, 1)
        val undated = fx.commitments.addCommitment(
            userId = "u1",
            personId = null,
            eventId = null,
            text = "Send the follow-up",
            dueOn = null,
            status = "open",
        ).getOrThrow()
        val dated = fx.commitments.addCommitment(
            userId = "u1",
            personId = null,
            eventId = null,
            text = "Call Friday",
            dueOn = day,
            status = "open",
        ).getOrThrow()

        assertNull(undated.dueOn)
        assertEquals(day, dated.dueOn)
        assertEquals(listOf(null, day), fx.store.all().map { it.dueOn })
        assertEquals(listOf(null, day), fx.commitments.listCommitments("u1").map { it.dueOn })
    }
}
