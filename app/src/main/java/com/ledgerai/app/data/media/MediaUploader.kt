package com.ledgerai.app.data.media

import com.google.gson.JsonParser
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files live in Cloudflare R2 (S3-compatible); text data stays in Supabase.
 * The app never holds R2 keys. It asks the `media-sign` Edge Function (JWT-checked) for a
 * short-lived presigned URL, then sends or fetches the bytes directly with that URL.
 * Objects are `{userId}/{assetId}.jpg`; the function builds the key, so a user cannot reach another folder.
 */
@Singleton
class MediaUploader @Inject constructor(
    private val session: UserSession,
    private val repo: MediaRepository,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    private class Signed(val url: String, val path: String)

    /** True for a signed-in Supabase user on a build that has a project URL. */
    suspend fun canUpload(): Boolean = baseUrl.isNotEmpty() && session.userInfo.first().hasRemoteUser

    /** Uploads one asset and marks it uploaded. Returns false on any failure so the worker retries. */
    suspend fun upload(row: MediaAssetEntity): Boolean = withContext(Dispatchers.IO) {
        val file = File(row.localPath)
        if (!file.exists()) return@withContext false
        val signed = sign("put", row.id) ?: return@withContext false
        val request = Request.Builder()
            .url(signed.url)
            .header("Content-Type", "image/jpeg")
            .put(file.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val ok = runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
        if (ok) {
            // Re-read so analysis written meanwhile is not overwritten with a stale copy.
            val current = repo.get(row.id) ?: row
            repo.save(current.copy(remotePath = signed.path, uploadState = UploadState.UPLOADED))
        }
        ok
    }

    /** Fetches the full image for a photo captured on another device. Local copies are never re-downloaded. */
    suspend fun downloadIfMissing(row: MediaAssetEntity): Boolean = withContext(Dispatchers.IO) {
        val dest = repo.fullFile(row.id)
        if (dest.exists()) return@withContext true
        if (row.remotePath == null) return@withContext false
        val signed = sign("get", row.id) ?: return@withContext false
        val ok = runCatching {
            client.newCall(Request.Builder().url(signed.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                resp.body?.byteStream()?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                true
            }
        }.getOrDefault(false)
        if (ok) repo.ensureThumb(row.id)
        ok
    }

    private suspend fun sign(action: String, id: String): Signed? {
        val info = session.userInfo.first()
        if (!info.hasRemoteUser || baseUrl.isEmpty()) return null
        val body = """{"action":"$action","id":"$id"}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/media-sign")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${info.accessToken}")
            .post(body)
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                Signed(json.get("url").asString, json.get("path").asString)
            }
        }.getOrNull()
    }
}
