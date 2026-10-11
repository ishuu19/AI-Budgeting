package com.ledgerai.app.data.media

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `media_assets`: one photo the user captured, whatever it turns out to be.
 * The file lives on the phone first and in the private storage bucket after upload.
 * [uploadState] and [analysisState] move independently so offline capture just waits.
 * [linkedType]/[linkedId] point at the record the AI filed it under (rule 5: keep sources).
 * [payload] holds a pending one-tap suggestion as JSON until the user accepts it.
 */
@Entity(
    tableName = "media_assets",
    indices = [Index("userId"), Index("uploadState"), Index("analysisState")],
)
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localPath: String,
    val remotePath: String? = null,
    val kind: String = MediaKind.UNKNOWN,
    val title: String = "",
    val description: String = "",
    val note: String = "",
    val uploadState: String = UploadState.PENDING,
    val analysisState: String = AnalysisState.PENDING,
    val linkedType: String? = null,
    val linkedId: String? = null,
    val payload: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

object MediaKind {
    const val RECEIPT = "receipt"
    const val FOOD = "food"
    const val CLOTHING = "clothing"
    const val PERSON = "person"
    const val OTHER = "other"
    const val UNKNOWN = "unknown"
}

object UploadState {
    const val PENDING = "pending"
    const val UPLOADED = "uploaded"
}

object AnalysisState {
    const val PENDING = "pending"
    /** Filed or described; nothing more is needed from the user. */
    const val DONE = "done"
    /** The AI looked but needs one answer (for example a person's name) or one tap. */
    const val NEEDS_INPUT = "needs_input"
    const val FAILED = "failed"
}

@Dao
interface MediaAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: MediaAssetEntity)

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun get(id: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets WHERE userId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(userId: String, limit: Int): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE deletedAt IS NULL AND analysisState = 'pending' ORDER BY createdAt")
    suspend fun listPendingAnalysis(): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE deletedAt IS NULL AND uploadState = 'pending' ORDER BY createdAt")
    suspend fun listPendingUpload(): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE userId = :userId AND kind = :kind AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun listByKind(userId: String, kind: String): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets WHERE updatedAt > :since")
    suspend fun listForSync(since: Long): List<MediaAssetEntity>

    @Query("UPDATE media_assets SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}
