package com.ledgerai.app.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Small English Zipformer model (sherpa-onnx). Offline. */
@Singleton
class ZipformerModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isReady(): Boolean {
        val dir = modelDir()
        return File(dir, "tokens.txt").isFile &&
            dir.listFiles().orEmpty().any { it.name.endsWith(".onnx") }
    }

    fun modelDir(): File = File(context.filesDir, "sherpa/sherpa-onnx-streaming-zipformer-en-2023-06-26")

    suspend fun ensure(onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext Result.success(modelDir())
        val root = File(context.filesDir, "sherpa").apply { mkdirs() }
        val zip = File(root, "zipformer-en.zip")
        try {
            val conn = (URL(URL_MODEL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            conn.connect()
            val total = conn.contentLength.toLong().coerceAtLeast(1)
            var read = 0L
            conn.inputStream.use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        read += n
                        onProgress((read / total.toFloat()).coerceIn(0f, 0.9f))
                    }
                }
            }
            conn.disconnect()
            ZipInputStream(zip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(root, entry.name)
                    if (entry.isDirectory) outFile.mkdirs() else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zip.delete()
            if (!isReady()) Result.failure(IllegalStateException("Zipformer model incomplete"))
            else Result.success(modelDir())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val URL_MODEL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.zip"
    }
}
