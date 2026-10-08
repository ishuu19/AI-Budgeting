package com.ledgerai.app.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.ledgerai.app.R
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Built-in `res/raw` tones and custom SAF copies under [filesDir]/tones/.
 * Stored [toneUri] values:
 * - `null` or blank → default built-in beep
 * - `builtin:alarm_beep` / `builtin:alarm_chime`
 * - absolute path or `file://` under tones dir for custom picks
 */
object AlarmToneHelper {

    const val MAX_CUSTOM_BYTES = 10L * 1024L * 1024L
    private const val TONES_DIR = "tones"
    private const val PREFIX_BUILTIN = "builtin:"
    private val ALLOWED_EXT = setOf("mp3", "ogg", "wav", "m4a", "aac")
    private val ALLOWED_MIME = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/ogg",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/mp4",
        "audio/aac",
        "audio/x-m4a",
        "application/ogg"
    )

    data class BuiltInTone(val id: String, val displayName: String, val rawRes: Int) {
        val uriString: String get() = "$PREFIX_BUILTIN$id"
    }

    val builtInTones: List<BuiltInTone> = listOf(
        BuiltInTone("alarm_beep", "Beep", R.raw.alarm_beep),
        BuiltInTone("alarm_chime", "Chime", R.raw.alarm_chime),
        BuiltInTone("alarm_bell", "Bell", R.raw.alarm_bell),
        BuiltInTone("alarm_digital", "Digital", R.raw.alarm_digital),
        BuiltInTone("alarm_gong", "Gong", R.raw.alarm_gong),
        BuiltInTone("alarm_melody", "Melody", R.raw.alarm_melody),
        BuiltInTone("alarm_pulse", "Pulse", R.raw.alarm_pulse),
        BuiltInTone("alarm_siren", "Siren", R.raw.alarm_siren),
        BuiltInTone("alarm_tick", "Tick", R.raw.alarm_tick),
        BuiltInTone("alarm_zen", "Zen", R.raw.alarm_zen)
    )

    fun displayName(toneUri: String?): String {
        if (toneUri.isNullOrBlank()) return builtInTones.first().displayName
        if (toneUri.startsWith(PREFIX_BUILTIN)) {
            val id = toneUri.removePrefix(PREFIX_BUILTIN)
            return builtInTones.firstOrNull { it.id == id }?.displayName ?: "Built-in"
        }
        return File(toneUri.removePrefix("file://")).nameWithoutExtension.ifBlank { "Custom" }
    }

    fun resolvePlayableUri(context: Context, toneUri: String?): Uri {
        if (toneUri.isNullOrBlank()) {
            return resourceUri(context, builtInTones.first().rawRes)
        }
        if (toneUri.startsWith(PREFIX_BUILTIN)) {
            val id = toneUri.removePrefix(PREFIX_BUILTIN)
            val res = builtInTones.firstOrNull { it.id == id }?.rawRes
                ?: builtInTones.first().rawRes
            return resourceUri(context, res)
        }
        val path = toneUri.removePrefix("file://")
        val file = File(path)
        if (file.isFile && file.canRead()) {
            return file.toUri()
        }
        Log.w(TAG, "Tone missing ($toneUri); falling back to built-in")
        return resourceUri(context, builtInTones.first().rawRes)
    }

    /**
     * Copies a user-picked audio URI into app-private storage.
     * @return stored absolute path suitable for [com.ledgerai.app.domain.model.CalendarEvent.alarmToneUri]
     */
    fun copyCustomTone(context: Context, source: Uri): Result<String> {
        return try {
            val (name, size) = queryNameAndSize(context, source)
            if (size > MAX_CUSTOM_BYTES) {
                return Result.failure(
                    IllegalArgumentException("Tone must be ≤ ${MAX_CUSTOM_BYTES / (1024 * 1024)} MB")
                )
            }
            val ext = extensionOf(name, context, source)
            if (ext !in ALLOWED_EXT) {
                return Result.failure(
                    IllegalArgumentException("Unsupported type; use mp3, ogg, wav, or m4a")
                )
            }
            val mime = context.contentResolver.getType(source)
            if (mime != null && mime !in ALLOWED_MIME && !mime.startsWith("audio/")) {
                return Result.failure(IllegalArgumentException("File is not an audio tone"))
            }
            val dir = File(context.filesDir, TONES_DIR).also { it.mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > MAX_CUSTOM_BYTES) {
                        dest.delete()
                        throw IllegalArgumentException("Tone must be ≤ 10 MB")
                    }
                }
            } ?: return Result.failure(IOException("Unable to read selected file"))
            Result.success(dest.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "copyCustomTone failed", e)
            Result.failure(e)
        }
    }

    private fun resourceUri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    private fun queryNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "tone"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun extensionOf(displayName: String, context: Context, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in ALLOWED_EXT) return fromName
        return when (context.contentResolver.getType(uri)) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
            else -> fromName.ifBlank { "wav" }
        }
    }

    private const val TAG = "AlarmToneHelper"
}
