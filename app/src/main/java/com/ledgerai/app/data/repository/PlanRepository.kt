package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.PlanBlockDao
import com.ledgerai.app.data.local.room.StudyPlanDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.service.PlanBlockScheduler
import com.ledgerai.app.data.schedule.BusyInterval
import com.ledgerai.app.data.schedule.expandSlotsForMonth
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
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
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

    private suspend fun collectBusyIntervals(from: LocalDate, to: LocalDate): List<BusyInterval> {
        val out = mutableListOf<BusyInterval>()
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(0) + 1
        val events = calendarRepository.listNextDays(days.coerceAtMost(60))
        for (e in events) {
            out += BusyInterval(e.startAt, e.endAt, e.title)
        }
        val slots = scheduleRepository.observeAllSlots().first()
        var month = from.withDayOfMonth(1)
        while (!month.isAfter(to)) {
            for (cls in expandSlotsForMonth(slots, month)) {
                val d = cls.startAt.toLocalDate()
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    out += BusyInterval(cls.startAt, cls.endAt, cls.title)
                }
            }
            month = month.plusMonths(1)
        }
        val tasks = taskRepository.observeTasks().first()
        for (t in tasks) {
            val due = t.dueAt
            if (!t.isCompleted && due != null) {
                val d = due.toLocalDate()
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    out += BusyInterval(due, due.plusMinutes(30), t.title)
                }
            }
        }
        return out
    }

    suspend fun markBlockDone(id: Long, actualMinutes: Int) {
        val now = System.currentTimeMillis()
        planBlockDao.updateStatus(id, PlanBlockStatus.DONE.name, now)
    }
}
