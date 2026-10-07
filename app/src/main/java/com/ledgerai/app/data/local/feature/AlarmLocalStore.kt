package com.ledgerai.app.data.local.feature

import com.ledgerai.app.domain.model.AlarmItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Temporary process-local store for alarms until Room Alarm DAO lands. */
@Singleton
class AlarmLocalStore @Inject constructor() {

    private val nextId = AtomicLong(1)

    fun nextId(): Long = nextId.getAndIncrement()

    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms.asStateFlow()

    fun upsert(item: AlarmItem) = _alarms.update { current ->
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current.toMutableList().also { it[idx] = item }
        else current + item
    }

    fun remove(id: Long) = _alarms.update { it.filterNot { a -> a.id == id } }

    fun getById(id: Long): AlarmItem? = _alarms.value.firstOrNull { it.id == id }
}
