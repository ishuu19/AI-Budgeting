package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.VoiceHistoryDao
import com.ledgerai.app.data.local.room.VoiceHistoryEntity
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room `voice_history` ↔ Supabase `public.voice_history`.
 * [VoiceHistoryEntity.linkedItemId] is a local row id and is never sent.
 * A pull that updates an existing row keeps that local id.
 */
@Singleton
class VoiceHistoryExtraSync @Inject constructor(
    private val dao: VoiceHistoryDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient
) : ExtraSync {

    private val api: VoiceHistoryApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(VoiceHistoryApi::class.java)

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String
    ) {
        pull(bearer, apiKey, filter)
        push(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pull(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pull(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote -> applyRemote(remote) }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun applyRemote(remote: RemoteVoiceHistoryDto) {
        val local = dao.getByRemoteId(remote.id)
        if (local == null) {
            dao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            dao.update(remote.toEntity(localId = local.id).copy(linkedItemId = local.linkedItemId))
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = dao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteVoiceHistoryDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                dao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsert(bearer, apiKey, body = rows)
    }

    private interface VoiceHistoryApi {
        @GET("voice_history")
        suspend fun pull(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteVoiceHistoryDto>

        @POST("voice_history")
        suspend fun upsert(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteVoiceHistoryDto>
        ): List<RemoteVoiceHistoryDto>
    }

    private data class RemoteVoiceHistoryDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("transcript") val transcript: String = "",
        @SerializedName("result_kind") val resultKind: String = "",
        @SerializedName("result_summary") val resultSummary: String = "",
        @SerializedName("created_at") val createdAt: String? = null,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private fun VoiceHistoryEntity.toRemote(remoteId: String, userId: String) = RemoteVoiceHistoryDto(
        id = remoteId,
        userId = userId,
        transcript = transcript,
        resultKind = resultKind,
        resultSummary = resultSummary,
        createdAt = SyncTime.millisToIso(createdAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    private fun RemoteVoiceHistoryDto.toEntity(localId: Long = 0) = VoiceHistoryEntity(
        id = localId,
        transcript = transcript,
        resultKind = resultKind,
        linkedItemId = null,
        resultSummary = resultSummary,
        createdAt = SyncTime.isoToMillis(createdAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
        remoteId = id,
        userId = userId,
        updatedAt = SyncTime.isoToMillis(updatedAt)
    )

    private companion object {
        const val PAGE_SIZE = 500
        const val PULL_ORDER = "updated_at.asc,id.asc"
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"

        /** Retrofit requires a non-empty absolute base URL even when sync is disabled. */
        fun restBaseUrl(supabaseUrl: String): String {
            val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
            val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceHistoryExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindVoiceHistoryExtraSync(impl: VoiceHistoryExtraSync): ExtraSync
}
