package com.ledgerai.app.domain.ai

class VoiceParseSession {
    private val observations = mutableListOf<VoiceParseObservation>()

    fun record(observation: VoiceParseObservation) {
        observations.add(observation)
    }

    fun snapshot(): VoiceParseReport = summarizeVoiceParses(observations.toList())
}
