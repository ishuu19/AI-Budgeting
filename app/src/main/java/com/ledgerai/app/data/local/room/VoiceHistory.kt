package com.ledgerai.app.data.local.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One spoken (or typed) capture and what it became.
 * [resultKind] is the name of a VoiceResultKind. [linkedItemId] is the id of the saved item
 * (transaction, event, bill, ...) when one exists. Local only, never synced.
 */
@Entity(tableName = "voice_history", indices = [Index(value = ["createdAt"])])
data class VoiceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val resultKind: String,
    val linkedItemId: Long? = null,
    val resultSummary: String = "",
    val createdAt: Long,
    val deletedAt: Long? = null,
    val remoteId: String? = null,
    val userId: String? = null,
    val updatedAt: Long = 0L
)

@Dao
interface VoiceHistoryDao {

    @Query("SELECT * FROM voice_history WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<VoiceHistoryEntity>>

    @Query("SELECT * FROM voice_history WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): VoiceHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VoiceHistoryEntity): Long

    @Update
    suspend fun update(entity: VoiceHistoryEntity)

    @Query("UPDATE voice_history SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE voice_history SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM voice_history WHERE updatedAt > :sinceMs OR remoteId IS NULL")
    suspend fun listForSync(sinceMs: Long): List<VoiceHistoryEntity>

    @Query("SELECT * FROM voice_history WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): VoiceHistoryEntity?
}

/** Version 7 to 8: adds the `voice_history` table. Nothing else changes. */
object Migration7To8Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS voice_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            transcript TEXT NOT NULL,
            resultKind TEXT NOT NULL,
            linkedItemId INTEGER,
            resultSummary TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            deletedAt INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS index_voice_history_createdAt ON voice_history (createdAt)"
    ).map { it.trimIndent() }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration7To8Sql.statements.forEach { db.execSQL(it) }
    }
}
