package com.ledgerai.app.data.local.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * One executor step. [actionIndex] plus [inputRef] is the idempotency key.
 * [planJson] keeps the action type and the executor message. [error] is set only when the step failed or was rejected.
 */
@Entity(
    tableName = "assistant_actions",
    indices = [Index(value = ["inputRef", "actionIndex"], unique = true)]
)
data class AssistantActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val inputRef: String,
    val actionIndex: Int,
    val planJson: String,
    val risk: String,
    val status: String,
    val error: String = "",
    val appliedAt: Long? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** One applied action. [sourceActionId] is the local [AssistantActionEntity.id]. */
@Entity(
    tableName = "event_log",
    indices = [Index("sourceActionId"), Index("createdAt")]
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val type: String,
    val payload: String,
    val sourceActionId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Dao
interface AssistantAuditDao {
    @Query(
        """
        SELECT * FROM assistant_actions
        WHERE inputRef = :inputRef AND actionIndex = :index AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun findAction(inputRef: String, index: Int): AssistantActionEntity?

    @Insert
    suspend fun insertAction(entity: AssistantActionEntity): Long

    @Update
    suspend fun updateAction(entity: AssistantActionEntity)

    @Insert
    suspend fun insertEvent(entity: EventLogEntity): Long

    @Query(
        """
        SELECT * FROM event_log
        WHERE sourceActionId = :actionId AND deletedAt IS NULL
        LIMIT 1
        """
    )
    suspend fun findEventByAction(actionId: Long): EventLogEntity?
}

/** Version 10 to 11: audit tables for the assistant executor. Nothing else changes. */
object Migration10To11Sql {
    val statements: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS assistant_actions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            remoteId TEXT,
            userId TEXT,
            inputRef TEXT NOT NULL,
            actionIndex INTEGER NOT NULL,
            planJson TEXT NOT NULL,
            risk TEXT NOT NULL,
            status TEXT NOT NULL,
            error TEXT NOT NULL,
            appliedAt INTEGER,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
        )
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS index_assistant_actions_inputRef_actionIndex
        ON assistant_actions (inputRef, actionIndex)
        """,
        """
        CREATE TABLE IF NOT EXISTS event_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            remoteId TEXT,
            userId TEXT,
            type TEXT NOT NULL,
            payload TEXT NOT NULL,
            sourceActionId INTEGER,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
        )
        """,
        "CREATE INDEX IF NOT EXISTS index_event_log_sourceActionId ON event_log (sourceActionId)",
        "CREATE INDEX IF NOT EXISTS index_event_log_createdAt ON event_log (createdAt)",
    ).map { it.trimIndent() }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration10To11Sql.statements.forEach { db.execSQL(it) }
    }
}
