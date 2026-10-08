package com.ledgerai.app.data.schedule

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.ScheduleSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Expands weekly [ScheduleSlot] rows into concrete class events for a calendar month. */
fun expandSlotsForMonth(slots: List<ScheduleSlot>, month: LocalDate): List<CalendarEvent> {
    val ym = YearMonth.from(month)
    val start = ym.atDay(1)
    val end = ym.atEndOfMonth()
    val out = mutableListOf<CalendarEvent>()
    var day = start
    while (!day.isAfter(end)) {
        val dow = day.dayOfWeek.value // Mon=1 … Sun=7
        slots.filter { it.dayOfWeek == dow }.forEach { slot ->
            if (day in slot.skippedDates) return@forEach
            if (slot.recurrenceUntil != null && day.isAfter(slot.recurrenceUntil)) return@forEach
            val startAt = LocalDateTime.of(day, slot.startTime)
            val endAt = LocalDateTime.of(day, slot.endTime)
            out += CalendarEvent(
                id = -(slot.id * 1000L + day.dayOfMonth),
                title = slot.title,
                courseId = slot.courseId,
                scheduleSlotId = slot.id,
                location = slot.location,
                startAt = startAt,
                endAt = endAt,
                kind = CalendarEventKind.CLASS,
                instanceDate = day,
                recurrence = com.ledgerai.app.domain.model.EventRecurrence(
                    frequency = com.ledgerai.app.domain.model.RecurrenceFrequency.WEEKLY,
                    weekDays = setOf(slot.dayOfWeek)
                )
            )
        }
        day = day.plusDays(1)
    }
    return out.sortedBy { it.startAt }
}

fun expandSlotsForDay(slots: List<ScheduleSlot>, date: LocalDate): List<CalendarEvent> =
    expandSlotsForMonth(slots, date).filter { it.startAt.toLocalDate() == date }
