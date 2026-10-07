package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.feature.NoteLocalStore
import com.ledgerai.app.domain.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val store: NoteLocalStore
) {

    fun observeNotes(): Flow<List<NoteItem>> =
        store.notes.map { list -> list.sortedByDescending { it.updatedAt } }

    suspend fun insert(note: NoteItem): Long {
        val id = if (note.id == 0L) store.nextId() else note.id
        val now = LocalDateTime.now()
        store.upsert(note.copy(id = id, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun update(note: NoteItem) {
        if (note.id == 0L) return
        store.upsert(note.copy(updatedAt = LocalDateTime.now()))
    }

    suspend fun delete(note: NoteItem) {
        if (note.id == 0L) return
        store.remove(note.id)
    }
}
