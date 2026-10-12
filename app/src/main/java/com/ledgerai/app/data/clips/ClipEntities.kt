package com.ledgerai.app.data.clips

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One captured piece of text and where it came from. The note is the readable record.
 * This row is the source, like voice_history or media_assets.
 */
@Entity(
    tableName = "clips",
    indices = [Index("noteId"), Index("status"), Index("remoteId"), Index("updatedAt"), Index("textHash")]
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val remoteId: String? = null,
    val userId: String? = null,
    val text: String,
    val textHash: String,
    val sourceKind: String,
    val sourceApp: String? = null,
    val url: String? = null,
    val noteId: Long? = null,
    val status: String = "inbox",
    val sensitive: Boolean = false,
    val aiState: String = "none",
    val aiKind: String? = null,
    val aiJson: String? = null,
    val filedType: String? = null,
    val filedId: String? = null,
    val capturedAt: Long,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
