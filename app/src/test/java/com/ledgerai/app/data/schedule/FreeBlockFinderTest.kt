package com.ledgerai.app.data.schedule

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FreeBlockFinderTest {

    @Test
    fun findsGapAfterBusyBlock() {
        val day = LocalDate.of(2026, 10, 8)
        val busy = listOf(
            BusyInterval(
                LocalDateTime.of(day.year, day.month, day.dayOfMonth, 9, 0),
                LocalDateTime.of(day.year, day.month, day.dayOfMonth, 11, 0),
                "Lecture"
            )
        )
        val slots = FreeBlockFinder.findSlots(
            rangeStart = day,
            rangeEnd = day,
            busy = busy,
            totalMinutesNeeded = 50,
            sessionLenMinutes = 50,
            deadline = null
        )
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.first().start.hour >= 11)
    }
}
