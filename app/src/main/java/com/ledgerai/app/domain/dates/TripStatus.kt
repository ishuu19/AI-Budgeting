package com.ledgerai.app.domain.dates

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Derived from dates only, never stored, so a timezone change cannot leave it stale. */
enum class TripStatus { PLANNED, ONGOING, DONE }

object Trips {

    fun status(start: LocalDate, end: LocalDate, today: LocalDate): TripStatus = when {
        today.isBefore(start) -> TripStatus.PLANNED
        today.isAfter(end) -> TripStatus.DONE
        else -> TripStatus.ONGOING
    }

    /** 1-based day of the trip, or null when today is outside the range. */
    fun dayNumber(start: LocalDate, end: LocalDate, today: LocalDate): Int? =
        if (today.isBefore(start) || today.isAfter(end)) null
        else (ChronoUnit.DAYS.between(start, today) + 1).toInt()

    fun length(start: LocalDate, end: LocalDate): Int = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    fun daysUntil(start: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, start)

    fun isValid(start: LocalDate, end: LocalDate): Boolean = !end.isBefore(start)

    /** Reminder instants: 1 week and 1 day before the start, at the reminder hour. */
    fun reminders(
        start: LocalDate,
        week: Boolean,
        day: Boolean,
        reminderHour: LocalTime = BirthdayLadder.DEFAULT_REMINDER_HOUR,
    ): List<Pair<String, LocalDateTime>> = buildList {
        if (week) add("1w" to start.minusWeeks(1).atTime(reminderHour))
        if (day) add("1d" to start.minusDays(1).atTime(reminderHour))
    }
}
