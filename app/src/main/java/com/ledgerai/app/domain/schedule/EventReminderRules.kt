package com.ledgerai.app.domain.schedule

import com.ledgerai.app.data.schedule.RecurrenceExpander
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Pure reminder rules shared by the repository, the scheduler, sync and tests. */
object EventReminderRules {

    /** Keeps the first [MAX_REMINDERS_PER_EVENT] reminders. */
    fun <T> cap(reminders: List<T>): List<T> = reminders.take(MAX_REMINDERS_PER_EVENT)

    /** A pulled reminder is accepted unless it is active and the event already has the maximum. */
    fun acceptPulled(activeCount: Int, incomingDeleted: Boolean): Boolean =
        incomingDeleted || activeCount < MAX_REMINDERS_PER_EVENT

    fun supportsReminders(kind: CalendarEventKind): Boolean = kind != CalendarEventKind.ALARM

    /**
     * Next time [reminder] should fire after [now], or null if it never will again.
     * Offset reminders follow each occurrence of a repeating event; absolute ones fire once.
     */
    fun nextFire(event: CalendarEvent, reminder: EventReminder, now: LocalDateTime): LocalDateTime? {
        if (!reminder.isEnabled || !event.isEnabled || !supportsReminders(event.kind) || !event.hasDate) {
            return null
        }
        val offset = reminder.offsetMinutes
            ?: return reminder.remindAt?.takeIf { it.isAfter(now) && !event.isCompleted }
        val recurrence = event.recurrence
        if (recurrence == null || !recurrence.repeats) {
            if (event.isCompleted) return null
            return event.startAt.minusMinutes(offset.toLong()).takeIf { it.isAfter(now) }
        }
        val from = now.toLocalDate()
        for (windowDays in longArrayOf(14, 90, 400)) {
            val next = RecurrenceExpander.expand(event, from, from.plusDays(windowDays))
                .map { it.startAt.minusMinutes(offset.toLong()) }
                .filter { it.isAfter(now) }
                .minOrNull()
            if (next != null) return next
        }
        return null
    }
}

/** Alarm repeat bitmask helpers. Mask: Sun=1, Mon=2, Tue=4 ... Sat=64. */
object AlarmDays {
    fun bit(day: DayOfWeek): Int = when (day) {
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 4
        DayOfWeek.WEDNESDAY -> 8
        DayOfWeek.THURSDAY -> 16
        DayOfWeek.FRIDAY -> 32
        DayOfWeek.SATURDAY -> 64
    }

    /** Mask to ISO weekdays (Mon=1 ... Sun=7). */
    fun toIsoDays(mask: Int): Set<Int> =
        DayOfWeek.entries.filter { mask and bit(it) != 0 }.map { it.value }.toSet()

    fun fromIsoDays(days: Set<Int>): Int =
        days.filter { it in 1..7 }.fold(0) { acc, d -> acc or bit(DayOfWeek.of(d)) }
}
