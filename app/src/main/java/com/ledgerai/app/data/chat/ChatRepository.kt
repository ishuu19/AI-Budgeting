package com.ledgerai.app.data.chat

import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.preferences.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
    private val clips: ClipDao,
    private val session: UserSession,
) {
    fun observeThreads(): Flow<List<ChatThreadEntity>> = dao.observeThreads()
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> = dao.observeMessages(threadId)

    suspend fun thread(id: String): ChatThreadEntity? = dao.thread(id)
    suspend fun message(id: String): ChatMessageEntity? = dao.message(id)
    suspend fun queued(): List<ChatMessageEntity> = dao.queued()
    suspend fun latest(threadId: String, limit: Int): List<ChatMessageEntity> = dao.latest(threadId, limit)

    suspend fun openThread(title: String, sourceApp: String?, origin: String = "notebook"): ChatThreadEntity {
        val now = System.currentTimeMillis()
        val row = ChatThreadEntity(
            id = UUID.randomUUID().toString(),
            userId = session.userInfo.first().userId.ifBlank { null },
            title = title.take(120).ifBlank { "New note" },
            origin = origin,
            sourceApp = sourceApp?.take(200),
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertThread(row)
        return row
    }

    suspend fun rename(id: String, title: String) {
        val row = dao.thread(id) ?: return
        val next = title.trim().take(120)
        if (next.isBlank()) return
        dao.upsertThread(row.copy(title = next, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addMessage(
        threadId: String,
        role: String,
        text: String,
        prompt: String?,
        clipIds: String?,
        state: String,
        actionsJson: String? = null,
    ): ChatMessageEntity {
        val now = System.currentTimeMillis()
        val row = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            userId = session.userInfo.first().userId.ifBlank { null },
            threadId = threadId,
            role = role,
            text = text.take(20_000),
            prompt = prompt?.take(2_000),
            clipIds = clipIds,
            state = state,
            actionsJson = actionsJson,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertMessage(row)
        dao.thread(threadId)?.let { dao.upsertThread(it.copy(updatedAt = now)) }
        return row
    }

    suspend fun updateMessage(row: ChatMessageEntity) = dao.upsertMessage(row)

    suspend fun deleteThread(id: String): Long {
        val now = System.currentTimeMillis()
        val thread = dao.thread(id) ?: return now
        dao.upsertThread(thread.copy(deletedAt = now, updatedAt = now))
        dao.messagesAny(id).filter { it.deletedAt == null }.forEach {
            dao.upsertMessage(it.copy(deletedAt = now, updatedAt = now))
        }
        return now
    }

    suspend fun undoDeleteThread(id: String, stamp: Long) {
        val thread = dao.thread(id) ?: return
        val now = System.currentTimeMillis()
        if (thread.deletedAt == stamp) dao.upsertThread(thread.copy(deletedAt = null, updatedAt = now))
        dao.messagesAny(id).filter { it.deletedAt == stamp }.forEach {
            dao.upsertMessage(it.copy(deletedAt = null, updatedAt = now))
        }
    }

    /** Threads with a pinned entry or a pinned answer stay. */
    suspend fun expire(before: Long) {
        val now = System.currentTimeMillis()
        dao.softDeleteUnpinnedThreads(before, now)
        dao.softDeleteMessagesOfDeletedThreads(now)
    }

    suspend fun hasPinned(threadId: String): Boolean = clips.pinnedCountForThread(threadId) > 0
}
