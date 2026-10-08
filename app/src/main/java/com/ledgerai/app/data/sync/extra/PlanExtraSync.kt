package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.PlanBlockDao
import com.ledgerai.app.data.local.room.PlanBlockEntity
import com.ledgerai.app.data.local.room.StudyPlanDao
import com.ledgerai.app.data.local.room.StudyPlanEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
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
 * Room ↔ PostgREST sync for study plans and the blocks that belong to them.
 * Plans are pulled and pushed before blocks so a block can resolve [source_plan_id].
 */
@Singleton
class PlanExtraSync @Inject constructor(
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
    private val studyPlanDao: StudyPlanDao,
    private val planBlockDao: PlanBlockDao,
) : ExtraSync {

    private val api: PlanTablesApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(PlanTablesApi::class.java)

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String,
    ) {
        pullStudyPlans(bearer, apiKey, filter)
        pullPlanBlocks(bearer, apiKey, filter)
        pushStudyPlans(bearer, apiKey, userId, sinceMs)
        pushPlanBlocks(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pullStudyPlans(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset ->
            api.pullStudyPlans(bearer, apiKey, filter, offset = offset)
        }.forEach { remote ->
            val local = studyPlanDao.getByRemoteId(remote.id)
            val entity = remote.toEntity(localId = local?.id ?: 0L)
            if (local == null) {
                studyPlanDao.insert(entity)
            } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                studyPlanDao.update(entity)
            }
        }
    }

    private suspend fun pullPlanBlocks(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset ->
            api.pullPlanBlocks(bearer, apiKey, filter, offset = offset)
        }.forEach { remote ->
            val local = planBlockDao.getByRemoteId(remote.id)
            val sourcePlanId = remote.sourcePlanId?.let { studyPlanDao.getByRemoteId(it)?.id }
            val entity = remote.toEntity(
                localId = local?.id ?: 0L,
                sourcePlanId = sourcePlanId,
                habitId = local?.habitId,
            )
            if (local == null) {
                planBlockDao.insert(entity)
            } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                planBlockDao.update(entity)
            }
        }
    }

    private suspend fun pushStudyPlans(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = studyPlanDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteStudyPlanDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                studyPlanDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertStudyPlans(bearer, apiKey, body = rows)
    }

    private suspend fun pushPlanBlocks(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = planBlockDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemotePlanBlockDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = UUID.randomUUID().toString()
                planBlockDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemote(rid, userId)
        }
        api.upsertPlanBlocks(bearer, apiKey, body = rows)
    }

    private suspend fun <T> forEachPage(fetch: suspend (offset: Int) -> List<T>): List<T> {
        val all = ArrayList<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            all += page
            if (page.size < PlanTablesApi.PULL_PAGE_SIZE) break
            offset += page.size
        }
        return all
    }

    private fun StudyPlanEntity.toRemote(remoteId: String, userId: String): RemoteStudyPlanDto =
        RemoteStudyPlanDto(
            id = remoteId,
            userId = userId,
            topic = topic,
            hoursTotal = hoursTotal,
            deadline = deadline?.let(SyncTime::dateToString),
            sessionLenMinutes = sessionLenMinutes,
            status = status,
            updatedAt = SyncTime.millisToIso(updatedAt),
            deletedAt = deletedAt?.let(SyncTime::millisToIso),
        )

    private fun RemoteStudyPlanDto.toEntity(localId: Long): StudyPlanEntity =
        StudyPlanEntity(
            id = localId,
            remoteId = id,
            userId = userId,
            topic = topic,
            hoursTotal = hoursTotal,
            deadline = SyncTime.optionalDate(deadline),
            sessionLenMinutes = sessionLenMinutes,
            status = status.ifBlank { "active" },
            updatedAt = SyncTime.isoToMillis(updatedAt),
            deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0L } },
        )

    /** Habit uuid is left null; habits sync in another class. */
    private suspend fun PlanBlockEntity.toRemote(remoteId: String, userId: String): RemotePlanBlockDto =
        RemotePlanBlockDto(
            id = remoteId,
            userId = userId,
            kind = kind.name,
            title = title,
            topic = topic,
            startAt = SyncTime.floatingToString(startAt),
            endAt = SyncTime.floatingToString(endAt),
            status = status.name,
            sourcePlanId = sourcePlanId?.let { studyPlanDao.getById(it)?.remoteId },
            habitId = null,
            actualMinutes = actualMinutes,
            updatedAt = SyncTime.millisToIso(updatedAt),
            deletedAt = deletedAt?.let(SyncTime::millisToIso),
        )

    private fun RemotePlanBlockDto.toEntity(
        localId: Long,
        sourcePlanId: Long?,
        habitId: Long?,
    ): PlanBlockEntity {
        val start = SyncTime.isoToDateTime(startAt) ?: LocalDateTime.now()
        return PlanBlockEntity(
            id = localId,
            remoteId = id,
            userId = userId,
            kind = enumOr(kind, PlanBlockKind.STUDY),
            title = title,
            topic = topic,
            startAt = start,
            endAt = SyncTime.isoToDateTime(endAt)?.takeIf { !it.isBefore(start) } ?: start,
            status = enumOr(status, PlanBlockStatus.SCHEDULED),
            sourcePlanId = sourcePlanId,
            habitId = habitId,
            actualMinutes = actualMinutes,
            updatedAt = SyncTime.isoToMillis(updatedAt),
            deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0L } },
        )
    }
}

internal interface PlanTablesApi {
    @GET("study_plans")
    suspend fun pullStudyPlans(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteStudyPlanDto>

    @POST("study_plans")
    suspend fun upsertStudyPlans(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteStudyPlanDto>,
    ): List<RemoteStudyPlanDto>

    @GET("plan_blocks")
    suspend fun pullPlanBlocks(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = PULL_ORDER,
        @Query("limit") limit: Int = PULL_PAGE_SIZE,
        @Query("offset") offset: Int = 0,
    ): List<RemotePlanBlockDto>

    @POST("plan_blocks")
    suspend fun upsertPlanBlocks(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = UPSERT_PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemotePlanBlockDto>,
    ): List<RemotePlanBlockDto>

    companion object {
        const val PULL_ORDER = "updated_at.asc,id.asc"
        const val PULL_PAGE_SIZE = 500
        const val UPSERT_PREFER = "resolution=merge-duplicates,return=representation"
    }
}

internal data class RemoteStudyPlanDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("topic") val topic: String = "",
    @SerializedName("hours_total") val hoursTotal: Double = 0.0,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("session_len_minutes") val sessionLenMinutes: Int = 50,
    @SerializedName("status") val status: String = "active",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

internal data class RemotePlanBlockDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("kind") val kind: String = PlanBlockKind.STUDY.name,
    @SerializedName("title") val title: String = "",
    @SerializedName("topic") val topic: String = "",
    @SerializedName("start_at") val startAt: String? = null,
    @SerializedName("end_at") val endAt: String? = null,
    @SerializedName("status") val status: String = PlanBlockStatus.SCHEDULED.name,
    @SerializedName("source_plan_id") val sourcePlanId: String? = null,
    @SerializedName("habit_id") val habitId: String? = null,
    @SerializedName("actual_minutes") val actualMinutes: Int? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
    if (raw.isNullOrBlank()) return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}

/** Retrofit requires a non-empty absolute base URL even when sync is disabled. */
private fun restBaseUrl(supabaseUrl: String): String {
    val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
    val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlanExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindPlanExtraSync(impl: PlanExtraSync): ExtraSync
}
