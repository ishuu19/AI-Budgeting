package com.ledgerai.app.data.people

import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.domain.people.RecordVisibility
import java.util.UUID

interface PersonStore {
    suspend fun upsert(person: Person)
    suspend fun all(): List<Person>
}

class InMemoryPersonStore : PersonStore {
    private val rows = mutableListOf<Person>()

    override suspend fun upsert(person: Person) {
        val index = rows.indexOfFirst { it.id == person.id }
        if (index >= 0) rows[index] = person else rows.add(person)
    }

    override suspend fun all(): List<Person> = rows.toList()
}

class PeopleRepository(
    private val store: PersonStore,
) {
    suspend fun addPerson(
        userId: String,
        name: String,
        org: String?,
        role: String?,
        notes: String,
    ): Person {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty()) { "Name is required" }
        val now = System.currentTimeMillis()
        val person = Person(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = cleaned,
            org = org?.trim()?.ifBlank { null },
            role = role?.trim()?.ifBlank { null },
            notes = notes.trim(),
            visibility = RecordVisibility.PRIVATE,
            updatedAt = now,
            deletedAt = null,
        )
        store.upsert(person)
        return person
    }

    suspend fun listPeople(userId: String): List<Person> =
        store.all().filter { it.userId == userId && it.deletedAt == null }

    suspend fun findActive(userId: String, personId: String): Person? =
        listPeople(userId).find { it.id == personId }

    suspend fun deletePerson(userId: String, personId: String) {
        val existing = store.all().find {
            it.id == personId && it.userId == userId && it.deletedAt == null
        } ?: return
        val now = System.currentTimeMillis()
        store.upsert(existing.copy(deletedAt = now, updatedAt = now))
    }
}

class RoomPersonStore(private val dao: PersonDao) : PersonStore {
    override suspend fun upsert(person: Person) {
        dao.upsert(person.toEntity())
    }

    override suspend fun all(): List<Person> = dao.listAll().map { it.toDomain() }
}
