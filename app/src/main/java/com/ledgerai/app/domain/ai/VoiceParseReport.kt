package com.ledgerai.app.domain.ai

data class VoiceParseReport(
    val samples: Int,
    val fastAnswers: Int,
    val fallbackAnswers: Int,
    val wrongKindAnswers: Int,
    val latencyMedianMs: Long?,
)

fun summarizeVoiceParses(samples: List<VoiceParseObservation>): VoiceParseReport {
    var fastAnswers = 0
    var fallbackAnswers = 0
    var wrongKindAnswers = 0
    val latencies = ArrayList<Long>(samples.size)
    for (sample in samples) {
        when (sample.path) {
            VoiceAnswerPath.FAST -> {
                fastAnswers++
                if (sample.wrongKind) wrongKindAnswers++
            }
            VoiceAnswerPath.FALLBACK -> fallbackAnswers++
        }
        sample.latencyMs?.let(latencies::add)
    }
    return VoiceParseReport(
        samples = samples.size,
        fastAnswers = fastAnswers,
        fallbackAnswers = fallbackAnswers,
        wrongKindAnswers = wrongKindAnswers,
        latencyMedianMs = lowerMedian(latencies),
    )
}

/** Null when [VoiceParseReport.fastAnswers] is 0. Unknown, not a measured zero. */
fun VoiceParseReport.wrongKindRate(): Double? {
    if (fastAnswers == 0) return null
    return wrongKindAnswers.toDouble() / fastAnswers
}

/** Even counts take the lower of the two middle values, not their average. */
private fun lowerMedian(latencies: List<Long>): Long? {
    if (latencies.isEmpty()) return null
    val sorted = latencies.sorted()
    return sorted[(sorted.size - 1) / 2]
}
