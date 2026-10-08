package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.AlarmDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.AlarmItem
import com.ledgerai.app.service.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler
) {

    fun observeAlarms(): Flow<List<AlarmItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.sortedBy { it.time } }

    suspend fun getById(id: Long): AlarmItem? = dao.getById(id)?.toDomain()

    suspend fun getEnabledAlarms(): List<AlarmItem> =
        dao.observeEnabled().first().map { it.toDomain() }

    /** Re-arms every enabled alarm (e.g. after boot). Returns count scheduled. */
    suspend fun rescheduleAllEnabled(): Int {
        val enabled = getEnabledAlarms()
        enabled.forEach { scheduler.schedule(it) }
        return enabled.size
    }

    suspend fun insert(alarm: AlarmItem): Long {
        val entity = alarm.toEntity(deletedAt = null).let {
            if (alarm.id == 0L) it.copy(id = 0) else it
        }
        val id = dao.insert(entity)
        val saved = alarm.copy(id = id)
        if (saved.isEnabled) scheduler.schedule(saved)
        return id
    }

    suspend fun update(alarm: AlarmItem) {
        if (alarm.id == 0L) return
        val existing = dao.getById(alarm.id)
        dao.update(alarm.toEntity(userId = existing?.userId, deletedAt = null))
        if (alarm.isEnabled) scheduler.schedule(alarm) else scheduler.cancel(alarm.id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val existing = dao.getById(id) ?: return
        val updated = existing.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        val domain = updated.toDomain()
        if (enabled) scheduler.schedule(domain) else scheduler.cancel(id)
    }

    suspend fun setToneUri(id: Long, toneUri: String?) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(toneUri = toneUri, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun delete(alarm: AlarmItem) {
        if (alarm.id == 0L) return
        scheduler.cancel(alarm.id)
        val now = System.currentTimeMillis()
        dao.softDelete(alarm.id, deletedAt = now, updatedAt = now)
    }
}
