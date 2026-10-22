package com.ledgerai.app.data.vault

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `folders`. The Vault tree, one level of nesting. Notes join through `notes.folderId`,
 * files through `media_assets` (`linkedType = 'folder'`).
 * [systemKey] is `notes` or `inbox` for the two folders that cannot be deleted.
 * A [locked] folder is encrypted, never uploaded and never indexed, so it implies [deviceOnly].
 */
@Entity(
    tableName = "folders",
    indices = [Index("userId"), Index("parentId"), Index("updatedAt")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val name: String,
    val parentId: String? = null,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val locked: Boolean = false,
    val deviceOnly: Boolean = false,
    val systemKey: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

object SystemFolder {
    const val NOTES = "notes"
    const val INBOX = "inbox"
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY pinned DESC, sortOrder, name COLLATE NOCASE")
    fun observeActive(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun get(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE systemKey = :key AND deletedAt IS NULL LIMIT 1")
    suspend fun bySystemKey(key: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: FolderEntity)

    @Query("SELECT * FROM folders WHERE updatedAt > :sinceMs AND deviceOnly = 0 AND locked = 0")
    suspend fun listForSync(sinceMs: Long): List<FolderEntity>

    @Query("UPDATE folders SET deletedAt = :now, updatedAt = :now WHERE id = :id AND systemKey IS NULL")
    suspend fun softDelete(id: String, now: Long)

    /** Live notes in a folder. A null `folderId` belongs to the Notes folder. */
    @Query(
        """
        SELECT COUNT(*) FROM notes
        WHERE deletedAt IS NULL
        AND (folderId = :folderId OR (:isNotes = 1 AND folderId IS NULL))
        """
    )
    suspend fun noteCount(folderId: String, isNotes: Int): Int
}
