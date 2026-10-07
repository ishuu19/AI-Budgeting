package com.ledgerai.app.data.local.feature

import com.ledgerai.app.domain.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary process-local store for tasks until Room Task DAO lands.
 */
@Singleton
class TaskLocalStore @Inject constructor() {

    private val nextId = AtomicLong(1)
    private val nextReminderId = AtomicLong(1)

    fun nextId(): Long = nextId.getAndIncrement()
    fun nextReminderId(): Long = nextReminderId.getAndIncrement()

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    fun upsert(item: TaskItem) = _tasks.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun remove(id: Long) = _tasks.update { it.filterNot { t -> t.id == id } }

    fun getById(id: Long): TaskItem? = _tasks.value.firstOrNull { it.id == id }
}
