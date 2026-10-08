package com.ledgerai.app.data.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sherpa-ONNX offline transcription wrapper.
 * Uses reflection to safely access Sherpa-ONNX when present on the classpath.
 * Falls back gracefully to Vosk if unavailable.
 */
@Singleton
class SherpaOnnxTranscriber @Inject constructor(
    private val modelManager: SherpaOnnxModelManager
) {
    suspend fun transcribe(
        audioFile: File,
        onModelProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio missing"))
        }

        if (!isSherpaAvailable()) {
            return@withContext Result.failure(IllegalStateException("Sherpa-ONNX native not present"))
        }

        val modelDir = try {
            modelManager.ensureModel(onModelProgress).getOrThrow()
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Sherpa model download failed: ${e.message}. Falling back to Vosk.")
            )
        }

        try {
            val pcm = decodeToMonoPcm16(audioFile, 16000)
            if (pcm.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Could not decode audio"))
            }

            val text = recognizeWithSherpa(modelDir.absolutePath, pcm)
            if (text.isBlank()) {
                Result.failure(IllegalStateException("Sherpa could not understand the audio"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sherpa transcription failed, will fallback", e)
            Result.failure(e)
        }
    }

    private fun isSherpaAvailable(): Boolean = try {
        Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
        true
    } catch (_: Throwable) { false }

    private fun recognizeWithSherpa(modelDir: String, pcm16: ByteArray): String {
        return try {
            // Defensive execution via reflection if library is provided
            val senseVoiceConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig")
            val senseVoiceConfig = senseVoiceConfigClass.getConstructor(
                String::class.java, String::class.java, Boolean::class.java
            ).newInstance("$modelDir/model.int8.onnx", "$modelDir/tokens.txt", true)

            val modelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            val modelConfig = modelConfigClass.getConstructor(
                senseVoiceConfigClass
            ).newInstance(senseVoiceConfig)

            val recognizerConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            val recognizerConfig = recognizerConfigClass.getConstructor(
                modelConfigClass
            ).newInstance(modelConfig)

            val recognizerClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            val recognizer = recognizerClass.getConstructor(recognizerConfigClass).newInstance(recognizerConfig)

            val createStreamMethod = recognizerClass.getMethod("createStream")
            val stream = createStreamMethod.invoke(recognizer)

            val streamClass = stream.javaClass
            val acceptWaveformMethod = streamClass.getMethod("acceptWaveform", ByteArray::class.java, Float::class.javaPrimitiveType)
            acceptWaveformMethod.invoke(stream, pcm16, 16000f)

            val decodeMethod = recognizerClass.getMethod("decode", streamClass)
            decodeMethod.invoke(recognizer, stream)

            val getResultMethod = recognizerClass.getMethod("getResult", streamClass)
            val result = getResultMethod.invoke(recognizer, stream)
            val textProperty = result.javaClass.getField("text")
            (textProperty.get(result) as? String)?.trim().orEmpty()
        } catch (t: Throwable) {
            Log.w(TAG, "Sherpa recognize failed: ${t.message}")
            ""
        }
    }

    companion object {
        private const val TAG = "SherpaOnnxTranscriber"
    }
}
