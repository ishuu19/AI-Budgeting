package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.data.local.room.NoteDao
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
 * Room `clips` <-> Supabase `public.clips`. Sensitive rows stay on the phone.
 * `noteId` is a local Long; the server column is the note's uuid.
 */
@Singleton
class ClipExtraSync @Inject constructor(
    private val dao: ClipDao,
    private val notes: NoteDao,
    gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : ExtraSync {

    private val api: ClipApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ClipApi::class.java)

    override suspend fun sync(bearer: String, apiKey: String, userId: String, sinceMs: Long, filter: String) {
        pull(bearer, apiKey, filter)
        push(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pull(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pull(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote ->
                if (remote.sensitive == true) return@forEach
                val local = dao.get(remote.id)
                val noteId = remote.noteId?.let { notes.getByRemoteId(it)?.id }
                if (local == null) {
                    dao.upsert(remote.toNewEntity(noteId))
                } else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) {
                    dao.upsert(remote.mergeInto(local, noteId))
                }
            }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val rows = dao.listForSync(sinceMs).mapNotNull { it.toRemote(userId) }
        if (rows.isEmpty()) return
        api.upsert(bearer, apiKey, body = rows)
    }

    private suspend fun ClipEntity.toRemote(owner: String): RemoteClipDto? {
        if (sensitive) return null
        val noteUuid = noteId?.let { notes.getById(it)?.remoteId }
        return RemoteClipDto(
            id = id,
            userId = owner,
            text = text,
            textHash = textHash,
            sourceKind = sourceKind,
            sourceApp = sourceApp,
            url = url,
            noteId = noteUuid,
            status = status,
            sensitive = false,
            aiKind = aiKind,
            aiJson = aiJson,
            filedType = filedType,
            filedId = filedId,
            editedText = editedText,
            pinned = pinned,
            pinOrder = pinOrder,
            prompt = prompt,
            windowTitle = windowTitle,
            threadId = threadId,
            messageId = messageId,
            capturedAt = SyncTime.millisToIso(capturedAt),
            updatedAt = SyncTime.millisToIso(updatedAt),
            deletedAt = deletedAt?.let(SyncTime::millisToIso),
        )
    }

    private fun RemoteClipDto.toNewEntity(noteId: Long?) = ClipEntity(
        id = id,
        remoteId = id,
        userId = userId,
        text = text,
        textHash = textHash,
        sourceKind = sourceKind,
        sourceApp = sourceApp,
        url = url,
        noteId = noteId,
        status = status,
        sensitive = false,
        aiState = "none",
        aiKind = aiKind,
        aiJson = aiJson,
        filedType = filedType,
        filedId = filedId,
        editedText = editedText,
        pinned = pinned == true,
        pinOrder = pinOrder,
        prompt = prompt,
        windowTitle = windowTitle,
        threadId = threadId,
        messageId = messageId,
        capturedAt = SyncTime.isoToMillis(capturedAt),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private fun RemoteClipDto.mergeInto(local: ClipEntity, noteId: Long?) = local.copy(
        remoteId = id,
        text = text,
        textHash = textHash,
        sourceKind = sourceKind,
        sourceApp = sourceApp,
        url = url,
        noteId = noteId ?: local.noteId,
        status = status,
        aiKind = aiKind ?: local.aiKind,
        aiJson = aiJson ?: local.aiJson,
        filedType = filedType ?: local.filedType,
        filedId = filedId ?: local.filedId,
        editedText = editedText ?: local.editedText,
        pinned = pinned ?: local.pinned,
        pinOrder = pinOrder ?: local.pinOrder,
        prompt = prompt ?: local.prompt,
        windowTitle = windowTitle ?: local.windowTitle,
        threadId = threadId ?: local.threadId,
        messageId = messageId ?: local.messageId,
        capturedAt = SyncTime.isoToMillis(capturedAt).takeIf { it > 0 } ?: local.capturedAt,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private interface ClipApi {
        @GET("clips")
        suspend fun pull(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0,
        ): List<RemoteClipDto>

        @POST("clips")
        suspend fun upsert(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteClipDto>,
        ): List<RemoteClipDto>
    }

    private data class RemoteClipDto(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("text") val text: String = "",
        @SerializedName("text_hash") val textHash: String = "",
        @SerializedName("source_kind") val sourceKind: String = "share",
        @SerializedName("source_app") val sourceApp: String? = null,
        @SerializedName("url") val url: String? = null,
        @SerializedName("note_id") val noteId: String? = null,
        @SerializedName("status") val status: String = "inbox",
        @SerializedName("sensitive") val sensitive: Boolean? = false,
        @SerializedName("ai_kind") val aiKind: String? = null,
        @SerializedName("ai_json") val aiJson: String? = null,
        @SerializedName("filed_type") val filedType: String? = null,
        @SerializedName("filed_id") val filedId: String? = null,
        @SerializedName("edited_text") val editedText: String? = null,
        @SerializedName("pinned") val pinned: Boolean? = null,
        @SerializedName("pin_order") val pinOrder: Int? = null,
        @SerializedName("prompt") val prompt: String? = null,
        @SerializedName("window_title") val windowTitle: String? = null,
        @SerializedName("thread_id") val threadId: String? = null,
        @SerializedName("message_id") val messageId: String? = null,
        @SerializedName("captured_at") val capturedAt: String? = null,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null,
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
abstract class ClipExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindClipExtraSync(impl: ClipExtraSync): ExtraSync
}
