package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.media.MediaAssetDao
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.UploadState
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room `media_assets` <-> Supabase `public.media_assets` (text only; the image bytes live in R2).
 * The Room id is already a UUID and is the remote id too. Device-only fields stay local:
 * localPath, uploadState and the pending payload.
 */
@Singleton
class MediaAssetExtraSync @Inject constructor(
    private val dao: MediaAssetDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient
) : ExtraSync {

    private val api: MediaApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(MediaApi::class.java)

    override suspend fun sync(bearer: String, apiKey: String, userId: String, sinceMs: Long, filter: String) {
        pull(bearer, apiKey, filter)
        push(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pull(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pull(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote ->
                val local = dao.get(remote.id)
                if (local == null) {
                    dao.upsert(remote.toNewEntity())
                } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                    dao.upsert(remote.mergeInto(local))
                }
            }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val rows = dao.listForSync(sinceMs).map { it.toRemote(userId) }
        if (rows.isEmpty()) return
        api.upsert(bearer, apiKey, body = rows)
    }

    private interface MediaApi {
        @GET("media_assets")
        suspend fun pull(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteMediaDto>

        @POST("media_assets")
        suspend fun upsert(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteMediaDto>
        ): List<RemoteMediaDto>
    }

    private data class RemoteMediaDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("remote_path") val remotePath: String? = null,
        @SerializedName("kind") val kind: String = "unknown",
        @SerializedName("title") val title: String = "",
        @SerializedName("description") val description: String = "",
        @SerializedName("note") val note: String = "",
        @SerializedName("analysis_state") val analysisState: String = "pending",
        @SerializedName("linked_type") val linkedType: String? = null,
        @SerializedName("linked_id") val linkedId: String? = null,
        @SerializedName("created_at") val createdAt: String? = null,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private fun MediaAssetEntity.toRemote(owner: String) = RemoteMediaDto(
        id = id,
        userId = owner,
        remotePath = remotePath,
        kind = kind,
        title = title,
        description = description,
        note = note,
        analysisState = analysisState,
        linkedType = linkedType,
        linkedId = linkedId,
        createdAt = SyncTime.millisToIso(createdAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    /** A photo from another device: no local file yet, so it counts as uploaded when it has a remote path. */
    private fun RemoteMediaDto.toNewEntity() = MediaAssetEntity(
        id = id,
        userId = userId.orEmpty(),
        localPath = "",
        remotePath = remotePath,
        kind = kind,
        title = title,
        description = description,
        note = note,
        uploadState = if (remotePath != null) UploadState.UPLOADED else UploadState.PENDING,
        analysisState = analysisState,
        linkedType = linkedType,
        linkedId = linkedId,
        createdAt = SyncTime.isoToMillis(createdAt),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

    private fun RemoteMediaDto.mergeInto(local: MediaAssetEntity) = local.copy(
        remotePath = remotePath ?: local.remotePath,
        kind = kind,
        title = title,
        description = description,
        note = note,
        analysisState = analysisState,
        linkedType = linkedType,
        linkedId = linkedId,
        uploadState = if (remotePath != null) UploadState.UPLOADED else local.uploadState,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

    private companion object {
        const val PAGE_SIZE = 500
        const val PULL_ORDER = "updated_at.asc,id.asc"
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"

        fun restBaseUrl(supabaseUrl: String): String {
            val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
            val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaAssetExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindMediaAssetExtraSync(impl: MediaAssetExtraSync): ExtraSync
}
