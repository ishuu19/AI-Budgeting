package com.ledgerai.app.data.schedule

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TaskEventKind
import com.ledgerai.app.domain.model.TaskItem
import java.time.LocalDate
import java.time.YearMonth

/** Due tasks (not exams/events — those live in [calendar_events]). */
fun expandTasksForMonth(tasks: List<TaskItem>, month: LocalDate): List<CalendarEvent> {
    val ym = YearMonth.from(month)
    val start = ym.atDay(1).atStartOfDay()
    val end = ym.plusMonths(1).atDay(1).atStartOfDay()
    return tasks
        .asSequence()
        .filter { !it.isCompleted && it.dueAt != null }
        .filter { it.eventKind == TaskEventKind.TASK }
        .filter { task ->
            val due = task.dueAt!!
            !due.isBefore(start) && due.isBefore(end)
        }
        .map { task ->
            val due = task.dueAt!!
            CalendarEvent(
                id = -(task.id + 500_000L),
                title = task.title,
                taskId = task.id,
                location = task.location,
                startAt = due,
                endAt = due.plusMinutes(30),
                kind = CalendarEventKind.TASK
            )
        }
        .toList()
}
