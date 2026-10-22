package com.ledgerai.app.data.passwords

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * `passwords`. Device only: never synced, never searched, never sent to the model.
 * [usernameEnc], [secretEnc] and [noteEnc] are `iv:ciphertext` Base64 strings from [PasswordCipher].
 * Title, site and dates stay readable so the list renders without decrypting every row.
 */
@Entity(
    tableName = "passwords",
    indices = [Index("title")]
)
data class PasswordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val site: String? = null,
    val usernameEnc: String = "",
    val secretEnc: String = "",
    val noteEnc: String = "",
    val lastChanged: Long,
    val changeEveryDays: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords")
    suspend fun all(): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PasswordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PasswordEntity)

    /** Hard delete: there is no trash for secrets. */
    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun delete(id: String)
}
