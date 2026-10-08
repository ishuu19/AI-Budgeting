package com.ledgerai.app.service

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure next-trigger calculation for alarms (testable without Android AlarmManager).
 * Weekday bitmask: Sun=1 … Sat=64; 0 = one-shot next occurrence.
 */
object AlarmTriggerCalc {

    fun nextTriggerMillis(
        time: LocalTime,
        repeatDays: Int,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val mask = repeatDays

        if (mask == 0) {
            var dateTime = LocalDateTime.of(now.toLocalDate(), time)
            if (!dateTime.isAfter(now)) {
                dateTime = dateTime.plusDays(1)
            }
            return dateTime.atZone(zone).toInstant().toEpochMilli()
        }

        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if ((mask and dayBit(date.dayOfWeek)) == 0) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(now)) {
                return candidate.atZone(zone).toInstant().toEpochMilli()
            }
        }

        return LocalDateTime.of(now.toLocalDate().plusDays(7), time)
            .atZone(zone).toInstant().toEpochMilli()
    }

    fun dayBit(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 4
        DayOfWeek.WEDNESDAY -> 8
        DayOfWeek.THURSDAY -> 16
        DayOfWeek.FRIDAY -> 32
        DayOfWeek.SATURDAY -> 64
    }
}
