package com.ledgerai.app.data.schedule

import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RecurrenceExpanderTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private fun weeklyClass(r: EventRecurrence) = CalendarEvent(
        id = 42,
        title = "Calculus",
        startAt = LocalDateTime.of(monday, java.time.LocalTime.of(9, 0)),
        endAt = LocalDateTime.of(monday, java.time.LocalTime.of(10, 0)),
        kind = CalendarEventKind.CLASS,
        recurrence = r
    )

    private fun dates(events: List<CalendarEvent>) = events.map { it.startAt.toLocalDate() }

    @Test
    fun weekly_skipsExcludedDates() {
        val master = weeklyClass(
            EventRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                weekDays = setOf(1),
                excludedDates = setOf(LocalDate.of(2026, 9, 14))
            )
        )
        val out = RecurrenceExpander.expand(master, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(
            listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)),
            dates(out)
        )
    }

    @Test
    fun weekly_stopsAtUntilInclusive() {
        val master = weeklyClass(
            EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(1), until = LocalDate.of(2026, 9, 21))
        )
        val out = RecurrenceExpander.expand(master, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31))
        assertEquals(listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21)), dates(out))
    }

    @Test
    fun instances_keepSeriesLinkAndTimeOfDay() {
        val master = weeklyClass(EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(1)))
        val out = RecurrenceExpander.expand(master, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14))
        assertEquals(1, out.size)
        val instance = out.single()
        assertEquals(42L, instance.seriesEventId)
        assertEquals(42L, instance.masterId)
        assertEquals(LocalDate.of(2026, 9, 14), instance.instanceDate)
        assertEquals(DayOfWeek.MONDAY, instance.startAt.dayOfWeek)
        assertEquals(9, instance.startAt.hour)
        assertEquals(10, instance.endAt.hour)
        assertTrue(instance.id < 0)
    }

    @Test
    fun exceptionsSurviveEntityRoundTrip() {
        val master = weeklyClass(
            EventRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                weekDays = setOf(1, 3),
                until = LocalDate.of(2026, 12, 1),
                excludedDates = setOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12))
            )
        )
        val back = master.toEntity().toDomain()
        assertEquals(master.recurrence, back.recurrence)
        assertEquals(CalendarEventKind.CLASS, back.kind)
    }

    @Test
    fun migratedSlotShape_anchoredInThePastStillExpandsFromToday() {
        // What Migration6To7 produces: anchor four weeks before the current week, weekday list, exceptions.
        val anchor = LocalDate.of(2026, 9, 14)
        val master = CalendarEvent(
            id = 3,
            title = "Slot",
            startAt = LocalDateTime.of(anchor, java.time.LocalTime.of(9, 0)),
            endAt = LocalDateTime.of(anchor, java.time.LocalTime.of(10, 0)),
            kind = CalendarEventKind.CLASS,
            recurrence = EventRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                weekDays = setOf(1),
                excludedDates = setOf(LocalDate.of(2026, 10, 12))
            )
        )
        val out = RecurrenceExpander.expand(master, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 22))
        assertEquals(listOf(LocalDate.of(2026, 10, 19)), dates(out))
    }

    @Test
    fun nonRepeating_onlyInRange() {
        val single = CalendarEvent(title = "x", startAt = LocalDateTime.of(2026, 10, 8, 9, 0))
        assertEquals(1, RecurrenceExpander.expand(single, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8)).size)
        assertEquals(0, RecurrenceExpander.expand(single, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 20)).size)
    }
}
