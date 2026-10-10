package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Two items that overlap in time. */
data class ScheduleConflict(val first: CalendarEvent, val second: CalendarEvent)

/**
 * Rule-based schedule checks and suggestions: clashes, free gaps, bill reminders, exam prep and study blocks.
 * Pure functions over already loaded data, so the cloud is only a fallback when this finds nothing.
 */
object ScheduleRules {

    private val DAY_START = LocalTime.of(8, 0)
    private val DAY_END = LocalTime.of(22, 0)
    private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun CalendarEvent.live() = isEnabled && hasDate && !allDay && !(isCompleted && kind == CalendarEventKind.TASK)

    /** Pairs of time-blocking items that overlap. Items that merely touch do not clash. */
    fun conflicts(events: List<CalendarEvent>): List<ScheduleConflict> {
        val blocking = events.filter { it.live() && it.kind.blocksTime && it.endAt.isAfter(it.startAt) }.sortedBy { it.startAt }
        val out = mutableListOf<ScheduleConflict>()
        for (i in blocking.indices) {
            for (j in i + 1 until blocking.size) {
                val a = blocking[i]
                val b = blocking[j]
                if (!b.startAt.isBefore(a.endAt)) break
                if (a.masterId == b.masterId && a.masterId != 0L) continue
                out += ScheduleConflict(a, b)
            }
        }
        return out
    }

    /** Free gaps of at least [minMinutes] on [day] between 08:00 and 22:00, starting no earlier than [from]. */
    fun freeGaps(
        events: List<CalendarEvent>,
        day: LocalDate,
        from: LocalDateTime? = null,
        minMinutes: Int = 30,
    ): List<Pair<LocalDateTime, LocalDateTime>> {
        val start = maxOf(day.atTime(DAY_START), from ?: day.atTime(DAY_START))
        val end = day.atTime(DAY_END)
        if (!start.isBefore(end)) return emptyList()
        val busy = events.filter { it.live() && it.kind.blocksTime && it.endAt.isAfter(start) && it.startAt.isBefore(end) }
            .map { maxOf(it.startAt, start) to minOf(maxOf(it.endAt, it.startAt.plusMinutes(1)), end) }
            .sortedBy { it.first }
        val gaps = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = start
        for ((s, e) in busy) {
            if (s.isAfter(cursor)) gaps += cursor to s
            if (e.isAfter(cursor)) cursor = e
        }
        if (cursor.isBefore(end)) gaps += cursor to end
        return gaps.filter { Duration.between(it.first, it.second).toMinutes() >= minMinutes }
    }

    private fun draft(type: String, title: String, at: LocalDateTime, reason: String) =
        ScheduleDraftDto(type = type, title = title, startAt = at.withSecond(0).withNano(0).format(ISO), reason = "Rules: $reason")

    private fun mentions(events: List<CalendarEvent>, vararg needles: String): Boolean =
        events.any { e -> needles.any { n -> n.isNotBlank() && e.title.contains(n, ignoreCase = true) } }

    /**
     * Up to [max] suggestions for the next two weeks, most urgent first: clashes, bill reminders,
     * exam prep, then a study block on class days.
     */
    fun suggest(now: LocalDateTime, events: List<CalendarEvent>, bills: List<Bill>, max: Int = 5): List<ScheduleDraftDto> {
        val today = now.toLocalDate()
        val out = mutableListOf<ScheduleDraftDto>()
        val horizon = today.plusDays(14)
        val upcoming = events.filter { it.live() && !it.startAt.isBefore(now.minusHours(1)) && !it.startAt.toLocalDate().isAfter(horizon) }

        // 1. Clashes.
        conflicts(upcoming).filter { !it.second.startAt.isBefore(now) }.take(2).forEach { c ->
            val at = maxOf(now.plusMinutes(30), c.first.startAt.minusHours(2))
            out += draft(
                "TASK", "Fix clash: ${c.first.title} and ${c.second.title}", at,
                "they overlap on ${c.second.startAt.format(SHORT)}"
            )
        }

        // 2. Bill reminders for bills due within a week that have no reminder yet.
        bills.filter { it.isActive && it.deletedAt == null && !it.nextDueDate.isAfter(today.plusDays(7)) }
            .sortedBy { it.nextDueDate }
            .forEach { b ->
                if (mentions(events, b.name)) return@forEach
                val due = b.nextDueDate
                val at = when {
                    due.isBefore(today) -> now.plusHours(1)
                    due == today -> maxOf(now.plusHours(1), today.atTime(9, 0))
                    else -> maxOf(now.plusHours(1), due.minusDays(1).atTime(9, 0))
                }
                val whenText = if (due.isBefore(today)) "it is overdue" else "it is due ${due.format(SHORT)}"
                out += draft("TASK", "Pay ${b.name}", at, "$whenText")
            }

        // 3. Exam prep: a study task in the best gap before each exam.
        upcoming.filter { it.kind == CalendarEventKind.EXAM && it.startAt.isAfter(now) }.forEach { exam ->
            if (mentions(events, "study ${exam.title}", "revise ${exam.title}", "prep ${exam.title}", "prepare for ${exam.title}")) return@forEach
            val daysBefore = (1..3).map { exam.startAt.toLocalDate().minusDays(it.toLong()) }.filter { !it.isBefore(today) }
            for (day in daysBefore) {
                val gap = freeGaps(events, day, from = if (day == today) now.plusHours(1) else null, minMinutes = 60)
                    .maxByOrNull { Duration.between(it.first, it.second).toMinutes() } ?: continue
                out += draft("TASK", "Study for ${exam.title}", gap.first, "${exam.title} is on ${exam.startAt.format(SHORT)} and this slot is free")
                break
            }
        }

        // 4. A study block on class days with a long free evening.
        val classDays = upcoming.filter { it.kind == CalendarEventKind.CLASS }.map { it.startAt.toLocalDate() }.distinct().sorted()
        var blocks = 0
        for (day in classDays) {
            if (blocks >= 2) break
            if (events.any { it.live() && it.startAt.toLocalDate() == day && it.title.contains("study", ignoreCase = true) }) continue
            val afternoon = day.atTime(14, 0)
            val slot = freeGaps(events, day, from = maxOf(afternoon, if (day == today) now.plusHours(1) else afternoon), minMinutes = 90)
                .firstOrNull() ?: continue
            out += draft("EVENT", "Study block", slot.first, "you have class on ${day.format(SHORT)} and a free gap after it")
            blocks++
        }

        return out.distinctBy { (it.title?.lowercase() ?: "") + "|" + it.startAt }.take(max)
    }
}
