package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val dao: NoteDao
) {

    fun observeNotes(): Flow<List<NoteItem>> =
        dao.observeAll().map { list ->
            list.map { it.toDomain() }.sortedByDescending { it.updatedAt }
        }

    suspend fun insert(note: NoteItem): Long {
        val now = LocalDateTime.now()
        val withTimestamps = note.copy(createdAt = now, updatedAt = now)
        return if (withTimestamps.id == 0L) {
            dao.insert(withTimestamps.toEntity(deletedAt = null).copy(id = 0))
        } else {
            val existing = dao.getById(withTimestamps.id)
            dao.insert(
                withTimestamps.toEntity(
                    userId = existing?.userId,
                    deletedAt = null
                )
            )
            withTimestamps.id
        }
    }

    suspend fun update(note: NoteItem) {
        if (note.id == 0L) return
        val existing = dao.getById(note.id)
        dao.update(
            note.copy(updatedAt = LocalDateTime.now())
                .toEntity(userId = existing?.userId, deletedAt = null)
        )
    }

    suspend fun delete(note: NoteItem) {
        if (note.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(note.id, deletedAt = now, updatedAt = now)
    }
}
