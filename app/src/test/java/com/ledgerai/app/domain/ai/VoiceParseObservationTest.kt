package com.ledgerai.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParseObservationTest {

    @Test
    fun skipNotAttempted_forcesFallbackAndNullLatency() {
        val observation = observeVoiceParse(
            attempted = false,
            latencyMs = 1200L,
            proposedKinds = listOf("Spend"),
            keptKinds = listOf("Bill"),
        )

        assertEquals(VoiceAnswerPath.FALLBACK, observation.path)
        assertNull(observation.latencyMs)
        assertEquals(emptyList<String>(), observation.proposedKinds)
        assertEquals(listOf("Bill"), observation.keptKinds)
        assertFalse(observation.wrongKind)
    }

    @Test
    fun timeoutEmptyProposal_isFallbackNotWrongKindWithLatency() {
        val observation = observeVoiceParse(
            attempted = true,
            latencyMs = 6000L,
            proposedKinds = emptyList(),
            keptKinds = emptyList(),
        )

        assertEquals(VoiceAnswerPath.FALLBACK, observation.path)
        assertFalse(observation.wrongKind)
        assertEquals(6000L, observation.latencyMs)
        assertEquals(emptyList<String>(), observation.proposedKinds)
    }

    @Test
    fun proposedSpendKeptSpend_isFastNotWrongKind() {
        val observation = observeVoiceParse(
            attempted = true,
            latencyMs = 400L,
            proposedKinds = listOf("Spend"),
            keptKinds = listOf("Spend"),
        )

        assertEquals(VoiceAnswerPath.FAST, observation.path)
        assertFalse(observation.wrongKind)
        assertEquals(listOf("Spend"), observation.proposedKinds)
        assertEquals(listOf("Spend"), observation.keptKinds)
    }

    @Test
    fun proposedSpendNoteKeptBill_isFastWrongKind() {
        val observation = observeVoiceParse(
            attempted = true,
            latencyMs = 400L,
            proposedKinds = listOf("Spend", "Note"),
            keptKinds = listOf("Bill"),
        )

        assertEquals(VoiceAnswerPath.FAST, observation.path)
        assertTrue(observation.wrongKind)
        assertEquals(listOf("Spend", "Note"), observation.proposedKinds)
        assertEquals(listOf("Bill"), observation.keptKinds)
    }

    @Test
    fun bothEmptyAndNotAttempted_doesNotInventAKind() {
        val observation = observeVoiceParse(
            attempted = false,
            latencyMs = null,
            proposedKinds = emptyList(),
            keptKinds = emptyList(),
        )

        assertEquals(emptyList<String>(), observation.keptKinds)
        assertEquals(emptyList<String>(), observation.proposedKinds)
        assertEquals(VoiceAnswerPath.FALLBACK, observation.path)
        assertFalse(observation.wrongKind)
        assertNull(observation.latencyMs)
    }
}
