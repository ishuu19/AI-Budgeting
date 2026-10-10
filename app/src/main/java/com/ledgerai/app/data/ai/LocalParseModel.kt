package com.ledgerai.app.data.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * First parse layer. Gemma 3 270M (about 290 MB) runs on the phone. Nothing is sent off the device.
 * The file is downloaded once into app storage and refused if it would pass 0.5 GB.
 */
@Singleton
class LocalParseModel @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val statusFlow = MutableStateFlow("On-phone model")
    val status: StateFlow<String> = statusFlow

    private val gate = Mutex()
    private var engine: LlmInference? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    suspend fun prepare() {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = modelFile()
                val llm = open(file) ?: error("Model did not load")
                engine = llm
                val marker = File(file.parentFile, ".checked")
                val check = File(context.filesDir, "model_check.txt")
                if (check.isFile) {
                    val sentence = check.readText().trim()
                    val sample = ask(llm, LocalParsePrompt.of(sentence))
                    val read = LocalParseAnswer.read(sample, sentence)
                    Log.i(TAG, "check sample=$sample")
                    Log.i(TAG, "check read=$read")
                    check.delete()
                } else if (!marker.exists()) {
                    val sample = ask(llm, LocalParsePrompt.of("spent 12 on lunch"))
                    Log.i(TAG, "sample=$sample")
                    marker.writeText("1")
                } else Unit
            }.onSuccess {
                statusFlow.value = "On-phone model ready"
            }.onFailure { error ->
                Log.e(TAG, "prepare failed", error)
                statusFlow.value = "On-phone model unavailable"
            }
        }
    }

    suspend fun complete(prompt: String): String? = withContext(Dispatchers.Default) {
        val file = runCatching { modelFile() }.getOrNull() ?: return@withContext null
        gate.withLock {
            val llm = engine ?: open(file).also { engine = it } ?: return@withLock null
            runCatching { ask(llm, prompt) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun ask(llm: LlmInference, prompt: String): String {
        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(1)
                .setTemperature(0f)
                .build()
        )
        return try {
            session.addQueryChunk(prompt)
            session.generateResponse().orEmpty()
        } finally {
            session.close()
        }
    }

    private fun open(file: File): LlmInference? =
        runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(1024)
                .build()
            LlmInference.createFromOptions(context, options)
        }.onFailure { Log.e(TAG, "load failed", it) }.getOrNull()

    private fun modelFile(): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val dest = File(dir, FILE_NAME)
        if (dest.isFile && dest.length() in 1..MAX_BYTES) return dest
        val part = File(dir, "$FILE_NAME.part")
        var attempt = 0
        var lastError: Exception? = null
        while (part.length() < EXPECTED_BYTES && attempt < 40) {
            attempt++
            statusFlow.value = "Downloading on-phone model ${(part.length() / 1_048_576)} MB"
            try {
                downloadMore(part)
                lastError = null
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "download paused at ${part.length()}", error)
                Thread.sleep(1500)
            }
        }
        if (part.length() < EXPECTED_BYTES) throw lastError ?: IllegalStateException("Model download stopped")
        if (part.length() !in EXPECTED_BYTES..MAX_BYTES) error("Model download stopped at ${part.length()} bytes")
        if (!part.renameTo(dest)) error("Could not store the model")
        return dest
    }

    private fun downloadMore(part: File) {
        val start = part.length()
        val request = Request.Builder()
            .url(MODEL_URL)
            .header("Range", "bytes=$start-")
            .build()
        http.newCall(request).execute().use { response ->
            val resume = response.code == 206
            if (response.code == 200 && start > 0L) part.writeBytes(ByteArray(0))
            if (!resume && response.code != 200) error("Model download failed (${response.code})")
            val body = response.body ?: error("Empty model download")
            body.byteStream().use { input ->
                part.appendBytesChunked(input, if (resume) start else 0L)
            }
        }
    }

    private fun File.appendBytesChunked(input: java.io.InputStream, already: Long) {
        java.io.FileOutputStream(this, already > 0L).use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = already
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) error("Model is larger than 0.5 GB")
                output.write(buffer, 0, n)
            }
        }
    }

    private companion object {
        const val TAG = "LocalParseModel"
        const val FILE_NAME = "gemma3-270m-it-q8.task"
        const val EXPECTED_BYTES = 303_950_933L
        const val MAX_BYTES = 500L * 1024L * 1024L
        const val MODEL_URL =
            "https://huggingface.co/omermalix66/gemma3-270m-it-q8.task/resolve/main/gemma3-270m-it-q8.task"
    }
}
