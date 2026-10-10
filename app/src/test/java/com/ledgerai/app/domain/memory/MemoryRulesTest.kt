package com.ledgerai.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {

    private val attached = MemoryDraft(
        personId = "person-1",
        text = "Prefers tea",
        kind = MemoryKind.PREFERENCE,
        status = MemoryStatus.CONFIRMED,
        sourceType = "manual",
        sourceId = null,
    )

    @Test
    fun memoryWithoutPersonIsRejected() {
        // 016_people_memory.sql is a stub and data-model.md does not mark person_id nullable.
        assertFalse(MemoryRules.allowsMemoryWithoutPerson)
        val result = MemoryRules.validate(attached.copy(personId = null))
        assertTrue(result is MemoryCheck.Rejected)
        assertEquals(
            "A memory must point at a person",
            (result as MemoryCheck.Rejected).reason,
        )
    }

    @Test
    fun blankPersonIdIsRejected() {
        val result = MemoryRules.validate(attached.copy(personId = "  "))
        assertTrue(result is MemoryCheck.Rejected)
        assertEquals(
            "A memory must point at a person",
            (result as MemoryCheck.Rejected).reason,
        )
    }

    @Test
    fun memoryWithPersonIsAccepted() {
        val result = MemoryRules.validate(attached)
        assertTrue(result is MemoryCheck.Accepted)
    }

    @Test
    fun blankMemoryTextIsRejected() {
        val result = MemoryRules.validate(attached.copy(text = "  "))
        assertTrue(result is MemoryCheck.Rejected)
        assertEquals("Memory text is required", (result as MemoryCheck.Rejected).reason)
    }

    @Test
    fun blankSourceTypeIsRejected() {
        val result = MemoryRules.validate(attached.copy(sourceType = " "))
        assertTrue(result is MemoryCheck.Rejected)
        assertEquals("Source type is required", (result as MemoryCheck.Rejected).reason)
    }

    @Test
    fun acceptedDraftKeepsTheCallerStatus() {
        val inferred = attached.copy(
            kind = MemoryKind.GOAL,
            status = MemoryStatus.INFERRED,
            sourceId = "utterance-1",
        )
        assertTrue(MemoryRules.validate(inferred) is MemoryCheck.Accepted)
        assertEquals("person-1", inferred.personId)
        assertEquals("Prefers tea", inferred.text)
        assertEquals(MemoryKind.GOAL, inferred.kind)
        assertEquals(MemoryStatus.INFERRED, inferred.status)
        assertEquals("manual", inferred.sourceType)
        assertEquals("utterance-1", inferred.sourceId)
    }
}
