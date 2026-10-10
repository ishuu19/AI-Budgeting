package com.ledgerai.app.data.people

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ledgerai.app.domain.people.Commitment
import com.ledgerai.app.domain.people.Interaction
import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.domain.people.RecordVisibility
import java.time.LocalDate

/** `people`. Private by default. No household share column beyond [visibility]. */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val org: String? = null,
    val role: String? = null,
    val notes: String = "",
    val visibility: String = RecordVisibility.PRIVATE.toStored(),
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** `interactions`. [personId] is required. */
@Entity(
    tableName = "interactions",
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
data class InteractionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val personId: String,
    val occurredOn: LocalDate,
    val where: String? = null,
    val summary: String,
    val sourceId: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/**
 * `commitments`. [personId] stays nullable. [dueOn] stays null when there is no date.
 */
@Entity(
    tableName = "commitments",
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
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val personId: String? = null,
    val eventId: String? = null,
    val text: String,
    val dueOn: LocalDate? = null,
    val status: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

fun RecordVisibility.toStored(): String = name.lowercase()

fun String.toRecordVisibility(): RecordVisibility = RecordVisibility.valueOf(uppercase())

fun Person.toEntity(): PersonEntity = PersonEntity(
    id = id,
    userId = userId,
    name = name,
    org = org,
    role = role,
    notes = notes,
    visibility = visibility.toStored(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    userId = userId,
    name = name,
    org = org,
    role = role,
    notes = notes,
    visibility = visibility.toRecordVisibility(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Interaction.toEntity(): InteractionEntity = InteractionEntity(
    id = id,
    userId = userId,
    personId = personId,
    occurredOn = occurredOn,
    where = where,
    summary = summary,
    sourceId = sourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun InteractionEntity.toDomain(): Interaction = Interaction(
    id = id,
    userId = userId,
    personId = personId,
    occurredOn = occurredOn,
    where = where,
    summary = summary,
    sourceId = sourceId,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Commitment.toEntity(): CommitmentEntity = CommitmentEntity(
    id = id,
    userId = userId,
    personId = personId,
    eventId = eventId,
    text = text,
    dueOn = dueOn,
    status = status,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun CommitmentEntity.toDomain(): Commitment = Commitment(
    id = id,
    userId = userId,
    personId = personId,
    eventId = eventId,
    text = text,
    dueOn = dueOn,
    status = status,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
