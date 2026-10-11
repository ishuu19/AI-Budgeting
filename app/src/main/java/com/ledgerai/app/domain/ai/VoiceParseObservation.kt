package com.ledgerai.app.domain.ai

enum class VoiceAnswerPath {
    FAST,
    FALLBACK,
}

data class VoiceParseObservation(
    val path: VoiceAnswerPath,
    val latencyMs: Long?,
    val proposedKinds: List<String>,
    val keptKinds: List<String>,
    val wrongKind: Boolean,
)

fun observeVoiceParse(
    attempted: Boolean,
    latencyMs: Long?,
    proposedKinds: List<String>,
    keptKinds: List<String>,
): VoiceParseObservation {
    if (!attempted) {
        return VoiceParseObservation(
            path = VoiceAnswerPath.FALLBACK,
            latencyMs = null,
            proposedKinds = emptyList(),
            keptKinds = keptKinds,
            wrongKind = false,
        )
    }
    if (proposedKinds.isEmpty()) {
        return VoiceParseObservation(
            path = VoiceAnswerPath.FALLBACK,
            latencyMs = latencyMs,
            proposedKinds = proposedKinds,
            keptKinds = keptKinds,
            wrongKind = false,
        )
    }
    return VoiceParseObservation(
        path = VoiceAnswerPath.FAST,
        latencyMs = latencyMs,
        proposedKinds = proposedKinds,
        keptKinds = keptKinds,
        wrongKind = proposedKinds != keptKinds,
    )
}
