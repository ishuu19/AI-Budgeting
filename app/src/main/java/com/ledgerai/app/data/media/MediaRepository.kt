package com.ledgerai.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local source of truth for captured photos. A photo is saved on the phone first
 * (full size for the AI, thumbnail for lists) and a row queues it for analysis and upload.
 * Nothing here needs the network, so capture works offline.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaAssetDao,
) {
    private val dir: File get() = File(context.filesDir, "media").also { it.mkdirs() }

    fun fullFile(id: String): File = File(dir, "$id.jpg")
    fun thumbFile(id: String): File = File(dir, "${id}_t.jpg")

    fun observeRecent(userId: String, limit: Int = 60): Flow<List<MediaAssetEntity>> =
        dao.observeRecent(userId, limit)

    suspend fun get(id: String): MediaAssetEntity? = dao.get(id)
    suspend fun save(row: MediaAssetEntity) = dao.upsert(row.copy(updatedAt = System.currentTimeMillis()))
    suspend fun pendingAnalysis(): List<MediaAssetEntity> = dao.listPendingAnalysis()
    suspend fun pendingUpload(): List<MediaAssetEntity> = dao.listPendingUpload()
    suspend fun listByKind(userId: String, kind: String): List<MediaAssetEntity> = dao.listByKind(userId, kind)

    suspend fun discard(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    /** Copies [uri] into private storage as a bounded JPEG. Returns null if the image cannot be read. */
    suspend fun addImage(userId: String, uri: Uri, note: String): MediaAssetEntity? = withContext(Dispatchers.IO) {
        val bitmap = runCatching { decodeUpright(uri) }.getOrNull() ?: return@withContext null
        val id = UUID.randomUUID().toString()
        try {
            writeJpeg(scaleTo(bitmap, FULL_MAX), fullFile(id), 85)
            writeJpeg(scaleTo(bitmap, THUMB_MAX), thumbFile(id), 80)
        } finally {
            bitmap.recycle()
        }
        val now = System.currentTimeMillis()
        val row = MediaAssetEntity(
            id = id,
            userId = userId,
            localPath = fullFile(id).absolutePath,
            note = note.trim(),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        row
    }

    /** Rebuilds the thumbnail when only the full image exists (for example after a download). */
    suspend fun ensureThumb(id: String) = withContext(Dispatchers.IO) {
        val full = fullFile(id)
        if (!full.exists() || thumbFile(id).exists()) return@withContext
        BitmapFactory.decodeFile(full.absolutePath)?.let { b ->
            writeJpeg(scaleTo(b, THUMB_MAX), thumbFile(id), 80)
            b.recycle()
        }
    }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds-only decoding returns null by design; the size lands in [bounds].
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: return null
        if (!opened) return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > FULL_MAX * 2 || bounds.outHeight / sample > FULL_MAX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return raw
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated != raw) raw.recycle()
        return rotated
    }

    private fun scaleTo(source: Bitmap, max: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= max) return source
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    companion object {
        const val FULL_MAX = 1600
        const val THUMB_MAX = 360
    }
}
