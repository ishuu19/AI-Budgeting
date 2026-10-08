package com.ledgerai.app.data.voice

/**
 * Five offline voice paths, best first. Cloud is never required.
 * 1. SenseVoice (sherpa-onnx) — strongest file transcription
 * 2. On-device — Android SpeechRecognizer, live, prefers offline
 * 3. Zipformer (sherpa-onnx) — smaller streaming English model
 * 4. Whisper tiny — ggml file + optional native runtime
 * 5. Vosk — small English model
 */
enum class OfflineVoiceEngine(
    val id: String,
    val label: String,
    val sizeHint: String
) {
    SHERPA("sherpa", "SenseVoice", "~250 MB"),
    ANDROID("android", "On-device", "System"),
    ZIPFORMER("zipformer", "Zipformer", "~70 MB"),
    WHISPER("whisper", "Whisper", "~75 MB"),
    VOSK("vosk", "Vosk", "~40 MB");

    /** Live recognition (no audio file); results stream while listening. */
    val isLive: Boolean get() = this == ANDROID

    companion object {
        fun fromId(id: String?): OfflineVoiceEngine = when (id) {
            "rules" -> ANDROID
            else -> entries.firstOrNull { it.id == id } ?: SHERPA
        }
    }
}
