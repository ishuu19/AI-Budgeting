package com.ledgerai.app.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Files the user adds on purpose (Vault uploads, achievement photos). The bytes are copied into app
 * storage from a document the user picked; the app never scans the phone's storage.
 * Unlocked files are queued for private cloud storage. Locked ones never are.
 */
object VaultFiles {
    const val MAX_BYTES = 25L * 1024 * 1024

    /** Extension to Content-Type. Must match the allowlist in the `media-sign` Edge Function. */
    val TYPES = mapOf(
        "jpg" to "image/jpeg", "png" to "image/png", "heic" to "image/heic", "pdf" to "application/pdf",
        "txt" to "text/plain",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "zip" to "application/zip",
    )

    /** The upload extension for a stored name, or null when the cloud does not accept that type. */
    fun uploadExt(name: String): String? {
        val raw = name.substringAfterLast('.', "").lowercase()
        val ext = if (raw == "jpeg") "jpg" else raw
        return ext.takeIf { it in TYPES }
    }

    /** Where a file lives on this phone, whether it was added here or fetched from the cloud. */
    fun localFile(context: Context, row: MediaAssetEntity): File {
        if (row.localPath.isNotBlank()) File(row.localPath).takeIf { it.exists() }?.let { return it }
        val ext = uploadExt(row.remotePath ?: row.title)?.let { ".$it" }.orEmpty()
        return File(File(context.filesDir, "vault").apply { mkdirs() }, row.id + ext)
    }

    suspend fun import(
        context: Context,
        uri: Uri,
        userId: String,
        linkedType: String,
        linkedId: String,
        locked: Boolean,
    ): Result<MediaAssetEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "file"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            require(size <= MAX_BYTES) { "Files can be up to 25 MB" }
            val mime = resolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: "application/octet-stream"
            val id = UUID.randomUUID().toString()
            val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
            val target = File(File(context.filesDir, "vault").apply { mkdirs() }, id + ext)
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: error("Could not read that file")
            val now = System.currentTimeMillis()
            val queue = !locked && userId.isNotBlank() && uploadExt(name) != null
            MediaAssetEntity(
                id = id,
                userId = userId,
                localPath = target.absolutePath,
                kind = MediaKind.OTHER,
                title = name,
                linkedType = linkedType,
                linkedId = linkedId,
                uploadState = if (queue) UploadState.PENDING else UploadState.LOCAL,
                // Not a photo to describe, so the AI pass skips it.
                analysisState = AnalysisState.DONE,
                mime = mime,
                sizeBytes = target.length(),
                locked = locked,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
