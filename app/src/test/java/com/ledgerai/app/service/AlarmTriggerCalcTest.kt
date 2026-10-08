package com.ledgerai.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class AlarmTriggerCalcTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun dayBit_matchesSunToSatMask() {
        assertEquals(1, AlarmTriggerCalc.dayBit(DayOfWeek.SUNDAY))
        assertEquals(2, AlarmTriggerCalc.dayBit(DayOfWeek.MONDAY))
        assertEquals(4, AlarmTriggerCalc.dayBit(DayOfWeek.TUESDAY))
        assertEquals(8, AlarmTriggerCalc.dayBit(DayOfWeek.WEDNESDAY))
        assertEquals(16, AlarmTriggerCalc.dayBit(DayOfWeek.THURSDAY))
        assertEquals(32, AlarmTriggerCalc.dayBit(DayOfWeek.FRIDAY))
        assertEquals(64, AlarmTriggerCalc.dayBit(DayOfWeek.SATURDAY))
    }

    @Test
    fun oneShot_sameDayWhenStillAhead() {
        // Wednesday 2026-10-07 08:00; alarm at 09:00 → today
        val now = LocalDateTime.of(2026, 10, 7, 8, 0)
        val trigger = AlarmTriggerCalc.nextTriggerMillis(
            time = LocalTime.of(9, 0),
            repeatDays = 0,
            now = now,
            zone = zone,
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 7, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun oneShot_rollsToTomorrowWhenPast() {
        val now = LocalDateTime.of(2026, 10, 7, 10, 0)
        val trigger = AlarmTriggerCalc.nextTriggerMillis(
            time = LocalTime.of(9, 0),
            repeatDays = 0,
            now = now,
            zone = zone,
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 8, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun repeatDays_weekdays_skipsWeekend() {
        // Saturday 2026-10-10 08:00; weekdays mask 62 → next Monday 09:00
        val now = LocalDateTime.of(2026, 10, 10, 8, 0)
        val weekdays = 2 or 4 or 8 or 16 or 32
        val trigger = AlarmTriggerCalc.nextTriggerMillis(
            time = LocalTime.of(9, 0),
            repeatDays = weekdays,
            now = now,
            zone = zone,
        )
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2026, 10, 12).dayOfWeek)
        assertEquals(
            LocalDateTime.of(2026, 10, 12, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun repeatDays_sameDayStillAhead() {
        // Friday 2026-10-09 07:00; Friday bit only → today 08:00
        val now = LocalDateTime.of(2026, 10, 9, 7, 0)
        val friday = AlarmTriggerCalc.dayBit(DayOfWeek.FRIDAY)
        val trigger = AlarmTriggerCalc.nextTriggerMillis(
            time = LocalTime.of(8, 0),
            repeatDays = friday,
            now = now,
            zone = zone,
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 9, 8, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun repeatDays_sameDayPast_goesNextWeek() {
        // Friday 2026-10-09 09:00; Friday-only alarm at 08:00 → next Friday
        val now = LocalDateTime.of(2026, 10, 9, 9, 0)
        val friday = AlarmTriggerCalc.dayBit(DayOfWeek.FRIDAY)
        val trigger = AlarmTriggerCalc.nextTriggerMillis(
            time = LocalTime.of(8, 0),
            repeatDays = friday,
            now = now,
            zone = zone,
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 16, 8, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }
}
