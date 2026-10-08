package com.ledgerai.app.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalTime

class ScheduleTimeParserTest {

    @Test
    fun parses24Hour() {
        assertEquals(LocalTime.of(9, 30), ScheduleTimeParser.parseTime("9:30"))
        assertEquals(LocalTime.of(14, 5), ScheduleTimeParser.parseTime("14:05"))
    }

    @Test
    fun parsesAmPm() {
        assertEquals(LocalTime.of(9, 0), ScheduleTimeParser.parseTime("9:00 AM"))
        assertEquals(LocalTime.of(21, 15), ScheduleTimeParser.parseTime("9:15 PM"))
        assertEquals(LocalTime.of(12, 0), ScheduleTimeParser.parseTime("12:00 PM"))
        assertEquals(LocalTime.of(0, 0), ScheduleTimeParser.parseTime("12:00 AM"))
    }

    @Test
    fun parsesCompact() {
        assertEquals(LocalTime.of(9, 30), ScheduleTimeParser.parseTime("0930"))
    }

    @Test
    fun parsesRangeWithDash() {
        val range = ScheduleTimeParser.parseTimeRange("9:00 AM - 10:30 AM")
        assertNotNull(range)
        assertEquals(LocalTime.of(9, 0), range!!.first)
        assertEquals(LocalTime.of(10, 30), range.second)
    }

    @Test
    fun parsesRange24h() {
        val range = ScheduleTimeParser.parseTimeRange("09:00–10:30")
        assertNotNull(range)
        assertEquals(LocalTime.of(9, 0), range!!.first)
        assertEquals(LocalTime.of(10, 30), range.second)
    }
}
