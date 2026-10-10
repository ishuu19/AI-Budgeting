package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.domain.people.RecordVisibility
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PeopleRepositoryTest {

    @Test
    fun deletedPeopleAreExcluded() = runBlocking {
        val store = InMemoryPersonStore()
        val repo = PeopleRepository(store)
        store.upsert(
            Person(
                id = "deleted",
                userId = "u1",
                name = "Ada",
                updatedAt = 1L,
                deletedAt = 2L,
            )
        )
        store.upsert(
            Person(
                id = "kept",
                userId = "u1",
                name = "Grace",
                org = "Navy",
                role = "Admiral",
                notes = "met in 1944",
                updatedAt = 3L,
            )
        )

        assertEquals(listOf("Grace"), repo.listPeople("u1").map { it.name })
        assertNull(repo.findActive(userId = "u1", personId = "deleted"))
        assertEquals("Grace", repo.findActive(userId = "u1", personId = "kept")?.name)
    }

    @Test
    fun deletePersonHidesThemFromTheList() = runBlocking {
        val repo = PeopleRepository(InMemoryPersonStore())
        val ada = repo.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        repo.addPerson(userId = "u1", name = "Grace", org = null, role = null, notes = "")
        repo.deletePerson(userId = "u1", personId = ada.id)

        assertEquals(listOf("Grace"), repo.listPeople("u1").map { it.name })
        assertNull(repo.findActive(userId = "u1", personId = ada.id))
    }

    @Test
    fun listPeopleIsScopedToTheUser() = runBlocking {
        val repo = PeopleRepository(InMemoryPersonStore())
        repo.addPerson(userId = "u1", name = "Ada", org = null, role = null, notes = "")
        repo.addPerson(userId = "u2", name = "Grace", org = null, role = null, notes = "")

        assertEquals(listOf("Ada"), repo.listPeople("u1").map { it.name })
    }

    @Test
    fun addedPersonStaysPrivate() = runBlocking {
        val repo = PeopleRepository(InMemoryPersonStore())
        val person = repo.addPerson(
            userId = "u1",
            name = "Ada",
            org = "Analytical",
            role = "Mathematician",
            notes = "first program",
        )

        assertEquals(RecordVisibility.PRIVATE, person.visibility)
        assertEquals("Analytical", person.org)
        assertEquals("Mathematician", person.role)
        assertEquals("first program", person.notes)
        assertEquals("u1", person.userId)
        assertNull(person.deletedAt)
    }

    @Test
    fun blankNameIsRejectedAndNothingIsStored() = runBlocking {
        val store = InMemoryPersonStore()
        val repo = PeopleRepository(store)
        try {
            repo.addPerson(userId = "u1", name = "  ", org = "Lab", role = "Lead", notes = "note")
            fail("blank name was stored")
        } catch (error: IllegalArgumentException) {
            assertEquals("Name is required", error.message)
        }
        assertTrue(store.all().isEmpty())
        assertTrue(repo.listPeople("u1").isEmpty())
    }

    @Test
    fun blankOrgAndRoleStayNull() = runBlocking {
        val repo = PeopleRepository(InMemoryPersonStore())
        val person = repo.addPerson(
            userId = "u1",
            name = " Ada ",
            org = " ",
            role = " ",
            notes = " ",
        )

        assertEquals("Ada", person.name)
        assertNull(person.org)
        assertNull(person.role)
        assertEquals("", person.notes)
        assertEquals(RecordVisibility.PRIVATE, person.visibility)
        assertEquals(listOf("Ada"), repo.listPeople("u1").map { it.name })
    }

    @Test
    fun deletePersonLeavesAnotherUsersRowActive() = runBlocking {
        val store = InMemoryPersonStore()
        val repo = PeopleRepository(store)
        store.upsert(
            Person(
                id = "p-other",
                userId = "u2",
                name = "Grace",
                updatedAt = 1L,
            ),
        )

        repo.deletePerson(userId = "u1", personId = "p-other")

        val stored = store.all().single()
        assertNull(stored.deletedAt)
        assertEquals("Grace", stored.name)
        assertEquals(listOf("Grace"), repo.listPeople("u2").map { it.name })
        assertTrue(repo.listPeople("u1").isEmpty())
    }
}
