package com.ledgerai.app.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** ggml-tiny.en for whisper.cpp. File is kept on device. Native lib is optional. */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun modelFile(): File = File(context.filesDir, "whisper/ggml-tiny.en.bin")

    fun isReady(): Boolean = modelFile().isFile && modelFile().length() > 1_000_000

    fun nativePresent(): Boolean = false

    suspend fun ensure(onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext Result.success(modelFile())
        val file = modelFile().apply { parentFile?.mkdirs() }
        try {
            val conn = (URL(URL_MODEL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                instanceFollowRedirects = true
            }
            conn.connect()
            val total = conn.contentLength.toLong().coerceAtLeast(1)
            var read = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        read += n
                        onProgress((read / total.toFloat()).coerceIn(0f, 0.98f))
                    }
                }
            }
            conn.disconnect()
            if (!isReady()) Result.failure(IllegalStateException("Whisper model incomplete"))
            else Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val URL_MODEL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
    }
}
