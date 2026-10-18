package com.ledgerai.app.data.clips

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips WHERE deletedAt IS NULL ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT COUNT(*) FROM clips WHERE status = 'inbox' AND deletedAt IS NULL")
    fun observeInboxCount(): Flow<Int>

    @Query("SELECT * FROM clips WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY capturedAt ASC")
    fun observeForNote(noteId: Long): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ClipEntity?

    @Query(
        """
        SELECT * FROM clips WHERE textHash = :hash AND deletedAt IS NULL AND capturedAt >= :since
        ORDER BY capturedAt DESC LIMIT 1
        """
    )
    suspend fun recentHash(hash: String, since: Long): ClipEntity?

    @Query("SELECT * FROM clips WHERE aiState = 'queued' AND sensitive = 0 AND deletedAt IS NULL ORDER BY capturedAt ASC")
    suspend fun queued(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE deletedAt IS NULL AND capturedAt >= :start")
    suspend fun capturedSince(start: Long): List<ClipEntity>

    @Query(
        """
        SELECT * FROM clips WHERE sensitive = 0 AND deletedAt IS NULL
        AND text LIKE '%' || :q || '%' ORDER BY capturedAt DESC LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE sensitive = 0 AND (updatedAt > :sinceMs OR remoteId IS NULL)")
    suspend fun listForSync(sinceMs: Long): List<ClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClipEntity)

    @Query("SELECT id FROM clips WHERE sensitive = 1")
    suspend fun sensitiveIds(): List<String>

    @Query("SELECT MAX(pinOrder) FROM clips WHERE pinned = 1 AND deletedAt IS NULL")
    suspend fun maxPinOrder(): Int?

    @Query(
        """
        SELECT COUNT(*) FROM clips WHERE deletedAt IS NULL AND pinned = 1 AND (
            threadId = :threadId OR messageId IN (SELECT id FROM chat_messages WHERE threadId = :threadId)
        )
        """
    )
    suspend fun pinnedCountForThread(threadId: String): Int

    @Query(
        """
        UPDATE clips SET deletedAt = :now, updatedAt = :now
        WHERE status = 'inbox' AND deletedAt IS NULL AND noteId IS NULL AND filedId IS NULL
        AND pinned = 0 AND capturedAt < :before
        """
    )
    suspend fun softDeleteInboxOlder(before: Long, now: Long)
}
