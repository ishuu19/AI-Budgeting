package com.ledgerai.app.data.chat

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One Ask session. Home chat can use the same table later. */
@Entity(
    tableName = "chat_threads",
    indices = [Index("remoteId"), Index("updatedAt")]
)
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val userId: String? = null,
    val title: String,
    val origin: String = "notebook",
    val sourceApp: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One turn. [state] is queued until the worker finishes. */
@Entity(
    tableName = "chat_messages",
    indices = [Index("threadId"), Index("remoteId"), Index("updatedAt")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val userId: String? = null,
    val threadId: String,
    val role: String,
    val text: String,
    val prompt: String? = null,
    val clipIds: String? = null,
    val state: String,
    val actionsJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
