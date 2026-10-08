package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.JobApplicationDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.JobApplicationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobRepository @Inject constructor(
    private val dao: JobApplicationDao
) {
    fun observeAll(): Flow<List<JobApplication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(application: JobApplication): Long {
        if (application.url.isNotBlank()) {
            val dup = dao.findByUrl(application.url)
            if (dup != null && dup.id != application.id) return dup.id
        }
        return dao.insert(application.toEntity())
    }

    suspend fun delete(id: Long) {
        if (id > 0L) dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun getById(id: Long): JobApplication? =
        if (id <= 0L) null else dao.getById(id)?.toDomain()

    suspend fun saveBatch(apps: List<JobApplication>) {
        for (app in apps) save(app)
    }

    fun defaultFollowUp(appliedOn: LocalDate): LocalDate = appliedOn.plusDays(7)

    fun stats(apps: List<JobApplication>): JobStats {
        val weekStart = LocalDate.now().minusDays(7)
        val appliedWeek = apps.count { !it.appliedOn.isBefore(weekStart) }
        val responded = apps.count {
            it.status != JobApplicationStatus.APPLIED && it.status != JobApplicationStatus.WITHDRAWN
        }
        val interviews = apps.count { it.status == JobApplicationStatus.INTERVIEW }
        val rate = if (apps.isEmpty()) 0 else (responded * 100 / apps.size)
        return JobStats(appliedWeek, rate, interviews)
    }
}

data class JobStats(val appliedThisWeek: Int, val responseRatePercent: Int, val interviews: Int)
