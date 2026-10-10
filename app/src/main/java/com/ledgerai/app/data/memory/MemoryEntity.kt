package com.ledgerai.app.data.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ledgerai.app.data.people.PersonEntity
import com.ledgerai.app.data.people.toRecordVisibility
import com.ledgerai.app.data.people.toStored
import com.ledgerai.app.domain.memory.Memory
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.people.RecordVisibility

/** `memories`. [personId] is required. Private by default. No embedding column. */
@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("personId")],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val personId: String,
    val text: String,
    val kind: String,
    val status: String,
    val sourceType: String,
    val sourceId: String? = null,
    val visibility: String = RecordVisibility.PRIVATE.toStored(),
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

fun MemoryKind.toStored(): String = name.lowercase()

fun String.toMemoryKind(): MemoryKind = MemoryKind.valueOf(uppercase())

fun MemoryStatus.toStored(): String = name.lowercase()

fun String.toMemoryStatus(): MemoryStatus = MemoryStatus.valueOf(uppercase())

fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    userId = userId,
    personId = personId,
    text = text,
    kind = kind.toStored(),
    status = status.toStored(),
    sourceType = sourceType,
    sourceId = sourceId,
    visibility = visibility.toStored(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun MemoryEntity.toDomain(): Memory = Memory(
    id = id,
    userId = userId,
    personId = personId,
    text = text,
    kind = kind.toMemoryKind(),
    status = status.toMemoryStatus(),
    sourceType = sourceType,
    sourceId = sourceId,
    visibility = visibility.toRecordVisibility(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
