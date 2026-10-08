package com.ledgerai.app.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the small English Vosk model once into [Context.getFilesDir], unzips, and caches the path.
 */
@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var cachedModelDir: File? = null

    fun isModelReady(): Boolean {
        cachedModelDir?.takeIf { isValidModelDir(it) }?.let { return true }
        val dir = modelDir()
        return isValidModelDir(dir).also { if (it) cachedModelDir = dir }
    }

    fun modelPathOrNull(): String? =
        cachedModelDir?.takeIf { isValidModelDir(it) }?.absolutePath
            ?: modelDir().takeIf { isValidModelDir(it) }?.also { cachedModelDir = it }?.absolutePath

    /**
     * Ensures the English small model is on disk. [onProgress] receives 0f..1f while downloading.
     * @return absolute path to the unpacked model directory
     */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            modelPathOrNull()?.let { return@withContext Result.success(it) }

            val root = File(context.filesDir, MODEL_ROOT).apply { mkdirs() }
            val zipFile = File(root, "$MODEL_FOLDER_NAME.zip")
            val targetDir = modelDir()

            try {
                if (targetDir.exists()) targetDir.deleteRecursively()
                downloadFile(MODEL_URL, zipFile, onProgress)
                unzip(zipFile, root)
                zipFile.delete()

                if (!isValidModelDir(targetDir)) {
                    return@withContext Result.failure(
                        IllegalStateException("Downloaded Vosk model looks incomplete.")
                    )
                }
                cachedModelDir = targetDir
                onProgress(1f)
                Result.success(targetDir.absolutePath)
            } catch (e: Exception) {
                runCatching { zipFile.delete() }
                runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
                Result.failure(e)
            }
        }
    }

    private fun modelDir(): File = File(context.filesDir, "$MODEL_ROOT/$MODEL_FOLDER_NAME")

    private fun isValidModelDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        // Small en-us model ships am/ and conf/ (or graph/) at the top level.
        val hasAm = File(dir, "am").isDirectory || File(dir, "am/final.mdl").exists()
        val hasConf = File(dir, "conf").isDirectory || File(dir, "ivector").isDirectory
        return hasAm && (hasConf || File(dir, "graph").isDirectory)
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        dest.parentFile?.mkdirs()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model download failed (HTTP ${connection.responseCode})")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    var lastReported = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        } else {
                            onProgress(0.05f)
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val canonicalDest = destDir.canonicalPath
                val canonicalOut = outFile.canonicalPath
                if (!canonicalOut.startsWith(canonicalDest + File.separator) &&
                    canonicalOut != canonicalDest
                ) {
                    throw SecurityException("Zip path traversal blocked: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        const val MODEL_FOLDER_NAME = "vosk-model-small-en-us-0.15"
        private const val MODEL_ROOT = "vosk"
        private const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }
}
