package com.ledgerai.app.data.ai

import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.ScheduleRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.schedule.expandSlotsForMonth
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleContextBuilder @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val planRepo: PlanRepository,
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun build14DaySlice(now: LocalDate = LocalDate.now()): String {
        val end = now.plusDays(14)
        val events = calendarRepo.listNextDays(14)
        val slots = scheduleRepo.observeAllSlots().first()
        val tasks = taskRepo.observeTasks().first().filter { !it.isCompleted }
        val blocks = planRepo.observeBlocks().first()

        val lines = buildList {
            add("Schedule next 14 days from $now:")
            events.forEach { e ->
                add("event|${fmt.format(e.startAt)}|${e.title}|${e.kind}")
            }
            var day = now
            while (!day.isAfter(end)) {
                expandSlotsForMonth(slots, day).filter { it.startAt.toLocalDate() == day }
                    .forEach { c -> add("class|${fmt.format(c.startAt)}|${c.title}") }
                day = day.plusDays(1)
            }
            tasks.filter { it.dueAt != null }.forEach { t ->
                add("task|${fmt.format(t.dueAt)}|${t.title}")
            }
            blocks.forEach { b ->
                add("plan|${fmt.format(b.startAt)}|${b.title}|${b.kind}")
            }
        }
        return lines.joinToString("\n")
    }
}
