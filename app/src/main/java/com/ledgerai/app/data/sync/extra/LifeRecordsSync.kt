package com.ledgerai.app.data.sync.extra

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.achievements.AchievementEntity
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayEntity
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.dates.TripEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.data.vault.FolderDao
import com.ledgerai.app.data.vault.FolderEntity
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishEntity
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
import retrofit2.http.Url
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Room <-> Supabase for the rebuild's records: folders, birthdays, trips, achievements, wishes.
 * The primary key is the server uuid, so there is no separate remoteId.
 *
 * A table that does not exist on the server yet (supabase/planned 023 not applied) answers with an
 * error. That is logged and skipped so it can never fail the core money and calendar sync.
 * Passwords, app usage and reminders have no sync handler on purpose: they never leave the phone.
 */
abstract class RowSync<E : Any>(
    gson: Gson,
    client: OkHttpClient,
    private val table: String,
) : ExtraSync {

    private val api: RowApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        // Nulls must be sent: a batch whose rows have different keys is refused (PGRST102), and a cleared
        // field has to reach the server as null, not be left out.
        .addConverterFactory(GsonConverterFactory.create(gson.newBuilder().serializeNulls().create()))
        .build()
        .create(RowApi::class.java)

    protected abstract suspend fun local(id: String): E?
    protected abstract suspend fun saveLocal(row: E)
    protected abstract suspend fun changedSince(sinceMs: Long): List<E>
    protected abstract fun updatedAt(row: E): Long
    protected abstract fun toJson(row: E, userId: String): JsonObject
    protected abstract fun fromJson(json: JsonObject, existing: E?): E?

    override suspend fun sync(bearer: String, apiKey: String, userId: String, sinceMs: Long, filter: String) {
        try {
            pull(bearer, apiKey, filter)
            push(bearer, apiKey, userId, sinceMs)
        } catch (e: retrofit2.HttpException) {
            // Only a table that is not on the server yet is skipped. Anything else (offline, expired
            // sign-in, server error) is rethrown so the sync cursor stays put and the next run retries.
            if (e.code() == 404) {
                Log.w(TAG, "$table is not on the server yet: skipped")
            } else {
                Log.e(TAG, "$table sync failed: HTTP ${e.code()} ${e.response()?.errorBody()?.string()?.take(300)}")
                throw e
            }
        }
    }

    private suspend fun pull(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pull(table, bearer, apiKey, filter, offset = offset)
            for (json in page) {
                val id = json.str("id") ?: continue
                val existing = local(id)
                val remoteUpdated = SyncTime.isoToMillis(json.str("updated_at"))
                if (existing == null || SyncTime.remoteWins(remoteUpdated, updatedAt(existing))) {
                    fromJson(json, existing)?.let { saveLocal(it) }
                }
            }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val rows = changedSince(sinceMs).map { toJson(it, userId) }
        for (chunk in rows.chunked(PUSH_CHUNK)) {
            val response = api.upsert(table, bearer, apiKey, body = chunk)
            if (response.isSuccessful) continue
            // A 4xx means the server refused something in this chunk (a length or format check).
            // Retry row by row so one bad row cannot hold back the others; a refused row is logged.
            // Anything else (401, 5xx) is thrown so the run is retried later.
            if (response.code() !in 400..499 || response.code() == 401 || response.code() == 404) throw retrofit2.HttpException(response)
            for (row in chunk) {
                val one = api.upsert(table, bearer, apiKey, body = listOf(row))
                if (one.isSuccessful) continue
                if (one.code() == 401 || one.code() == 404 || one.code() !in 400..499) throw retrofit2.HttpException(one)
                Log.e(TAG, "$table row ${row.str("id")} was refused by the server (${one.code()}): ${one.errorBody()?.string()?.take(200)}")
            }
        }
    }

    private interface RowApi {
        @GET
        suspend fun pull(
            @Url table: String,
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = "updated_at.asc,id.asc",
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0,
        ): List<JsonObject>

        @POST
        suspend fun upsert(
            @Url table: String,
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<JsonObject>,
        ): retrofit2.Response<Unit>
    }

    protected companion object {
        const val TAG = "RowSync"
        const val PAGE_SIZE = 500
        const val PUSH_CHUNK = 200

        fun restBaseUrl(supabaseUrl: String): String {
            val trimmed = supabaseUrl.trim().ifBlank { "https://localhost/" }
            val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return if (withSlash.contains("/rest/v1")) withSlash else "${withSlash}rest/v1/"
        }

        fun JsonObject.str(name: String): String? =
            get(name)?.takeIf { !it.isJsonNull }?.asString

        fun JsonObject.int(name: String): Int? =
            get(name)?.takeIf { !it.isJsonNull }?.asInt

        fun JsonObject.dbl(name: String): Double? =
            get(name)?.takeIf { !it.isJsonNull }?.asDouble

        fun JsonObject.bool(name: String): Boolean? =
            get(name)?.takeIf { !it.isJsonNull }?.asBoolean

        /** Writes an explicit null so a cleared field is cleared on the server too. */
        fun JsonObject.put(name: String, value: Any?): JsonObject {
            when (value) {
                null -> add(name, JsonNull.INSTANCE)
                is Boolean -> addProperty(name, value)
                is Number -> addProperty(name, value)
                else -> addProperty(name, value.toString())
            }
            return this
        }

        /** Goal and transaction ids are device row ids until those tables sync; only real uuids go up. */
        fun isUuid(value: String): Boolean = runCatching { java.util.UUID.fromString(value) }.isSuccess

        fun iso(ms: Long): String = SyncTime.millisToIso(ms)
        fun isoOrNull(ms: Long?): String? = ms?.let(::iso)
        fun msOrNull(value: String?): Long? = value?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    }
}

@Singleton
class FolderSync @Inject constructor(
    private val dao: FolderDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : RowSync<FolderEntity>(gson, client, "folders") {
    override suspend fun local(id: String) = dao.get(id)
    override suspend fun saveLocal(row: FolderEntity) = dao.upsert(row)
    override suspend fun changedSince(sinceMs: Long) = dao.listForSync(sinceMs)
    override fun updatedAt(row: FolderEntity) = row.updatedAt
    override fun toJson(row: FolderEntity, userId: String) = JsonObject()
        .put("id", row.id).put("user_id", userId).put("name", row.name)
        .put("parent_id", row.parentId).put("pinned", row.pinned).put("sort_order", row.sortOrder)
        .put("system_key", row.systemKey)
        .put("updated_at", iso(row.updatedAt)).put("deleted_at", isoOrNull(row.deletedAt))

    override fun fromJson(json: JsonObject, existing: FolderEntity?): FolderEntity? {
        val id = json.str("id") ?: return null
        return (existing ?: FolderEntity(id = id, name = "")).copy(
            userId = json.str("user_id") ?: existing?.userId,
            name = json.str("name") ?: existing?.name.orEmpty(),
            parentId = json.str("parent_id"),
            pinned = json.bool("pinned") ?: false,
            sortOrder = json.int("sort_order") ?: 0,
            systemKey = json.str("system_key"),
            updatedAt = SyncTime.isoToMillis(json.str("updated_at")),
            deletedAt = msOrNull(json.str("deleted_at")),
        )
    }
}

@Singleton
class BirthdaySync @Inject constructor(
    private val dao: BirthdayDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : RowSync<BirthdayEntity>(gson, client, "birthdays") {
    override suspend fun local(id: String) = dao.get(id)
    override suspend fun saveLocal(row: BirthdayEntity) = dao.upsert(row)
    override suspend fun changedSince(sinceMs: Long) = dao.listForSync(sinceMs)
    override fun updatedAt(row: BirthdayEntity) = row.updatedAt
    override fun toJson(row: BirthdayEntity, userId: String) = JsonObject()
        .put("id", row.id).put("user_id", userId).put("person_id", row.personId)
        .put("month", row.month).put("day", row.day).put("birth_year", row.birthYear)
        .put("celebrate_minute", row.celebrateMinute).put("note", row.note)
        .put("r_1mo", row.r1mo).put("r_2w", row.r2w).put("r_1w", row.r1w).put("r_3d", row.r3d)
        .put("r_1d", row.r1d).put("r_5h", row.r5h).put("r_30m", row.r30m).put("r_10m", row.r10m)
        .put("r_at", row.rAt)
        .put("updated_at", iso(row.updatedAt)).put("deleted_at", isoOrNull(row.deletedAt))

    override fun fromJson(json: JsonObject, existing: BirthdayEntity?): BirthdayEntity? {
        val id = json.str("id") ?: return null
        val person = json.str("person_id") ?: return null
        val month = json.int("month") ?: return null
        val day = json.int("day") ?: return null
        return BirthdayEntity(
            id = id,
            userId = json.str("user_id"),
            personId = person,
            month = month,
            day = day,
            birthYear = json.int("birth_year"),
            celebrateMinute = json.int("celebrate_minute") ?: 0,
            note = json.str("note").orEmpty(),
            r1mo = json.bool("r_1mo") ?: true,
            r2w = json.bool("r_2w") ?: true,
            r1w = json.bool("r_1w") ?: true,
            r3d = json.bool("r_3d") ?: true,
            r1d = json.bool("r_1d") ?: true,
            r5h = json.bool("r_5h") ?: true,
            r30m = json.bool("r_30m") ?: true,
            r10m = json.bool("r_10m") ?: true,
            rAt = json.bool("r_at") ?: true,
            updatedAt = SyncTime.isoToMillis(json.str("updated_at")),
            deletedAt = msOrNull(json.str("deleted_at")),
        )
    }
}

@Singleton
class TripSync @Inject constructor(
    private val dao: TripDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : RowSync<TripEntity>(gson, client, "trips") {
    override suspend fun local(id: String) = dao.get(id)
    override suspend fun saveLocal(row: TripEntity) = dao.upsert(row)
    override suspend fun changedSince(sinceMs: Long) = dao.listForSync(sinceMs)
    override fun updatedAt(row: TripEntity) = row.updatedAt
    override fun toJson(row: TripEntity, userId: String) = JsonObject()
        .put("id", row.id).put("user_id", userId).put("name", row.name).put("place", row.place)
        .put("start_date", row.startDate.toString()).put("end_date", row.endDate.toString())
        .put("notes", row.notes).put("r_1w", row.r1w).put("r_1d", row.r1d)
        .put("visibility", row.visibility)
        .put("updated_at", iso(row.updatedAt)).put("deleted_at", isoOrNull(row.deletedAt))

    override fun fromJson(json: JsonObject, existing: TripEntity?): TripEntity? {
        val id = json.str("id") ?: return null
        val start = json.str("start_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val end = json.str("end_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return TripEntity(
            id = id,
            userId = json.str("user_id"),
            name = json.str("name").orEmpty(),
            place = json.str("place").orEmpty(),
            startDate = start,
            endDate = if (end.isBefore(start)) start else end,
            notes = json.str("notes").orEmpty(),
            r1w = json.bool("r_1w") ?: true,
            r1d = json.bool("r_1d") ?: true,
            visibility = json.str("visibility") ?: "private",
            updatedAt = SyncTime.isoToMillis(json.str("updated_at")),
            deletedAt = msOrNull(json.str("deleted_at")),
        )
    }
}

@Singleton
class AchievementSync @Inject constructor(
    private val dao: AchievementDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : RowSync<AchievementEntity>(gson, client, "achievements") {
    override suspend fun local(id: String) = dao.get(id)
    override suspend fun saveLocal(row: AchievementEntity) = dao.upsert(row)
    override suspend fun changedSince(sinceMs: Long) = dao.listForSync(sinceMs)
    override fun updatedAt(row: AchievementEntity) = row.updatedAt
    // sourceTaskId is a device-local row id, so it stays on the phone.
    override fun toJson(row: AchievementEntity, userId: String) = JsonObject()
        .put("id", row.id).put("user_id", userId).put("title", row.title)
        .put("happened_on", row.happenedOn.toString()).put("note", row.note)
        .put("updated_at", iso(row.updatedAt)).put("deleted_at", isoOrNull(row.deletedAt))

    override fun fromJson(json: JsonObject, existing: AchievementEntity?): AchievementEntity? {
        val id = json.str("id") ?: return null
        val on = json.str("happened_on")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return AchievementEntity(
            id = id,
            userId = json.str("user_id"),
            title = json.str("title").orEmpty(),
            happenedOn = on,
            note = json.str("note").orEmpty(),
            sourceTaskId = existing?.sourceTaskId,
            updatedAt = SyncTime.isoToMillis(json.str("updated_at")),
            deletedAt = msOrNull(json.str("deleted_at")),
        )
    }
}

@Singleton
class WishSync @Inject constructor(
    private val dao: WishDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : RowSync<WishEntity>(gson, client, "wishes") {
    override suspend fun local(id: String) = dao.get(id)
    override suspend fun saveLocal(row: WishEntity) = dao.upsert(row)
    override suspend fun changedSince(sinceMs: Long) = dao.listForSync(sinceMs)
    override fun updatedAt(row: WishEntity) = row.updatedAt
    override fun toJson(row: WishEntity, userId: String) = JsonObject()
        .put("id", row.id).put("user_id", userId).put("name", row.name).put("amount", row.amount)
        .put("priority", row.priority).put("wanted_by", row.wantedBy).put("link", row.link)
        .put("note", row.note).put("status", row.status)
        .put("goal_id", row.goalId?.takeIf(::isUuid)).put("transaction_id", row.transactionId?.takeIf(::isUuid))
        .put("visibility", row.visibility)
        .put("created_at", iso(row.createdAt))
        .put("updated_at", iso(row.updatedAt)).put("deleted_at", isoOrNull(row.deletedAt))

    override fun fromJson(json: JsonObject, existing: WishEntity?): WishEntity? {
        val id = json.str("id") ?: return null
        return WishEntity(
            id = id,
            userId = json.str("user_id"),
            name = json.str("name").orEmpty(),
            amount = json.dbl("amount"),
            priority = (json.int("priority") ?: 2).coerceIn(1, 3),
            wantedBy = json.str("wanted_by"),
            link = json.str("link"),
            note = json.str("note").orEmpty(),
            status = json.str("status") ?: "WANT",
            goalId = json.str("goal_id"),
            transactionId = json.str("transaction_id"),
            visibility = json.str("visibility") ?: "private",
            createdAt = SyncTime.isoToMillis(json.str("created_at")),
            updatedAt = SyncTime.isoToMillis(json.str("updated_at")),
            deletedAt = msOrNull(json.str("deleted_at")),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LifeRecordsSyncModule {
    @Binds @IntoSet abstract fun folders(impl: FolderSync): ExtraSync
    @Binds @IntoSet abstract fun birthdays(impl: BirthdaySync): ExtraSync
    @Binds @IntoSet abstract fun trips(impl: TripSync): ExtraSync
    @Binds @IntoSet abstract fun achievements(impl: AchievementSync): ExtraSync
    @Binds @IntoSet abstract fun wishes(impl: WishSync): ExtraSync
}
