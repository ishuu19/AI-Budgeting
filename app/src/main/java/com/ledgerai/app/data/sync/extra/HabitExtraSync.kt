package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.HabitDao
import com.ledgerai.app.data.local.room.HabitEntity
import com.ledgerai.app.data.local.room.HabitLogDao
import com.ledgerai.app.data.local.room.HabitLogEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.HabitCategory
import com.ledgerai.app.domain.model.HabitOutcome
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
 * Room ↔ PostgREST sync for habits and habit logs.
 * Habits are pushed before logs so each log can store the habit's remote uuid.
 */
@Singleton
class HabitExtraSync @Inject constructor(
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
) : ExtraSync {

    private val api: HabitPostgrestApi by lazy {
        Retrofit.Builder()
            .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HabitPostgrestApi::class.java)
    }

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String,
    ) {
        if (BuildConfig.SUPABASE_URL.isBlank()) return
        pullHabits(bearer, apiKey, filter)
        pullLogs(bearer, apiKey, filter)
        pushHabits(bearer, apiKey, userId, sinceMs)
        pushLogs(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pullHabits(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset -> api.pullHabits(bearer, apiKey, filter, offset = offset) }.forEach { remote ->
            val local = habitDao.getByRemoteId(remote.id)
            if (local == null) {
                habitDao.insert(remote.toEntity(localId = 0L))
            } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                habitDao.update(remote.toEntity(localId = local.id))
            }
        }
    }

    private suspend fun pullLogs(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset -> api.pullLogs(bearer, apiKey, filter, offset = offset) }.forEach { remote ->
            val habit = habitDao.getByRemoteId(remote.habitId) ?: return@forEach
            val local = habitLogDao.getByRemoteId(remote.id)
            if (local == null) {
                habitLogDao.insert(remote.toEntity(localId = 0L, localHabitId = habit.id))
            } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                habitLogDao.update(remote.toEntity(localId = local.id, localHabitId = habit.id))
            }
        }
    }

    private suspend fun pushHabits(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = habitDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteHabitDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: UUID.randomUUID().toString().also { id ->
                habitDao.update(local.copy(remoteId = id, userId = userId))
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertHabits(bearer, apiKey, body = rows)
    }

    private suspend fun pushLogs(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = habitLogDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteHabitLogDto>()
        for (local in locals) {
            val habitRemoteId = habitDao.getById(local.habitId)?.remoteId ?: continue
            val rid = local.remoteId ?: UUID.randomUUID().toString().also { id ->
                habitLogDao.update(local.copy(remoteId = id, userId = userId))
            }
            rows += local.toRemoteDto(rid, userId, habitRemoteId)
        }
        if (rows.isEmpty()) return
        api.upsertLogs(bearer, apiKey, body = rows)
    }

    private suspend fun <T> forEachPage(fetch: suspend (offset: Int) -> List<T>): List<T> {
        val all = ArrayList<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            all += page
            if (page.size < HabitPostgrestApi.PULL_PAGE_SIZE) break
            offset += page.size
        }
        return all
    }

    /** Retrofit requires a non-empty absolute base URL even when sync is disabled. */
    private fun restBaseUrl(supabaseUrl: String): String {
        val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HabitExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bind(impl: HabitExtraSync): ExtraSync
}

internal interface HabitPostgrestApi {
    @GET("habits")
    suspend fun pullHabits(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteHabitDto>

    @POST("habits")
    suspend fun upsertHabits(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteHabitDto>,
    ): List<RemoteHabitDto>

    @GET("habit_logs")
    suspend fun pullLogs(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteHabitLogDto>

    @POST("habit_logs")
    suspend fun upsertLogs(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteHabitLogDto>,
    ): List<RemoteHabitLogDto>

    companion object {
        const val PULL_ORDER = "updated_at.asc,id.asc"
        const val PULL_PAGE_SIZE = 500
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"
    }
}

internal data class RemoteHabitDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("category") val category: String = HabitCategory.CUSTOM.name,
    @SerializedName("days_mask") val daysMask: Int = 0,
    @SerializedName("start_time") val startTime: String = "00:00:00",
    @SerializedName("duration_minutes") val durationMinutes: Int = 30,
    @SerializedName("nudge_enabled") val nudgeEnabled: Boolean = true,
    @SerializedName("quiet_override") val quietOverride: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

internal data class RemoteHabitLogDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("habit_id") val habitId: String,
    @SerializedName("date") val date: String = "",
    @SerializedName("outcome") val outcome: String = HabitOutcome.DONE.name,
    @SerializedName("minutes") val minutes: Int? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

private fun HabitEntity.toRemoteDto(remoteId: String, userId: String): RemoteHabitDto =
    RemoteHabitDto(
        id = remoteId,
        userId = userId,
        title = title,
        category = category.name,
        daysMask = daysMask,
        startTime = SyncTime.timeToString(startTime),
        durationMinutes = durationMinutes,
        nudgeEnabled = nudgeEnabled,
        quietOverride = quietOverride,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso),
    )

private fun RemoteHabitDto.toEntity(localId: Long): HabitEntity =
    HabitEntity(
        id = localId,
        title = title,
        category = enumOr(category, HabitCategory.CUSTOM),
        daysMask = daysMask.coerceIn(0, 127),
        startTime = SyncTime.stringToTime(startTime),
        durationMinutes = durationMinutes.coerceAtLeast(1),
        nudgeEnabled = nudgeEnabled,
        quietOverride = quietOverride,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0L } },
        remoteId = id,
        userId = userId,
    )

private fun HabitLogEntity.toRemoteDto(
    remoteId: String,
    userId: String,
    remoteHabitId: String,
): RemoteHabitLogDto =
    RemoteHabitLogDto(
        id = remoteId,
        userId = userId,
        habitId = remoteHabitId,
        date = SyncTime.dateToString(date),
        outcome = outcome.name,
        minutes = minutes,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = null,
    )

private fun RemoteHabitLogDto.toEntity(localId: Long, localHabitId: Long): HabitLogEntity =
    HabitLogEntity(
        id = localId,
        habitId = localHabitId,
        date = SyncTime.stringToDate(date),
        outcome = enumOr(outcome, HabitOutcome.DONE),
        minutes = minutes,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        remoteId = id,
        userId = userId,
    )

private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
