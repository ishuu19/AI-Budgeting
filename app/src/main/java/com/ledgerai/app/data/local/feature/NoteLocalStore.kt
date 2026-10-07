package com.ledgerai.app.data.local.feature

import com.ledgerai.app.domain.model.NoteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Temporary process-local store for notes until Room Note DAO lands. */
@Singleton
class NoteLocalStore @Inject constructor() {

    private val nextId = AtomicLong(1)

    fun nextId(): Long = nextId.getAndIncrement()

    private val _notes = MutableStateFlow<List<NoteItem>>(emptyList())
    val notes: StateFlow<List<NoteItem>> = _notes.asStateFlow()

    fun upsert(item: NoteItem) = _notes.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun remove(id: Long) = _notes.update { it.filterNot { n -> n.id == id } }
}
