package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.local.room.JobApplicationDao
import com.ledgerai.app.data.local.room.JobApplicationEntity
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.model.JobApplicationStatus
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
 * Room `job_applications` ↔ Supabase `public.job_applications`.
 * [JobApplicationStatus] is stored as the enum name.
 * A pull that updates an existing row keeps that local id.
 */
@Singleton
class JobExtraSync @Inject constructor(
    private val dao: JobApplicationDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient
) : ExtraSync {

    private val api: JobApplicationApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(JobApplicationApi::class.java)

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

    private suspend fun applyRemote(remote: RemoteJobApplicationDto) {
        val local = dao.getByRemoteId(remote.id)
        if (local == null) {
            dao.insert(remote.toEntity())
        } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
            dao.update(
                remote.toEntity(localId = local.id).copy(
                    location = local.location,
                    extraDates = local.extraDates
                )
            )
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val locals = dao.listForSync(sinceMs)
        if (locals.isEmpty()) return
        val rows = ArrayList<RemoteJobApplicationDto>(locals.size)
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

    private interface JobApplicationApi {
        @GET("job_applications")
        suspend fun pull(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0
        ): List<RemoteJobApplicationDto>

        @POST("job_applications")
        suspend fun upsert(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteJobApplicationDto>
        ): List<RemoteJobApplicationDto>
    }

    private data class RemoteJobApplicationDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("company") val company: String = "",
        @SerializedName("title") val title: String = "",
        @SerializedName("url") val url: String = "",
        @SerializedName("source") val source: String = "",
        @SerializedName("status") val status: String = JobApplicationStatus.APPLIED.name,
        @SerializedName("applied_on") val appliedOn: String = "",
        @SerializedName("follow_up_on") val followUpOn: String? = null,
        @SerializedName("notes") val notes: String = "",
        @SerializedName("contact") val contact: String = "",
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null
    )

    private fun JobApplicationEntity.toRemote(remoteId: String, userId: String) = RemoteJobApplicationDto(
        id = remoteId,
        userId = userId,
        company = company,
        title = title,
        url = url,
        source = source,
        status = status.name,
        appliedOn = SyncTime.dateToString(appliedOn),
        followUpOn = followUpOn?.let(SyncTime::dateToString),
        notes = notes,
        contact = contact,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

    private fun RemoteJobApplicationDto.toEntity(localId: Long = 0) = JobApplicationEntity(
        id = localId,
        company = company,
        title = title,
        url = url,
        source = source,
        status = runCatching { JobApplicationStatus.valueOf(status) }
            .getOrDefault(JobApplicationStatus.APPLIED),
        appliedOn = SyncTime.stringToDate(appliedOn),
        followUpOn = SyncTime.optionalDate(followUpOn),
        notes = notes,
        contact = contact,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
        remoteId = id,
        userId = userId
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
abstract class JobExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindJobExtraSync(impl: JobExtraSync): ExtraSync
}
