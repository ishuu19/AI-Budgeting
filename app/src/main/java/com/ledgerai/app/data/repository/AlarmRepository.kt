package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.feature.AlarmLocalStore
import com.ledgerai.app.domain.model.AlarmItem
import com.ledgerai.app.service.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val store: AlarmLocalStore,
    private val scheduler: AlarmScheduler
) {

    fun observeAlarms(): Flow<List<AlarmItem>> =
        store.alarms.map { list -> list.sortedBy { it.time } }

    suspend fun insert(alarm: AlarmItem): Long {
        val id = if (alarm.id == 0L) store.nextId() else alarm.id
        val saved = alarm.copy(id = id)
        store.upsert(saved)
        if (saved.isEnabled) scheduler.schedule(saved)
        return id
    }

    suspend fun update(alarm: AlarmItem) {
        if (alarm.id == 0L) return
        store.upsert(alarm)
        if (alarm.isEnabled) scheduler.schedule(alarm) else scheduler.cancel(alarm.id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val existing = store.getById(id) ?: return
        val updated = existing.copy(isEnabled = enabled)
        store.upsert(updated)
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(id)
    }

    suspend fun delete(alarm: AlarmItem) {
        if (alarm.id == 0L) return
        scheduler.cancel(alarm.id)
        store.remove(alarm.id)
    }
}
