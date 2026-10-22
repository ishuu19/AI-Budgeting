package com.ledgerai.app.domain.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TripsTest {
    private val start = LocalDate.of(2026, 11, 20)
    private val end = LocalDate.of(2026, 11, 24)

    @Test
    fun statusFollowsTheDates() {
        assertEquals(TripStatus.PLANNED, Trips.status(start, end, LocalDate.of(2026, 11, 19)))
        assertEquals(TripStatus.ONGOING, Trips.status(start, end, start))
        assertEquals(TripStatus.ONGOING, Trips.status(start, end, end))
        assertEquals(TripStatus.DONE, Trips.status(start, end, LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun dayNumberIsOneBasedAndNullOutsideTheRange() {
        assertEquals(1, Trips.dayNumber(start, end, start))
        assertEquals(5, Trips.dayNumber(start, end, end))
        assertNull(Trips.dayNumber(start, end, LocalDate.of(2026, 11, 25)))
        assertEquals(5, Trips.length(start, end))
    }

    @Test
    fun sameDayTripIsValidAndOneDayLong() {
        assertTrue(Trips.isValid(start, start))
        assertEquals(1, Trips.length(start, start))
        assertFalse(Trips.isValid(end, start))
    }

    @Test
    fun remindersAreAWeekAndADayBeforeAtNineAm() {
        val r = Trips.reminders(start, week = true, day = true)
        assertEquals(LocalDateTime.of(2026, 11, 13, 9, 0), r[0].second)
        assertEquals(LocalDateTime.of(2026, 11, 19, 9, 0), r[1].second)
        assertEquals(1, Trips.reminders(start, week = false, day = true).size)
    }
}
