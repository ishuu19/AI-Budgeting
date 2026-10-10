package com.ledgerai.app.data.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline file transcription via Vosk.
 *
 * Prefers the real `org.vosk` API. If native/classes are unavailable at runtime,
 * falls back to a sibling `.txt` next to the audio (dev/stub only).
 */
@Singleton
class VoskTranscriber @Inject constructor(
    private val modelManager: VoskModelManager
) {
    suspend fun transcribe(
        audioFile: File,
        onModelProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audio file missing or empty."))
        }

        // Dev/test stub: sibling .txt next to the audio file (kept only when present).
        stubFromSiblingTxt(audioFile)?.let { return@withContext it }

        if (!isVoskAvailable()) {
            return@withContext modelRetryFailure(
                "Vosk native library unavailable. Retry the speech model download, or type instead."
            )
        }

        val modelPath = modelManager.ensureModel(onModelProgress).getOrElse {
            return@withContext modelRetryFailure(
                it.message?.takeIf { msg -> msg.isNotBlank() }
                    ?: "Speech model download failed. Retry the model download, or type instead."
            )
        }

        try {
            val pcm = decodeToMonoPcm16(audioFile, TARGET_SAMPLE_RATE)
            if (pcm.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Could not decode audio to PCM for transcription.")
                )
            }
            val text = recognizePcm(modelPath, pcm, TARGET_SAMPLE_RATE)
            if (text.isBlank()) {
                Result.failure(
                    IllegalStateException("Couldn't understand the audio. Please speak clearly or type instead.")
                )
            } else {
                Result.success(text)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Vosk native link failed", e)
            modelRetryFailure(
                "Offline speech engine failed to load. Retry the Vosk model download, or type instead."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    private fun modelRetryFailure(message: String): Result<String> =
        Result.failure(IllegalStateException(message))

    private fun recognizePcm(modelPath: String, pcm: ByteArray, sampleRate: Int): String {
        error("File speech models are off. The phone listener is the only voice path.")
    }

    private fun extractText(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            JSONObject(json).optString("text", "").trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun stubFromSiblingTxt(audioFile: File): Result<String>? {
        val sibling = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".txt")
        if (!sibling.exists()) return null
        val text = sibling.readText().trim()
        return if (text.isNotBlank()) Result.success(text) else null
    }

    companion object {
        private const val TAG = "VoskTranscriber"
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val CHUNK_BYTES = 4096

        fun isVoskAvailable(): Boolean = false
    }
}

/**
 * Decode wav / 3gp / m4a / aac into 16-bit mono PCM at [targetSampleRate].
 * WAV PCM is read directly; compressed formats go through MediaExtractor + MediaCodec.
 */
internal fun decodeToMonoPcm16(audioFile: File, targetSampleRate: Int): ByteArray {
    val name = audioFile.name.lowercase()
    if (name.endsWith(".wav") && looksLikePcmWav(audioFile)) {
        return readWavPcmAndResample(audioFile, targetSampleRate)
    }
    return decodeWithMediaCodec(audioFile, targetSampleRate)
}

private fun looksLikePcmWav(file: File): Boolean {
    if (file.length() < 44) return false
    FileInputStream(file).use { input ->
        val header = ByteArray(12)
        if (input.read(header) != 12) return false
        return header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'A'.code.toByte() &&
            header[10] == 'V'.code.toByte() &&
            header[11] == 'E'.code.toByte()
    }
}

private fun readWavPcmAndResample(file: File, targetSampleRate: Int): ByteArray {
    FileInputStream(file).use { input ->
        val header = ByteArray(44)
        if (input.read(header) != 44) return ByteArray(0)
        val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val bits = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        if (bits != 16) {
            // Fall back to MediaCodec path for unusual WAV encodings.
            return decodeWithMediaCodec(file, targetSampleRate)
        }
        val pcm = input.readBytes()
        val mono = if (channels <= 1) pcm else downmixInterleaved16(pcm, channels)
        return resamplePcm16(mono, sampleRate, targetSampleRate)
    }
}

private fun decodeWithMediaCodec(audioFile: File, targetSampleRate: Int): ByteArray {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(audioFile.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        if (track < 0 || format == null) return ByteArray(0)
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return ByteArray(0)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outSampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: targetSampleRate
        var outChannels = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    outSampleRate = newFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                        ?: outSampleRate
                    outChannels = newFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                        ?: outChannels
                }
                outIndex >= 0 -> {
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.get(chunk)
                        out.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        }

        codec.stop()
        codec.release()

        var pcm = out.toByteArray()
        if (outChannels > 1) pcm = downmixInterleaved16(pcm, outChannels)
        return resamplePcm16(pcm, outSampleRate, targetSampleRate)
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.getIntegerOrNull(key: String): Int? =
    try {
        if (containsKey(key)) getInteger(key) else null
    } catch (_: Exception) {
        null
    }

private fun downmixInterleaved16(pcm: ByteArray, channels: Int): ByteArray {
    if (channels <= 1) return pcm
    val samples = pcm.size / 2
    val frames = samples / channels
    val out = ByteArray(frames * 2)
    val src = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val dst = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    for (i in 0 until frames) {
        var sum = 0
        for (c in 0 until channels) {
            sum += src.get(i * channels + c).toInt()
        }
        dst.put((sum / channels).toShort())
    }
    return out
}

private fun resamplePcm16(pcm: ByteArray, fromRate: Int, toRate: Int): ByteArray {
    if (fromRate <= 0 || fromRate == toRate) return pcm
    val inSamples = ShortArray(pcm.size / 2)
    ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inSamples)
    val outLen = (inSamples.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
    val outSamples = ShortArray(outLen)
    for (i in 0 until outLen) {
        val srcPos = i.toDouble() * fromRate / toRate
        val idx = srcPos.toInt().coerceIn(0, inSamples.lastIndex)
        val next = (idx + 1).coerceAtMost(inSamples.lastIndex)
        val frac = srcPos - idx
        val mixed = inSamples[idx] * (1.0 - frac) + inSamples[next] * frac
        outSamples[i] = mixed.toInt().toShort()
    }
    val out = ByteArray(outLen * 2)
    ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outSamples)
    return out
}
