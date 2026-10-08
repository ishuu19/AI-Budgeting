package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.PlanBlockDao
import com.ledgerai.app.data.local.room.StudyPlanDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.service.PlanBlockScheduler
import com.ledgerai.app.data.schedule.BusyInterval
import com.ledgerai.app.data.schedule.FreeBlockFinder
import com.ledgerai.app.data.schedule.FreeSlotOption
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.StudyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepository @Inject constructor(
    private val studyPlanDao: StudyPlanDao,
    private val planBlockDao: PlanBlockDao,
    private val calendarRepository: CalendarRepository,
    private val planBlockScheduler: PlanBlockScheduler
) {

    fun observeBlocks(): Flow<List<PlanBlock>> =
        planBlockDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveStudyPlan(plan: StudyPlan): Long =
        studyPlanDao.insert(plan.toEntity())

    suspend fun createBlocksFromSlots(planId: Long, topic: String, slots: List<FreeSlotOption>) {
        val now = System.currentTimeMillis()
        for (slot in slots) {
            val id = planBlockDao.insert(
                PlanBlock(
                    kind = PlanBlockKind.STUDY,
                    title = topic,
                    topic = topic,
                    startAt = slot.start,
                    endAt = slot.end,
                    status = PlanBlockStatus.SCHEDULED,
                    sourcePlanId = planId
                ).toEntity(now)
            )
            planBlockScheduler.schedule(id, topic, slot.start)
        }
    }

    /** Saves the plan and its blocks together. Call only after the user confirmed the proposals. */
    suspend fun saveStudyPlanWithBlocks(plan: StudyPlan, slots: List<FreeSlotOption>): Long {
        if (slots.isEmpty()) return 0L
        val planId = saveStudyPlan(plan)
        createBlocksFromSlots(planId, plan.topic, slots)
        return planId
    }

    suspend fun getBlock(id: Long): PlanBlock? =
        if (id <= 0L) null else planBlockDao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    suspend fun deleteBlock(id: Long) {
        if (id <= 0L) return
        planBlockDao.softDelete(id, System.currentTimeMillis())
        planBlockScheduler.cancel(id)
    }

    suspend fun setBlockStatus(id: Long, status: PlanBlockStatus) {
        if (id <= 0L) return
        planBlockDao.updateStatus(id, status.name, System.currentTimeMillis())
        if (status != PlanBlockStatus.SCHEDULED) planBlockScheduler.cancel(id)
    }

    suspend fun rescheduleAllBlockAlarms(): Int {
        val now = LocalDateTime.now()
        val blocks = planBlockDao.listFutureScheduled(now)
        blocks.forEach { entity ->
            planBlockScheduler.schedule(entity.id, entity.title, entity.startAt)
        }
        return blocks.size
    }

    suspend fun findStudySlots(
        hoursNeeded: Double,
        sessionLenMinutes: Int,
        deadline: LocalDate?
    ): List<FreeSlotOption> {
        val today = LocalDate.now()
        val end = deadline?.plusDays(1) ?: today.plusDays(14)
        val busy = collectBusyIntervals(today, end)
        return FreeBlockFinder.findSlots(
            rangeStart = today,
            rangeEnd = end,
            busy = busy,
            totalMinutesNeeded = (hoursNeeded * 60).toInt(),
            sessionLenMinutes = sessionLenMinutes,
            deadline = deadline
        )
    }

    /** Calendar events that occupy time (classes included) plus scheduled plan blocks. */
    private suspend fun collectBusyIntervals(from: LocalDate, to: LocalDate): List<BusyInterval> {
        val out = mutableListOf<BusyInterval>()
        val events = calendarRepository.listRange(from, to.coerceAtMost(from.plusDays(60)))
        for (e in events) {
            if (!e.kind.blocksTime || !e.isEnabled || !e.endAt.isAfter(e.startAt)) continue
            out += BusyInterval(e.startAt, e.endAt, e.title)
        }
        val blocks = planBlockDao.observeAll().first()
        for (b in blocks) {
            if (b.deletedAt != null) continue
            val d = b.startAt.toLocalDate()
            if (!d.isBefore(from) && !d.isAfter(to)) {
                out += BusyInterval(b.startAt, b.endAt, b.title)
            }
        }
        return out
    }

    suspend fun markBlockDone(id: Long, actualMinutes: Int) {
        val now = System.currentTimeMillis()
        planBlockDao.updateStatus(id, PlanBlockStatus.DONE.name, now)
    }
}
