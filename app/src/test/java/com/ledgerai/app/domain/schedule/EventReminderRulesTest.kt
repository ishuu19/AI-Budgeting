package com.ledgerai.app.domain.schedule

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import com.ledgerai.app.domain.model.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class EventReminderRulesTest {

    private val now = LocalDateTime.of(2026, 10, 8, 12, 0) // Thursday

    private fun task(start: LocalDateTime, vararg reminders: EventReminder) = CalendarEvent(
        id = 1,
        title = "t",
        startAt = start,
        kind = CalendarEventKind.TASK,
        reminders = reminders.toList()
    )

    @Test
    fun cap_keepsOnlyTenReminders() {
        val many = (1..25).map { EventReminder(label = "r$it", offsetMinutes = it) }
        val capped = EventReminderRules.cap(many)
        assertEquals(MAX_REMINDERS_PER_EVENT, capped.size)
        assertEquals("r1", capped.first().label)
        assertEquals("r10", capped.last().label)
        assertEquals(3, EventReminderRules.cap(many.take(3)).size)
    }

    @Test
    fun acceptPulled_rejectsActiveReminderOverTheCapButAllowsTombstones() {
        assertTrue(EventReminderRules.acceptPulled(activeCount = 9, incomingDeleted = false))
        assertFalse(EventReminderRules.acceptPulled(activeCount = 10, incomingDeleted = false))
        assertTrue(EventReminderRules.acceptPulled(activeCount = 10, incomingDeleted = true))
    }

    @Test
    fun defaultReminders_areTenMinutesOneHourThreeHours() {
        assertEquals(listOf(10, 60, 180), defaultEventReminders().map { it.offsetMinutes })
        assertTrue(allBeforeEventOptions().any { it.second == 0L && it.first == "At time" })
    }

    @Test
    fun nextFire_oneOffOffsetBeforeStart() {
        val event = task(now.plusHours(5), EventReminder(label = "1h", offsetMinutes = 60))
        assertEquals(now.plusHours(4), EventReminderRules.nextFire(event, event.reminders[0], now))
    }

    @Test
    fun nextFire_atTimeFiresAtStartAndPastIsNull() {
        val future = task(now.plusHours(1), EventReminder(label = "At time", offsetMinutes = 0))
        assertEquals(now.plusHours(1), EventReminderRules.nextFire(future, future.reminders[0], now))
        val past = task(now.minusMinutes(1), EventReminder(label = "At time", offsetMinutes = 0))
        assertNull(EventReminderRules.nextFire(past, past.reminders[0], now))
    }

    @Test
    fun nextFire_absoluteReminderFiresOnce() {
        val event = task(now.plusDays(2), EventReminder(label = "x", remindAt = now.plusHours(3)))
        assertEquals(now.plusHours(3), EventReminderRules.nextFire(event, event.reminders[0], now))
        assertNull(EventReminderRules.nextFire(event, event.reminders[0], now.plusHours(4)))
    }

    @Test
    fun nextFire_skipsCompletedDisabledUndatedAndAlarms() {
        val r = EventReminder(label = "10", offsetMinutes = 10)
        val base = task(now.plusHours(2), r)
        assertNull(EventReminderRules.nextFire(base.copy(isCompleted = true), r, now))
        assertNull(EventReminderRules.nextFire(base.copy(isEnabled = false), r, now))
        assertNull(EventReminderRules.nextFire(base.copy(hasDate = false), r, now))
        assertNull(EventReminderRules.nextFire(base, r.copy(isEnabled = false), now))
        assertNull(EventReminderRules.nextFire(base.copy(kind = CalendarEventKind.ALARM), r, now))
    }

    @Test
    fun nextFire_weeklyClassFollowsNextOccurrenceAndHonoursExceptions() {
        // Mondays 09:00, started well before now. Next Monday after Thu 2026-10-08 is 10-12.
        val weekly = EventRecurrence(
            frequency = RecurrenceFrequency.WEEKLY,
            weekDays = setOf(DayOfWeek.MONDAY.value),
            excludedDates = setOf(LocalDate.of(2026, 10, 12))
        )
        val r = EventReminder(label = "10", offsetMinutes = 10)
        val cls = CalendarEvent(
            id = 7,
            title = "Calculus",
            startAt = LocalDateTime.of(2026, 9, 7, 9, 0),
            endAt = LocalDateTime.of(2026, 9, 7, 10, 0),
            kind = CalendarEventKind.CLASS,
            recurrence = weekly,
            reminders = listOf(r)
        )
        // 10-12 is excluded, so the first reminder is for 10-19 09:00
        assertEquals(LocalDateTime.of(2026, 10, 19, 8, 50), EventReminderRules.nextFire(cls, r, now))
        val noException = cls.copy(recurrence = weekly.copy(excludedDates = emptySet()))
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 50), EventReminderRules.nextFire(noException, r, now))
    }

    @Test
    fun nextFire_weeklyEventWithUntilInThePastNeverFires() {
        val r = EventReminder(label = "10", offsetMinutes = 10)
        val ended = CalendarEvent(
            id = 7,
            title = "Old",
            startAt = LocalDateTime.of(2026, 9, 7, 9, 0),
            kind = CalendarEventKind.CLASS,
            recurrence = EventRecurrence(
                frequency = RecurrenceFrequency.WEEKLY,
                weekDays = setOf(1),
                until = LocalDate.of(2026, 10, 1)
            ),
            reminders = listOf(r)
        )
        assertNull(EventReminderRules.nextFire(ended, r, now))
    }

    @Test
    fun nextFire_yearlyBirthdayWithWeekBeforeReminderIsFoundBeyondShortWindow() {
        val r = EventReminder(label = "1 week", offsetMinutes = 10_080)
        val birthday = CalendarEvent(
            id = 9,
            title = "Birthday",
            startAt = LocalDateTime.of(2020, 1, 15, 9, 0),
            recurrence = EventRecurrence(frequency = RecurrenceFrequency.YEARLY),
            reminders = listOf(r)
        )
        assertEquals(LocalDateTime.of(2027, 1, 8, 9, 0), EventReminderRules.nextFire(birthday, r, now))
    }

    @Test
    fun alarmDays_maskRoundTrip() {
        assertEquals(setOf(1, 2, 3, 4, 5), AlarmDays.toIsoDays(62))
        assertEquals(setOf(6, 7), AlarmDays.toIsoDays(65))
        assertEquals(62, AlarmDays.fromIsoDays(setOf(1, 2, 3, 4, 5)))
        assertEquals(127, AlarmDays.fromIsoDays((1..7).toSet()))
        assertEquals(0, AlarmDays.fromIsoDays(emptySet()))
        (0..127).forEach { mask -> assertEquals(mask, AlarmDays.fromIsoDays(AlarmDays.toIsoDays(mask))) }
    }
}
