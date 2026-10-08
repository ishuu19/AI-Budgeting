package com.ledgerai.app.domain.schedule

import com.ledgerai.app.domain.model.EventReminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Standard lead times before an event (all strictly before the event time). */
val DEFAULT_BEFORE_EVENT_MINUTES: List<Long> = listOf(10L, 60L, 180L)

fun defaultBeforeEventOptions(): List<Pair<String, Long>> = listOf(
    "10 min before" to 10L,
    "1 hour before" to 60L,
    "3 hours before" to 180L
)

/** Extra offsets users can add beyond the defaults. */
fun extraBeforeEventOptions(): List<Pair<String, Long>> = listOf(
    "At time" to 0L,
    "5 min before" to 5L,
    "15 min before" to 15L,
    "30 min before" to 30L,
    "2 hours before" to 120L,
    "1 day before" to 1_440L,
    "2 days before" to 2_880L,
    "1 week before" to 10_080L
)

fun allBeforeEventOptions(): List<Pair<String, Long>> =
    defaultBeforeEventOptions() + extraBeforeEventOptions().filter { ( _, m) ->
        m !in DEFAULT_BEFORE_EVENT_MINUTES
    }

/**
 * When no calendar date was chosen, treat the event as today at [time].
 * Returns null only when [explicitNoDate] (user cleared the date on edit).
 */
fun resolveEventDateTime(
    date: LocalDate?,
    time: LocalTime,
    isNew: Boolean,
    explicitNoDate: Boolean
): LocalDateTime? = when {
    date != null -> LocalDateTime.of(date, time)
    isNew -> LocalDateTime.of(LocalDate.now(), time)
    explicitNoDate -> null
    else -> LocalDateTime.of(LocalDate.now(), time)
}

fun labelForMinutesBefore(minutes: Long): String = when (minutes) {
    0L -> "At time"
    60L -> "1 hour before"
    120L -> "2 hours before"
    180L -> "3 hours before"
    1_440L -> "1 day before"
    2_880L -> "2 days before"
    10_080L -> "1 week before"
    else -> "$minutes min before"
}

/** Offsets applied to a new event unless the user changes them. */
fun defaultEventReminders(): List<EventReminder> =
    defaultBeforeEventOptions().map { (label, minutes) ->
        EventReminder(label = label, offsetMinutes = minutes.toInt())
    }
