package com.ledgerai.app.data.schedule

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object RecurrenceExpander {

    fun expand(
        master: CalendarEvent,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> {
        val recurrence = master.recurrence ?: EventRecurrence()
        if (!recurrence.repeats) {
            val d = master.startAt.toLocalDate()
            return if (!d.isBefore(rangeStart) && !d.isAfter(rangeEnd)) listOf(instance(master, d)) else emptyList()
        }
        return when (recurrence.frequency) {
            RecurrenceFrequency.SPECIFIC_DATES -> expandSpecificDates(master, recurrence, rangeStart, rangeEnd)
            RecurrenceFrequency.DAILY -> expandDaily(master, recurrence, rangeStart, rangeEnd)
            RecurrenceFrequency.WEEKLY -> expandWeekly(master, recurrence, rangeStart, rangeEnd)
            RecurrenceFrequency.MONTHLY -> expandMonthly(master, recurrence, rangeStart, rangeEnd)
            RecurrenceFrequency.YEARLY -> expandYearly(master, recurrence, rangeStart, rangeEnd)
            RecurrenceFrequency.NONE -> emptyList()
        }
    }

    private fun expandSpecificDates(
        master: CalendarEvent,
        recurrence: EventRecurrence,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> =
        recurrence.specificDates
            .filter { !it.isBefore(rangeStart) && !it.isAfter(rangeEnd) }
            .filter { recurrence.until == null || !it.isAfter(recurrence.until) }
            .filter { it !in recurrence.excludedDates }
            .sorted()
            .map { instance(master, it) }

    private fun expandDaily(
        master: CalendarEvent,
        recurrence: EventRecurrence,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        var day = master.startAt.toLocalDate()
        val interval = recurrence.interval.coerceAtLeast(1).toLong()
        if (day.isBefore(rangeStart)) {
            val daysBetween = ChronoUnit.DAYS.between(day, rangeStart)
            val steps = (daysBetween + interval - 1) / interval
            day = day.plusDays(steps * interval)
        }
        while (!day.isAfter(rangeEnd)) {
            if (recurrence.until != null && day.isAfter(recurrence.until)) break
            if (day !in recurrence.excludedDates && !day.isBefore(rangeStart)) {
                out += instance(master, day)
            }
            day = day.plusDays(interval)
        }
        return out
    }

    private fun expandWeekly(
        master: CalendarEvent,
        recurrence: EventRecurrence,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> {
        val days = recurrence.weekDays.ifEmpty {
            setOf(master.startAt.dayOfWeek.value)
        }
        val out = mutableListOf<CalendarEvent>()
        var day = rangeStart
        while (!day.isAfter(rangeEnd)) {
            if (recurrence.until != null && day.isAfter(recurrence.until)) break
            if (day.dayOfWeek.value in days &&
                !day.isBefore(master.startAt.toLocalDate()) &&
                day !in recurrence.excludedDates
            ) {
                val weeks = ChronoUnit.WEEKS.between(master.startAt.toLocalDate(), day)
                if (weeks % recurrence.interval.coerceAtLeast(1) == 0L) {
                    out += instance(master, day)
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    private fun expandMonthly(
        master: CalendarEvent,
        recurrence: EventRecurrence,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> {
        val anchor = master.startAt.toLocalDate()
        val out = mutableListOf<CalendarEvent>()
        var cursor = anchor.withDayOfMonth(1)
        if (cursor.isBefore(rangeStart)) {
            cursor = rangeStart.withDayOfMonth(1)
        }
        val dom = anchor.dayOfMonth
        while (!cursor.isAfter(rangeEnd)) {
            val candidate = runCatching { cursor.withDayOfMonth(dom.coerceAtMost(cursor.lengthOfMonth())) }
                .getOrNull() ?: cursor.withDayOfMonth(cursor.lengthOfMonth())
            if (!candidate.isBefore(anchor) &&
                !candidate.isBefore(rangeStart) &&
                !candidate.isAfter(rangeEnd)
            ) {
                if (recurrence.until == null || !candidate.isAfter(recurrence.until)) {
                    if (candidate !in recurrence.excludedDates) {
                        val months = ChronoUnit.MONTHS.between(
                            anchor.withDayOfMonth(1),
                            candidate.withDayOfMonth(1)
                        )
                        if (months % recurrence.interval.coerceAtLeast(1) == 0L) {
                            out += instance(master, candidate)
                        }
                    }
                }
            }
            cursor = cursor.plusMonths(1)
        }
        return out
    }

    private fun expandYearly(
        master: CalendarEvent,
        recurrence: EventRecurrence,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarEvent> {
        val anchor = master.startAt.toLocalDate()
        val out = mutableListOf<CalendarEvent>()
        var year = maxOf(anchor.year, rangeStart.year)
        val endYear = rangeEnd.year
        while (year <= endYear) {
            val candidate = runCatching { LocalDate.of(year, anchor.month, anchor.dayOfMonth) }.getOrNull()
            if (candidate != null &&
                !candidate.isBefore(anchor) &&
                !candidate.isBefore(rangeStart) &&
                !candidate.isAfter(rangeEnd)
            ) {
                if (recurrence.until == null || !candidate.isAfter(recurrence.until)) {
                    if (candidate !in recurrence.excludedDates) {
                        val years = (year - anchor.year).toLong()
                        if (years % recurrence.interval.coerceAtLeast(1) == 0L) {
                            out += instance(master, candidate)
                        }
                    }
                }
            }
            year++
        }
        return out
    }

    private fun instance(master: CalendarEvent, date: LocalDate): CalendarEvent {
        val start = LocalDateTime.of(date, master.startAt.toLocalTime())
        val end = LocalDateTime.of(date, master.endAt.toLocalTime()).let {
            if (it.isBefore(start)) it.plusDays(1) else it
        }
        val syntheticId = -(master.id * 10_000L + date.toEpochDay())
        return master.copy(
            id = syntheticId,
            startAt = start,
            endAt = end,
            instanceDate = date,
            seriesEventId = master.id.takeIf { it > 0 }
        )
    }
}
