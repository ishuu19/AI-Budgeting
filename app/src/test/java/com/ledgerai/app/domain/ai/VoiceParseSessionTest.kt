package com.ledgerai.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParseSessionTest {

    private fun fallbackObservation() = VoiceParseObservation(
        path = VoiceAnswerPath.FALLBACK,
        latencyMs = null,
        proposedKinds = emptyList(),
        keptKinds = emptyList(),
        wrongKind = false,
    )

    @Test
    fun emptySnapshot_hasZeroSamplesAndNullMedian() {
        val report = VoiceParseSession().snapshot()

        assertEquals(0, report.samples)
        assertNull(report.latencyMedianMs)
    }

    @Test
    fun twoRecords_snapshotShowsTwoSamples() {
        val session = VoiceParseSession()
        session.record(fallbackObservation())
        session.record(fallbackObservation())

        assertEquals(2, session.snapshot().samples)
    }

    @Test
    fun secondSnapshot_stillShowsBothRecords() {
        val session = VoiceParseSession()
        session.record(fallbackObservation())
        session.record(fallbackObservation())

        session.snapshot()

        assertEquals(2, session.snapshot().samples)
    }
}
