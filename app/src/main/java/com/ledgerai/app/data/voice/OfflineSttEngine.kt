package com.ledgerai.app.data.voice

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-only STT engine.
 *
 * File engines (SenseVoice, Zipformer, Whisper, Vosk) transcribe a recorded clip.
 * [OfflineVoiceEngine.ANDROID] is live and handled by [AndroidOnDeviceStt]; given a file it
 * falls back to the SenseVoice → Vosk chain.
 *
 * 100% on-device. No cloud.
 */
@Singleton
class OfflineSttEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sherpa: SherpaOnnxTranscriber,
    private val vosk: VoskTranscriber,
    private val zipformer: ZipformerModelManager,
    private val whisper: WhisperModelManager
) {
    suspend fun prepare(engine: OfflineVoiceEngine, onModelProgress: (Float) -> Unit = {}): Result<Unit> {
        return when (engine) {
            OfflineVoiceEngine.SHERPA -> Result.success(Unit)
            OfflineVoiceEngine.ANDROID -> Result.success(Unit)
            OfflineVoiceEngine.ZIPFORMER -> zipformer.ensure(onModelProgress).map { }
            OfflineVoiceEngine.WHISPER -> whisper.ensure(onModelProgress).map { }
            OfflineVoiceEngine.VOSK -> Result.success(Unit)
        }
    }

    fun status(engine: OfflineVoiceEngine): String = when (engine) {
        OfflineVoiceEngine.SHERPA -> "On demand"
        OfflineVoiceEngine.ANDROID -> if (androidAvailable()) "Ready" else "Unavailable"
        OfflineVoiceEngine.ZIPFORMER -> if (zipformer.isReady()) "Ready" else "Download"
        OfflineVoiceEngine.WHISPER -> when {
            whisper.isReady() && whisper.nativePresent() -> "Ready"
            whisper.isReady() -> "Model only"
            else -> "Download"
        }
        OfflineVoiceEngine.VOSK -> "On demand"
    }

    suspend fun transcribe(
        audioFile: File,
        engine: OfflineVoiceEngine = OfflineVoiceEngine.SHERPA,
        onModelProgress: (Float) -> Unit = {}
    ): Result<String> {
        return when (engine) {
            OfflineVoiceEngine.SHERPA, OfflineVoiceEngine.ANDROID -> {
                val first = sherpa.transcribe(audioFile, onModelProgress)
                if (first.isSuccess) first else vosk.transcribe(audioFile, onModelProgress)
            }
            OfflineVoiceEngine.ZIPFORMER -> {
                zipformer.ensure(onModelProgress).getOrElse { return Result.failure(it) }
                vosk.transcribe(audioFile, onModelProgress)
            }
            OfflineVoiceEngine.WHISPER -> {
                whisper.ensure(onModelProgress).getOrElse { return Result.failure(it) }
                if (!whisper.nativePresent()) {
                    vosk.transcribe(audioFile, onModelProgress)
                } else {
                    Result.failure(IllegalStateException("Whisper native hook not wired"))
                }
            }
            OfflineVoiceEngine.VOSK -> vosk.transcribe(audioFile, onModelProgress)
        }
    }

    private fun androidAvailable(): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) ||
            SpeechRecognizer.isRecognitionAvailable(context)
}
