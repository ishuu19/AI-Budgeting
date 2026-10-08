package com.ledgerai.app.data.schedule

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class BusyInterval(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val label: String = ""
)

data class FreeSlotOption(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val reason: String,
    val score: Int
)

data class FreeBlockFinderConfig(
    val wakeStart: LocalTime = LocalTime.of(7, 0),
    val wakeEnd: LocalTime = LocalTime.of(23, 0),
    val minBlockMinutes: Int = 25,
    val breakBetweenSessionsMinutes: Int = 10,
    val mealBuffersMinutes: Int = 30,
    val maxStudyMinutesPerDay: Int = 240
)

object FreeBlockFinder {

    fun findSlots(
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        busy: List<BusyInterval>,
        totalMinutesNeeded: Int,
        sessionLenMinutes: Int,
        deadline: LocalDate?,
        config: FreeBlockFinderConfig = FreeBlockFinderConfig()
    ): List<FreeSlotOption> {
        if (totalMinutesNeeded <= 0) return emptyList()
        val merged = mergeBusy(busy)
        val candidates = mutableListOf<FreeSlotOption>()
        var day = rangeStart
        while (!day.isAfter(rangeEnd)) {
            if (deadline != null && day.isAfter(deadline)) break
            val dayStart = LocalDateTime.of(day, config.wakeStart)
            val dayEnd = LocalDateTime.of(day, config.wakeEnd)
            val gaps = gapsForDay(dayStart, dayEnd, merged, config.minBlockMinutes)
            for (gap in gaps) {
                val durationMin = Duration.between(gap.start, gap.end).toMinutes().toInt()
                if (durationMin < config.minBlockMinutes) continue
                val usable = minOf(durationMin, sessionLenMinutes)
                val slotEnd = gap.start.plusMinutes(usable.toLong())
                val reason = gapReason(gap, merged)
                val score = scoreSlot(gap.start, usable, day, deadline, config)
                candidates.add(FreeSlotOption(gap.start, slotEnd, reason, score))
            }
            day = day.plusDays(1)
        }
        return rankAndTakeSessions(candidates, totalMinutesNeeded, sessionLenMinutes, config.breakBetweenSessionsMinutes)
    }

    private fun mergeBusy(busy: List<BusyInterval>): List<BusyInterval> {
        if (busy.isEmpty()) return emptyList()
        val sorted = busy.sortedBy { it.start }
        val out = mutableListOf<BusyInterval>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (!next.start.isAfter(cur.end)) {
                val end = if (next.end.isAfter(cur.end)) next.end else cur.end
                cur = cur.copy(end = end, label = cur.label.ifBlank { next.label })
            } else {
                out += cur
                cur = next
            }
        }
        out += cur
        return out
    }

    private data class Gap(val start: LocalDateTime, val end: LocalDateTime)

    private fun gapsForDay(
        dayStart: LocalDateTime,
        dayEnd: LocalDateTime,
        busy: List<BusyInterval>,
        minMinutes: Int
    ): List<Gap> {
        val dayBusy = busy.filter { it.end.isAfter(dayStart) && it.start.isBefore(dayEnd) }
            .map {
                BusyInterval(
                    start = maxOf(it.start, dayStart),
                    end = minOf(it.end, dayEnd),
                    label = it.label
                )
            }
        if (dayBusy.isEmpty()) {
            return listOf(Gap(dayStart, dayEnd))
        }
        val gaps = mutableListOf<Gap>()
        var cursor = dayStart
        for (b in dayBusy.sortedBy { it.start }) {
            if (b.start.isAfter(cursor)) {
                val gap = Gap(cursor, b.start)
                if (Duration.between(gap.start, gap.end).toMinutes() >= minMinutes) gaps += gap
            }
            if (b.end.isAfter(cursor)) cursor = b.end
        }
        if (cursor.isBefore(dayEnd)) {
            val gap = Gap(cursor, dayEnd)
            if (Duration.between(gap.start, gap.end).toMinutes() >= minMinutes) gaps += gap
        }
        return gaps
    }

    private fun gapReason(gap: Gap, busy: List<BusyInterval>): String {
        val before = busy.filter { it.end <= gap.start }.maxByOrNull { it.end }
        return when {
            before != null && before.label.isNotBlank() -> "After ${before.label}"
            before != null -> "After ${before.end.toLocalTime()}"
            else -> "Morning free"
        }
    }

    private fun scoreSlot(
        start: LocalDateTime,
        minutes: Int,
        day: LocalDate,
        deadline: LocalDate?,
        config: FreeBlockFinderConfig
    ): Int {
        var score = 50
        val hour = start.hour
        if (hour in 9..11) score += 15
        if (hour in 14..17) score += 10
        if (deadline != null) {
            val daysLeft = Duration.between(day.atStartOfDay(), deadline.atStartOfDay()).toDays()
            if (daysLeft <= 2) score += 20
            else if (daysLeft <= 7) score += 10
        }
        if (minutes >= config.minBlockMinutes * 2) score += 5
        return score
    }

    private fun rankAndTakeSessions(
        candidates: List<FreeSlotOption>,
        totalMinutesNeeded: Int,
        sessionLen: Int,
        breakMinutes: Int
    ): List<FreeSlotOption> {
        val ranked = candidates.sortedByDescending { it.score }
        val picked = mutableListOf<FreeSlotOption>()
        var remaining = totalMinutesNeeded
        val usedDays = mutableSetOf<LocalDate>()
        for (c in ranked) {
            if (remaining <= 0) break
            val day = c.start.toLocalDate()
            if (usedDays.count { it == day } >= 2) continue
            val sessionMin = minOf(sessionLen, remaining, Duration.between(c.start, c.end).toMinutes().toInt())
            if (sessionMin < 25) continue
            picked += c.copy(end = c.start.plusMinutes(sessionMin.toLong()))
            remaining -= sessionMin
            usedDays += day
        }
        return picked.take(5)
    }
}
