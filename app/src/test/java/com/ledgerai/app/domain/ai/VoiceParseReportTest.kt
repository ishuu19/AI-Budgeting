package com.ledgerai.app.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParseReportTest {

    @Test
    fun emptyList_isUnknownNotAMeasuredZero() {
        val report = summarizeVoiceParses(emptyList())

        assertEquals(0, report.samples)
        assertEquals(0, report.fastAnswers)
        assertEquals(0, report.fallbackAnswers)
        assertEquals(0, report.wrongKindAnswers)
        assertNull(report.latencyMedianMs)
        assertNull(report.wrongKindRate())
    }

    @Test
    fun twoFastOneWrongPlusFallbackWrongKind_countsOnlyFastWrong() {
        val report = summarizeVoiceParses(
            listOf(
                observation(VoiceAnswerPath.FAST, wrongKind = false),
                observation(VoiceAnswerPath.FAST, wrongKind = true),
                observation(VoiceAnswerPath.FALLBACK, wrongKind = true),
            ),
        )

        assertEquals(3, report.samples)
        assertEquals(2, report.fastAnswers)
        assertEquals(1, report.fallbackAnswers)
        assertEquals(1, report.wrongKindAnswers)
        assertEquals(0.5, report.wrongKindRate()!!, 0.0)
    }

    @Test
    fun allFallback_wrongKindRateIsNull() {
        val report = summarizeVoiceParses(
            listOf(
                observation(VoiceAnswerPath.FALLBACK, wrongKind = true),
                observation(VoiceAnswerPath.FALLBACK, wrongKind = false),
            ),
        )

        assertEquals(0, report.fastAnswers)
        assertEquals(2, report.fallbackAnswers)
        assertEquals(0, report.wrongKindAnswers)
        assertNull(report.wrongKindRate())
    }

    @Test
    fun latencies100And400_medianIsLowerMiddle() {
        val report = summarizeVoiceParses(
            listOf(
                observation(VoiceAnswerPath.FAST, latencyMs = 400L),
                observation(VoiceAnswerPath.FAST, latencyMs = 100L),
            ),
        )

        assertEquals(100L, report.latencyMedianMs)
    }

    @Test
    fun latencies100200900_medianIsMiddle() {
        val report = summarizeVoiceParses(
            listOf(
                observation(VoiceAnswerPath.FAST, latencyMs = 900L),
                observation(VoiceAnswerPath.FAST, latencyMs = 100L),
                observation(VoiceAnswerPath.FALLBACK, latencyMs = 200L),
            ),
        )

        assertEquals(200L, report.latencyMedianMs)
    }

    @Test
    fun nullLatency_isExcludedFromMedian() {
        val report = summarizeVoiceParses(
            listOf(
                observation(VoiceAnswerPath.FAST, latencyMs = null),
                observation(VoiceAnswerPath.FALLBACK, latencyMs = 400L),
            ),
        )

        assertEquals(400L, report.latencyMedianMs)
    }

    private fun observation(
        path: VoiceAnswerPath,
        latencyMs: Long? = null,
        wrongKind: Boolean = false,
    ) = VoiceParseObservation(
        path = path,
        latencyMs = latencyMs,
        proposedKinds = emptyList(),
        keptKinds = emptyList(),
        wrongKind = wrongKind,
    )
}
