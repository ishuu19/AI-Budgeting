package com.ledgerai.app.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun thread(id: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun message(id: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId")
    suspend fun messagesAny(threadId: String): List<ChatMessageEntity>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE threadId = :threadId AND deletedAt IS NULL
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    suspend fun latest(threadId: String, limit: Int): List<ChatMessageEntity>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE state = 'queued' AND role = 'user' AND deletedAt IS NULL
        ORDER BY createdAt ASC
        """
    )
    suspend fun queued(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_threads WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun threadsForSync(sinceMs: Long): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_messages WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun messagesForSync(sinceMs: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(entity: ChatThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: ChatMessageEntity)

    @Query(
        """
        UPDATE chat_threads SET deletedAt = :now, updatedAt = :now
        WHERE deletedAt IS NULL AND createdAt < :before
        AND id NOT IN (
            SELECT threadId FROM clips WHERE pinned = 1 AND deletedAt IS NULL AND threadId IS NOT NULL
        )
        AND id NOT IN (
            SELECT m.threadId FROM chat_messages m
            INNER JOIN clips c ON c.messageId = m.id
            WHERE c.pinned = 1 AND c.deletedAt IS NULL AND c.messageId IS NOT NULL
        )
        """
    )
    suspend fun softDeleteUnpinnedThreads(before: Long, now: Long)

    @Query(
        """
        UPDATE chat_messages SET deletedAt = :now, updatedAt = :now
        WHERE deletedAt IS NULL AND threadId IN (
            SELECT id FROM chat_threads WHERE deletedAt = :now
        )
        """
    )
    suspend fun softDeleteMessagesOfDeletedThreads(now: Long)
}
