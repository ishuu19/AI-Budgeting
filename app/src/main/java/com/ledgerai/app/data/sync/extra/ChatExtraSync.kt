package com.ledgerai.app.data.sync.extra

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.chat.ChatDao
import com.ledgerai.app.data.chat.ChatMessageEntity
import com.ledgerai.app.data.chat.ChatThreadEntity
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.sync.ExtraSync
import com.ledgerai.app.data.sync.SyncTime
import com.ledgerai.app.domain.notebook.NotebookEntry
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
 * Room chats <-> Supabase. Messages that used a sensitive clip stay on the phone.
 */
@Singleton
class ChatExtraSync @Inject constructor(
    private val dao: ChatDao,
    private val clips: ClipDao,
    private val gson: Gson,
    @Named("supabaseOkHttp") client: OkHttpClient,
) : ExtraSync {

    private val api: ChatApi = Retrofit.Builder()
        .baseUrl(restBaseUrl(BuildConfig.SUPABASE_URL))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ChatApi::class.java)

    override suspend fun sync(bearer: String, apiKey: String, userId: String, sinceMs: Long, filter: String) {
        pull(bearer, apiKey, filter)
        push(bearer, apiKey, userId, sinceMs)
    }

    private suspend fun pull(bearer: String, apiKey: String, filter: String) {
        var offset = 0
        while (true) {
            val page = api.pullThreads(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote ->
                val local = dao.thread(remote.id)
                if (local == null) dao.upsertThread(remote.toThread())
                else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) dao.upsertThread(remote.mergeThread(local))
            }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
        offset = 0
        val sensitive = clips.sensitiveIds().toSet()
        while (true) {
            val page = api.pullMessages(bearer, apiKey, updatedAt = filter, offset = offset)
            page.forEach { remote ->
                val ids = remote.clipIds.orEmpty()
                if (!NotebookEntry.syncMessage(ids, remote.text, sensitive)) return@forEach
                val local = dao.message(remote.id)
                val row = remote.toMessage()
                if (local == null) dao.upsertMessage(row)
                else if (SyncTime.remoteWins(remote.updatedAt, local.updatedAt)) dao.upsertMessage(remote.mergeMessage(local))
            }
            if (page.size < PAGE_SIZE) break
            offset += page.size
        }
    }

    private suspend fun push(bearer: String, apiKey: String, userId: String, sinceMs: Long) {
        val threads = dao.threadsForSync(sinceMs).map { it.toRemote(userId) }
        if (threads.isNotEmpty()) api.upsertThreads(bearer, apiKey, body = threads)
        val sensitive = clips.sensitiveIds().toSet()
        val messages = dao.messagesForSync(sinceMs).mapNotNull { it.toRemote(userId, sensitive) }
        if (messages.isNotEmpty()) api.upsertMessages(bearer, apiKey, body = messages)
    }

    private fun ChatThreadEntity.toRemote(owner: String) = RemoteThread(
        id = id,
        userId = owner,
        title = title.ifBlank { "Note" },
        origin = origin,
        sourceApp = sourceApp,
        createdAt = SyncTime.millisToIso(createdAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso),
    )

    private fun ChatMessageEntity.toRemote(owner: String, sensitive: Set<String>): RemoteMessage? {
        val ids = parseIds(clipIds)
        if (!NotebookEntry.syncMessage(ids, text, sensitive)) return null
        return RemoteMessage(
            id = id,
            userId = owner,
            threadId = threadId,
            role = role,
            text = text,
            prompt = prompt,
            clipIds = ids,
            state = state,
            actionsJson = actionsJson?.let { runCatching { JsonParser.parseString(it) }.getOrNull() },
            createdAt = SyncTime.millisToIso(createdAt),
            updatedAt = SyncTime.millisToIso(updatedAt),
            deletedAt = deletedAt?.let(SyncTime::millisToIso),
        )
    }

    private fun RemoteThread.toThread() = ChatThreadEntity(
        id = id,
        remoteId = id,
        userId = userId,
        title = title,
        origin = origin,
        sourceApp = sourceApp,
        createdAt = SyncTime.isoToMillis(createdAt),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private fun RemoteThread.mergeThread(local: ChatThreadEntity) = local.copy(
        remoteId = id,
        title = title,
        origin = origin,
        sourceApp = sourceApp,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private fun RemoteMessage.toMessage() = ChatMessageEntity(
        id = id,
        remoteId = id,
        userId = userId,
        threadId = threadId,
        role = role,
        text = text,
        prompt = prompt,
        clipIds = clipIds?.let { gson.toJson(it) },
        state = state,
        actionsJson = actionsJson?.toString(),
        createdAt = SyncTime.isoToMillis(createdAt),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private fun RemoteMessage.mergeMessage(local: ChatMessageEntity) = local.copy(
        remoteId = id,
        text = text,
        prompt = prompt ?: local.prompt,
        clipIds = clipIds?.let { gson.toJson(it) } ?: local.clipIds,
        state = state,
        actionsJson = actionsJson?.toString() ?: local.actionsJson,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } },
    )

    private fun parseIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }
    }

    private interface ChatApi {
        @GET("chat_threads")
        suspend fun pullThreads(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0,
        ): List<RemoteThread>

        @POST("chat_threads")
        suspend fun upsertThreads(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteThread>,
        ): List<RemoteThread>

        @GET("chat_messages")
        suspend fun pullMessages(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Query("updated_at") updatedAt: String,
            @Query("select") select: String = "*",
            @Query("order") order: String = PULL_ORDER,
            @Query("limit") limit: Int = PAGE_SIZE,
            @Query("offset") offset: Int = 0,
        ): List<RemoteMessage>

        @POST("chat_messages")
        suspend fun upsertMessages(
            @Header("Authorization") authorization: String,
            @Header("apikey") apiKey: String,
            @Header("Prefer") prefer: String = UPSERT_PREFER,
            @Query("on_conflict") onConflict: String = "id",
            @Body body: List<RemoteMessage>,
        ): List<RemoteMessage>
    }

    private data class RemoteThread(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("title") val title: String = "",
        @SerializedName("origin") val origin: String = "notebook",
        @SerializedName("source_app") val sourceApp: String? = null,
        @SerializedName("created_at") val createdAt: String? = null,
        @SerializedName("updated_at") val updatedAt: String? = null,
        @SerializedName("deleted_at") val deletedAt: String? = null,
    )

    private data class RemoteMessage(
        @SerializedName("id") val id: String,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("thread_id") val threadId: String = "",
        @SerializedName("role") val role: String = "user",
        @SerializedName("text") val text: String = "",
        @SerializedName("prompt") val prompt: String? = null,
        @SerializedName("clip_ids") val clipIds: List<String>? = null,
        @SerializedName("state") val state: String = "done",
        @SerializedName("actions_json") val actionsJson: com.google.gson.JsonElement? = null,
        @SerializedName("created_at") val createdAt: String? = null,
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
abstract class ChatExtraSyncModule {
    @Binds
    @IntoSet
    abstract fun bindChatExtraSync(impl: ChatExtraSync): ExtraSync
}
