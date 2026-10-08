package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.FocusSessionDao
import com.ledgerai.app.data.local.room.FocusSessionEntity
import com.ledgerai.app.data.local.room.NudgeProposalDao
import com.ledgerai.app.data.local.room.NudgeProposalEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.NudgeProposalState
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
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room `focus_sessions` and `nudge_proposals` ↔ Supabase.
 * [FocusSessionEntity.blockId] and [NudgeProposalEntity.noteId] are device-local longs.
 * They travel as `block_local_id` and `note_local_id` and are written back unchanged on pull.
 * Location points and visits are not synced.
 */
@Singleton
class FocusNudgeExtraSync @Inject constructor(
    private val focusDao: FocusSessionDao,
    private val nudgeDao: NudgeProposalDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient
) : ExtraSync {

    private val api: FocusNudgeApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(FocusNudgeApi::class.java)

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String
    ) {
        pullFocus(bearer, apiKey, filter)
        pullNudges(bearer, apiKey, filter)
        pushFocus(bearer, apiKey, userId, sinceMs)
        pushNudges(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pullFocus(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pullFocus(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote -> applyFocus(remote) }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun applyFocus(remote: RemoteFocusSessionDto) {
        val local = focusDao.getByRemoteId(remote.id)
        if (local == null) {
            focusDao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            focusDao.update(remote.toEntity(localId = local.id))
        }
    }

    private suspend fun pullNudges(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pullNudges(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote -> applyNudge(remote) }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun applyNudge(remote: RemoteNudgeProposalDto) {
        val local = nudgeDao.getByRemoteId(remote.id)
        if (local == null) {
            nudgeDao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            nudgeDao.update(remote.toEntity(localId = local.id))
        }
    }

    private suspend fun pushFocus(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = focusDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteFocusSessionDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                focusDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertFocus(bearer, apiKey, body = rows)
    }

    private suspend fun pushNudges(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = nudgeDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteNudgeProposalDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                nudgeDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertNudges(bearer, apiKey, body = rows)
    }

    private interface FocusNudgeApi {
        @GET("focus_sessions")
        suspend fun pullFocus(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteFocusSessionDto>

        @POST("focus_sessions")
        suspend fun upsertFocus(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteFocusSessionDto>
        ): List<RemoteFocusSessionDto>

        @GET("nudge_proposals")
        suspend fun pullNudges(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteNudgeProposalDto>

        @POST("nudge_proposals")
        suspend fun upsertNudges(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteNudgeProposalDto>
        ): List<RemoteNudgeProposalDto>
    }

    private data class RemoteFocusSessionDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("block_local_id") val blockLocalId: Long = 0,
        @SerializedName("started_at") val startedAt: String = "",
        @SerializedName("ended_at") val endedAt: String? = null,
        @SerializedName("preset") val preset: String = "deep",
        @SerializedName("updated_at") val updatedAt: String? = null
    )

    private data class RemoteNudgeProposalDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("note_local_id") val noteLocalId: Long = 0,
        @SerializedName("message") val message: String = "",
        @SerializedName("suggested_at") val suggestedAt: String = "",
        @SerializedName("reason") val reason: String = "",
        @SerializedName("state") val state: String = NudgeProposalState.PENDING.name,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private fun FocusSessionEntity.toRemote(remoteId: String, userId: String) = RemoteFocusSessionDto(
        id = remoteId,
        userId = userId,
        blockLocalId = blockId,
        startedAt = SyncTime.floatingToString(startedAt),
        endedAt = endedAt?.let(SyncTime::floatingToString),
        preset = preset,
        updatedAt = SyncTime.millisToIso(updatedAt)
    )

    private fun RemoteFocusSessionDto.toEntity(localId: Long = 0) = FocusSessionEntity(
        id = localId,
        blockId = blockLocalId,
        startedAt = SyncTime.isoToDateTime(startedAt) ?: LocalDateTime.now(),
        endedAt = SyncTime.isoToDateTime(endedAt),
        preset = preset.ifBlank { "deep" },
        remoteId = id,
        userId = userId,
        updatedAt = SyncTime.isoToMillis(updatedAt)
    )

    private fun NudgeProposalEntity.toRemote(remoteId: String, userId: String) = RemoteNudgeProposalDto(
        id = remoteId,
        userId = userId,
        noteLocalId = noteId,
        message = message,
        suggestedAt = SyncTime.floatingToString(suggestedAt),
        reason = reason,
        state = state.name,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    private fun RemoteNudgeProposalDto.toEntity(localId: Long = 0) = NudgeProposalEntity(
        id = localId,
        noteId = noteLocalId,
        message = message,
        suggestedAt = SyncTime.isoToDateTime(suggestedAt) ?: LocalDateTime.now(),
        reason = reason,
        state = runCatching { NudgeProposalState.valueOf(state) }
            .getOrDefault(NudgeProposalState.PENDING),
        remoteId = id,
        userId = userId,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
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
abstract class FocusNudgeExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindFocusNudgeExtraSync(impl: FocusNudgeExtraSync): ExtraSync
}
