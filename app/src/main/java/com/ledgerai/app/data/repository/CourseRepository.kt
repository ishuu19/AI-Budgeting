package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.CourseDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.Course
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val dao: CourseDao
) {
    fun observeCourses(): Flow<List<Course>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Course? = dao.getById(id)?.toDomain()

    suspend fun insert(course: Course): Long {
        val now = System.currentTimeMillis()
        return if (course.id == 0L) {
            dao.insert(course.toEntity(updatedAt = now, deletedAt = null).copy(id = 0))
        } else {
            val existing = dao.getById(course.id)
            dao.insert(
                course.toEntity(
                    userId = existing?.userId,
                    updatedAt = now,
                    deletedAt = null
                )
            )
            course.id
        }
    }

    suspend fun delete(course: Course) {
        if (course.id == 0L) return
        val now = System.currentTimeMillis()
        dao.softDelete(course.id, deletedAt = now, updatedAt = now)
    }

    /** Match by code (case-insensitive) or create a new course from import row. */
    suspend fun resolveOrCreate(code: String, name: String, location: String): Long {
        val trimmedCode = code.trim()
        val displayName = name.trim().ifBlank { trimmedCode }.ifBlank { "Class" }
        if (trimmedCode.isNotBlank()) {
            dao.findByCode(trimmedCode)?.let { return it.id }
        }
        return insert(
            Course(
                code = trimmedCode,
                name = displayName,
                defaultLocation = location.trim()
            )
        )
    }
}
