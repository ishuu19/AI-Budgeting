package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.ActivityEntryDao
import com.ledgerai.app.data.local.room.ActivityEntryEntity
import com.ledgerai.app.data.local.room.CheckinWindowDao
import com.ledgerai.app.data.local.room.CheckinWindowEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.ActivityEntrySource
import com.ledgerai.app.domain.model.CheckinWindowState
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
 * Room `activity_entries` and `checkin_windows` ↔ Supabase.
 * [ActivityEntryEntity.visitId] is a local place visit and is never sent.
 * A pull inserts with a null visit id and keeps a non-null local visit id.
 */
@Singleton
class LifeLogExtraSync @Inject constructor(
    private val activityEntryDao: ActivityEntryDao,
    private val checkinWindowDao: CheckinWindowDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient
) : ExtraSync {

    private val api: LifeLogApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(LifeLogApi::class.java)

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String
    ) {
        pullEntries(bearer, apiKey, filter)
        pullWindows(bearer, apiKey, filter)
        pushEntries(bearer, apiKey, userId, sinceMs)
        pushWindows(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pullEntries(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pullEntries(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote -> applyEntry(remote) }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun pullWindows(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pullWindows(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote -> applyWindow(remote) }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun applyEntry(remote: RemoteActivityEntryDto) {
        val local = activityEntryDao.getByRemoteId(remote.id)
        if (local == null) {
            activityEntryDao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            activityEntryDao.update(remote.toEntity(localId = local.id).copy(visitId = local.visitId))
        }
    }

    private suspend fun applyWindow(remote: RemoteCheckinWindowDto) {
        val local = checkinWindowDao.getByRemoteId(remote.id)
        if (local == null) {
            checkinWindowDao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            checkinWindowDao.update(remote.toEntity(localId = local.id))
        }
    }

    private suspend fun pushEntries(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = activityEntryDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteActivityEntryDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                activityEntryDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertEntries(bearer, apiKey, body = rows)
    }

    private suspend fun pushWindows(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = checkinWindowDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteCheckinWindowDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                checkinWindowDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertWindows(bearer, apiKey, body = rows)
    }

    private interface LifeLogApi {
        @GET("activity_entries")
        suspend fun pullEntries(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteActivityEntryDto>

        @POST("activity_entries")
        suspend fun upsertEntries(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteActivityEntryDto>
        ): List<RemoteActivityEntryDto>

        @GET("checkin_windows")
        suspend fun pullWindows(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteCheckinWindowDto>

        @POST("checkin_windows")
        suspend fun upsertWindows(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteCheckinWindowDto>
        ): List<RemoteCheckinWindowDto>
    }

    private data class RemoteActivityEntryDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("start_at") val startAt: String = "",
        @SerializedName("end_at") val endAt: String = "",
        @SerializedName("text") val text: String = "",
        @SerializedName("source") val source: String = ActivityEntrySource.MANUAL.name,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private data class RemoteCheckinWindowDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("start_at") val startAt: String = "",
        @SerializedName("end_at") val endAt: String = "",
        @SerializedName("state") val state: String = CheckinWindowState.PENDING.name,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private fun ActivityEntryEntity.toRemote(remoteId: String, userId: String) = RemoteActivityEntryDto(
        id = remoteId,
        userId = userId,
        startAt = SyncTime.floatingToString(startAt),
        endAt = SyncTime.floatingToString(endAt),
        text = text,
        source = source.name,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    private fun RemoteActivityEntryDto.toEntity(localId: Long = 0): ActivityEntryEntity {
        val start = SyncTime.isoToDateTime(startAt) ?: LocalDateTime.now()
        return ActivityEntryEntity(
            id = localId,
            startAt = start,
            endAt = SyncTime.isoToDateTime(endAt)?.takeIf { !it.isBefore(start) } ?: start,
            text = text,
            source = runCatching { ActivityEntrySource.valueOf(source) }
                .getOrDefault(ActivityEntrySource.MANUAL),
            visitId = null,
            remoteId = id,
            userId = userId,
            updatedAt = SyncTime.isoToMillis(updatedAt),
            deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
        )
    }

    private fun CheckinWindowEntity.toRemote(remoteId: String, userId: String) = RemoteCheckinWindowDto(
        id = remoteId,
        userId = userId,
        startAt = SyncTime.floatingToString(startAt),
        endAt = SyncTime.floatingToString(endAt),
        state = state.name,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    private fun RemoteCheckinWindowDto.toEntity(localId: Long = 0): CheckinWindowEntity {
        val start = SyncTime.isoToDateTime(startAt) ?: LocalDateTime.now()
        return CheckinWindowEntity(
            id = localId,
            startAt = start,
            endAt = SyncTime.isoToDateTime(endAt)?.takeIf { !it.isBefore(start) } ?: start,
            state = runCatching { CheckinWindowState.valueOf(state) }
                .getOrDefault(CheckinWindowState.PENDING),
            remoteId = id,
            userId = userId,
            updatedAt = SyncTime.isoToMillis(updatedAt),
            deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
        )
    }

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
abstract class LifeLogExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindLifeLogExtraSync(impl: LifeLogExtraSync): ExtraSync
}
