package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.RoutineDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.RoutineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao
) {

    fun observeRoutines(): Flow<List<RoutineItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<RoutineItem>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun insert(routine: RoutineItem): Long {
        val now = System.currentTimeMillis()
        return if (routine.id == 0L) {
            dao.insert(routine.toEntity(updatedAt = now, deletedAt = null).copy(id = 0))
        } else {
            val existing = dao.getById(routine.id)
            dao.insert(
                routine.toEntity(
                    userId = existing?.userId,
                    updatedAt = now,
                    deletedAt = null
                )
            )
            routine.id
        }
    }

    suspend fun update(routine: RoutineItem) {
        if (routine.id == 0L) return
        val existing = dao.getById(routine.id) ?: return
        dao.update(
            routine.toEntity(
                userId = existing.userId,
                updatedAt = System.currentTimeMillis(),
                deletedAt = null
            )
        )
    }

    suspend fun setActive(id: Long, active: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(isActive = active, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun delete(routine: RoutineItem) {
        if (routine.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(routine.id, deletedAt = now, updatedAt = now)
    }

    suspend fun findById(id: Long): RoutineItem? = dao.getById(id)?.toDomain()
}
