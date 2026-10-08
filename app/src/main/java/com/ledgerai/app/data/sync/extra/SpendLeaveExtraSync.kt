package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.LeaveRuleDao
import com.ledgerai.app.data.local.room.LeaveRuleEntity
import com.ledgerai.app.data.local.room.SpendGuideDayDao
import com.ledgerai.app.data.local.room.SpendGuideDayEntity
import com.ledgerai.app.data.local.room.SpendSpeculationDao
import com.ledgerai.app.data.local.room.SpendSpeculationEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
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
 * Syncs spend speculations, daily spend-guide rows, and leave-by rules.
 * Bound into [ExtraSync] so [com.ledgerai.app.data.sync.SyncRepository] runs it
 * after the core money and calendar tables.
 *
 * Leave-rule `refId` is a local calendar-event id. The server stores that long
 * as `ref_local_id` and the pull writes the same long back; it is not resolved
 * to a remote event id.
 */
@Singleton
class SpendLeaveExtraSync @Inject constructor(
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
    private val speculationDao: SpendSpeculationDao,
    private val guideDayDao: SpendGuideDayDao,
    private val leaveRuleDao: LeaveRuleDao,
) : ExtraSync {

    private val api: SpendLeavePostgrest = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(SpendLeavePostgrest::class.java)

    override suspend fun sync(
        bearer: String,
        apiKey: String,
        userId: String,
        sinceMs: Long,
        filter: String,
    ) {
        pullSpeculations(bearer, apiKey, filter)
        pullGuideDays(bearer, apiKey, filter)
        pullLeaveRules(bearer, apiKey, filter)
        pushSpeculations(bearer, apiKey, userId, sinceMs)
        pushGuideDays(bearer, apiKey, userId, sinceMs)
        pushLeaveRules(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pullSpeculations(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset -> api.pullSpeculations(bearer, apiKey, filter, offset = offset) }
            .forEach { remote ->
                val local = speculationDao.getByRemoteId(remote.id)
                if (local == null) {
                    speculationDao.insert(remote.toEntity())
                } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                    speculationDao.update(remote.toEntity(localId = local.id))
                }
            }
    }

    private suspend fun pullGuideDays(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset -> api.pullGuideDays(bearer, apiKey, filter, offset = offset) }
            .forEach { remote ->
                val local = guideDayDao.getByRemoteId(remote.id)
                val incoming = remote.toEntity()
                if (local == null) {
                    guideDayDao.upsert(incoming)
                } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                    if (incoming.date == local.date) guideDayDao.update(incoming)
                    else guideDayDao.upsert(incoming)
                }
            }
    }

    private suspend fun pullLeaveRules(bearer: String, apiKey: String, filter: String) {
        forEachPage { offset -> api.pullLeaveRules(bearer, apiKey, filter, offset = offset) }
            .forEach { remote ->
                val local = leaveRuleDao.getByRemoteId(remote.id)
                if (local == null) {
                    leaveRuleDao.insert(remote.toEntity())
                } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                    leaveRuleDao.update(remote.toEntity(localId = local.id))
                }
            }
    }

    private suspend fun pushSpeculations(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = speculationDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteSpendSpeculationDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                speculationDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertSpeculations(bearer, apiKey, body = rows)
    }

    private suspend fun pushGuideDays(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = guideDayDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteSpendGuideDayDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                guideDayDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertGuideDays(bearer, apiKey, body = rows)
    }

    private suspend fun pushLeaveRules(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = leaveRuleDao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteLeaveRuleDto>(locals.size)
        for (local in locals) {
            val rid = local.remoteId ?: run {
                val id = newId()
                leaveRuleDao.update(local.copy(remoteId = id, userId = userId))
                id
            }
            rows += local.toRemoteDto(rid, userId)
        }
        api.upsertLeaveRules(bearer, apiKey, body = rows)
    }

    private suspend fun <T> forEachPage(fetch: suspend (offset: Int) -> List<T>): List<T> {
        val all = ArrayList<T>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            all += page
            if (page.size < PAGE) break
            offset += page.size
        }
        return all
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val PAGE = 500
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SpendLeaveExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindSpendLeaveExtraSync(impl: SpendLeaveExtraSync): ExtraSync
}

private interface SpendLeavePostgrest {

    @GET("spend_speculations")
    suspend fun pullSpeculations(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = ORDER,
        @Query("limit") limit: Int = PAGE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteSpendSpeculationDto>

    @POST("spend_speculations")
    suspend fun upsertSpeculations(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteSpendSpeculationDto>,
    ): List<RemoteSpendSpeculationDto>

    @GET("spend_guide_days")
    suspend fun pullGuideDays(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = ORDER,
        @Query("limit") limit: Int = PAGE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteSpendGuideDayDto>

    @POST("spend_guide_days")
    suspend fun upsertGuideDays(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteSpendGuideDayDto>,
    ): List<RemoteSpendGuideDayDto>

    @GET("leave_rules")
    suspend fun pullLeaveRules(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Query("updated_at") updatedAt: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = ORDER,
        @Query("limit") limit: Int = PAGE,
        @Query("offset") offset: Int = 0,
    ): List<RemoteLeaveRuleDto>

    @POST("leave_rules")
    suspend fun upsertLeaveRules(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = PREFER,
        @Query("on_conflict") onConflict: String = "id",
        @Body body: List<RemoteLeaveRuleDto>,
    ): List<RemoteLeaveRuleDto>

    companion object {
        const val PAGE = 500
        const val ORDER = "updated_at.asc,id.asc"
        const val PREFER = "resolution=merge-duplicates,return=representation"
    }
}

private data class RemoteSpendSpeculationDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("label") val label: String = "",
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("direction") val direction: String = "EXPENSE",
    @SerializedName("expected_date") val expectedDate: String = "",
    @SerializedName("confidence") val confidence: String = "MEDIUM",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

private data class RemoteSpendGuideDayDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("date") val date: String = "",
    @SerializedName("guide_amount") val guideAmount: Double = 0.0,
    @SerializedName("spent") val spent: Double = 0.0,
    @SerializedName("buffer") val buffer: Double = 0.0,
    @SerializedName("margin_used") val marginUsed: Double = 0.0,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

private data class RemoteLeaveRuleDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("ref_type") val refType: String = "CALENDAR_EVENT",
    @SerializedName("ref_local_id") val refLocalId: Long = 0L,
    @SerializedName("place_label") val placeLabel: String = "",
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lng") val lng: Double? = null,
    @SerializedName("travel_minutes") val travelMinutes: Int = 15,
    @SerializedName("buffer_minutes") val bufferMinutes: Int = 5,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

private fun SpendSpeculationEntity.toRemoteDto(remoteId: String, userId: String) =
    RemoteSpendSpeculationDto(
        id = remoteId,
        userId = userId,
        label = label,
        amount = amount,
        direction = direction.name,
        expectedDate = SyncTime.dateToString(expectedDate),
        confidence = confidence.name,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso),
    )

private fun RemoteSpendSpeculationDto.toEntity(localId: Long = 0L) = SpendSpeculationEntity(
    id = localId,
    label = label,
    amount = amount,
    direction = enumOr(direction, SpeculationDirection.EXPENSE),
    expectedDate = SyncTime.stringToDate(expectedDate),
    confidence = enumOr(confidence, SpeculationConfidence.MEDIUM),
    updatedAt = SyncTime.isoToMillis(updatedAt),
    deletedAt = deletedAt.toEpochMillisOrNull(),
    remoteId = id,
    userId = userId,
)

private fun SpendGuideDayEntity.toRemoteDto(remoteId: String, userId: String) =
    RemoteSpendGuideDayDto(
        id = remoteId,
        userId = userId,
        date = SyncTime.dateToString(date),
        guideAmount = guideAmount,
        spent = spent,
        buffer = buffer,
        marginUsed = marginUsed,
        updatedAt = SyncTime.millisToIso(updatedAt),
    )

private fun RemoteSpendGuideDayDto.toEntity() = SpendGuideDayEntity(
    date = SyncTime.stringToDate(date),
    guideAmount = guideAmount,
    spent = spent,
    buffer = buffer,
    marginUsed = marginUsed,
    remoteId = id,
    userId = userId,
    updatedAt = SyncTime.isoToMillis(updatedAt),
)

private fun LeaveRuleEntity.toRemoteDto(remoteId: String, userId: String) = RemoteLeaveRuleDto(
    id = remoteId,
    userId = userId,
    refType = refType.name,
    refLocalId = refId,
    placeLabel = placeLabel,
    lat = lat,
    lng = lng,
    travelMinutes = travelMinutes,
    bufferMinutes = bufferMinutes,
    enabled = enabled,
    updatedAt = SyncTime.millisToIso(updatedAt),
    deletedAt = deletedAt?.let(SyncTime::millisToIso),
)

private fun RemoteLeaveRuleDto.toEntity(localId: Long = 0L) = LeaveRuleEntity(
    id = localId,
    refType = enumOr(refType, LeaveRefType.CALENDAR_EVENT),
    refId = refLocalId,
    placeLabel = placeLabel,
    lat = lat,
    lng = lng,
    travelMinutes = travelMinutes,
    bufferMinutes = bufferMinutes,
    enabled = enabled,
    remoteId = id,
    userId = userId,
    updatedAt = SyncTime.isoToMillis(updatedAt),
    deletedAt = deletedAt.toEpochMillisOrNull(),
)

private fun String?.toEpochMillisOrNull(): Long? =
    this?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0L } }

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
