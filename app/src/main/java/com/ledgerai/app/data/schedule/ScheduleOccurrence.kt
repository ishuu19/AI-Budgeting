package com.ledgerai.app.data.schedule

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Next calendar fire time for a weekly slot (day 1 = Monday … 7 = Sunday). */
fun nextOccurrence(
    dayOfWeek: Int,
    time: LocalTime,
    from: LocalDateTime = LocalDateTime.now()
): LocalDateTime {
    val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
    var date = from.toLocalDate()
    while (true) {
        if (date.dayOfWeek == target) {
            val at = LocalDateTime.of(date, time)
            if (at.isAfter(from)) return at
        }
        date = date.plusDays(1)
    }
}
