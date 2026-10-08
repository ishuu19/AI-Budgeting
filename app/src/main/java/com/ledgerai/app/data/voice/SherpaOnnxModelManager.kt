package com.ledgerai.app.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages download + extraction of a small offline Sherpa-ONNX model.
 * Default: SenseVoice Small (good accuracy, fully offline).
 * Falls back gracefully if download fails.
 */
@Singleton
class SherpaOnnxModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var cachedDir: File? = null

    companion object {
        private const val MODEL_ROOT = "sherpa"
        // Small, good quality offline model (SenseVoice Small English-friendly)
        // ~250MB zip. We use a known stable mirror.
        private const val MODEL_FOLDER = "sherpa-onnx-sense-voice-small"
        private const val ZIP_NAME = "$MODEL_FOLDER.zip"
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-small.zip"
    }

    fun isModelReady(): Boolean {
        cachedDir?.takeIf { isValid(it) }?.let { return true }
        val dir = modelDir()
        return isValid(dir).also { if (it) cachedDir = dir }
    }

    fun modelDirOrNull(): File? =
        cachedDir?.takeIf { isValid(it) } ?: modelDir().takeIf { isValid(it) }?.also { cachedDir = it }

    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            modelDirOrNull()?.let { return@withContext Result.success(it) }

            val root = File(context.filesDir, MODEL_ROOT).apply { mkdirs() }
            val zipFile = File(root, ZIP_NAME)
            val target = modelDir()

            try {
                if (target.exists()) target.deleteRecursively()
                download(zipFile, onProgress)
                unzip(zipFile, root)
                zipFile.delete()

                if (!isValid(target)) {
                    return@withContext Result.failure(IllegalStateException("Sherpa model incomplete after download"))
                }
                cachedDir = target
                onProgress(1f)
                Result.success(target)
            } catch (e: Exception) {
                runCatching { zipFile.delete() }
                runCatching { if (target.exists()) target.deleteRecursively() }
                Result.failure(e)
            }
        }
    }

    private fun modelDir(): File = File(context.filesDir, "$MODEL_ROOT/$MODEL_FOLDER")

    private fun isValid(dir: File): Boolean {
        if (!dir.isDirectory) return false
        // SenseVoice model has tokens.txt + model.onnx + int8.onnx etc.
        val hasTokens = File(dir, "tokens.txt").isFile
        val hasModel = File(dir, "model.int8.onnx").isFile || File(dir, "model.onnx").isFile
        return hasTokens && hasModel
    }

    private fun download(zip: File, onProgress: (Float) -> Unit) {
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
        }
        conn.connect()

        val total = conn.contentLength.toLong().coerceAtLeast(1)
        var read = 0L

        conn.inputStream.use { input ->
            FileOutputStream(zip).use { out ->
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
    }

    private fun unzip(zip: File, dest: File) {
        ZipInputStream(zip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
