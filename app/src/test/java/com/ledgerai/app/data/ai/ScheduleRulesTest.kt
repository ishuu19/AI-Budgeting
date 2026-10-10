package com.ledgerai.app.data.ai

import com.ledgerai.app.data.schedule.TimetableRules
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleRulesTest {

    private val now = LocalDateTime.of(2026, 10, 14, 12, 0) // Wednesday
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)

    private fun ev(title: String, start: LocalDateTime, end: LocalDateTime, kind: CalendarEventKind = CalendarEventKind.EVENT, id: Long = 0) =
        CalendarEvent(id = id, title = title, startAt = start, endAt = end, kind = kind)

    // ─── conflicts ───────────────────────────────────────────────────────────

    @Test
    fun overlappingEventsClash() {
        val c = ScheduleRules.conflicts(listOf(ev("A", at(15, 10), at(15, 11)), ev("B", at(15, 10, 30), at(15, 12))))
        assertEquals(1, c.size)
        assertEquals("A", c[0].first.title)
    }

    @Test
    fun touchingEventsDoNotClash() {
        assertTrue(ScheduleRules.conflicts(listOf(ev("A", at(15, 10), at(15, 11)), ev("B", at(15, 11), at(15, 12)))).isEmpty())
    }

    @Test
    fun contained_eventClashes() {
        assertEquals(1, ScheduleRules.conflicts(listOf(ev("A", at(15, 9), at(15, 13)), ev("B", at(15, 10), at(15, 11)))).size)
    }

    @Test
    fun tasksAndAlarmsNeverClash() {
        val list = listOf(
            ev("A", at(15, 10), at(15, 11)),
            ev("T", at(15, 10), at(15, 10), CalendarEventKind.TASK),
            ev("W", at(15, 10), at(15, 10), CalendarEventKind.ALARM),
        )
        assertTrue(ScheduleRules.conflicts(list).isEmpty())
    }

    @Test
    fun disabledEventsAreIgnored() {
        val off = ev("B", at(15, 10, 30), at(15, 12)).copy(isEnabled = false)
        assertTrue(ScheduleRules.conflicts(listOf(ev("A", at(15, 10), at(15, 11)), off)).isEmpty())
    }

    @Test
    fun differentDaysDoNotClash() {
        assertTrue(ScheduleRules.conflicts(listOf(ev("A", at(15, 10), at(15, 11)), ev("B", at(16, 10), at(16, 11)))).isEmpty())
    }

    @Test
    fun threeWayOverlapGivesPairs() {
        val list = listOf(ev("A", at(15, 10), at(15, 12)), ev("B", at(15, 10, 30), at(15, 12)), ev("C", at(15, 11), at(15, 12)))
        assertEquals(3, ScheduleRules.conflicts(list).size)
    }

    @Test
    fun sameSeriesOccurrencesAreNotClashes() {
        val a = ev("Math", at(15, 10), at(15, 11), id = 5)
        val b = ev("Math", at(15, 10, 30), at(15, 11, 30), id = 5)
        assertTrue(ScheduleRules.conflicts(listOf(a, b)).isEmpty())
    }

    // ─── gaps ────────────────────────────────────────────────────────────────

    private val day = LocalDate.of(2026, 10, 15)

    @Test
    fun emptyDayIsOneBigGap() {
        val g = ScheduleRules.freeGaps(emptyList(), day)
        assertEquals(listOf(at(15, 8) to at(15, 22)), g)
    }

    @Test
    fun gapsAroundOneEvent() {
        val g = ScheduleRules.freeGaps(listOf(ev("A", at(15, 10), at(15, 12))), day)
        assertEquals(listOf(at(15, 8) to at(15, 10), at(15, 12) to at(15, 22)), g)
    }

    @Test
    fun shortGapsAreDropped() {
        val g = ScheduleRules.freeGaps(listOf(ev("A", at(15, 8, 10), at(15, 12)), ev("B", at(15, 12, 15), at(15, 22))), day)
        assertTrue(g.isEmpty())
    }

    @Test
    fun gapStartsFromNow() {
        val g = ScheduleRules.freeGaps(emptyList(), LocalDate.of(2026, 10, 14), from = now)
        assertEquals(listOf(at(14, 12) to at(14, 22)), g)
    }

    @Test
    fun overlappingBusyBlocksMerge() {
        val g = ScheduleRules.freeGaps(listOf(ev("A", at(15, 9), at(15, 11)), ev("B", at(15, 10), at(15, 13))), day)
        assertEquals(listOf(at(15, 8) to at(15, 9), at(15, 13) to at(15, 22)), g)
    }

    @Test
    fun lateNowLeavesNoGap() {
        assertTrue(ScheduleRules.freeGaps(emptyList(), LocalDate.of(2026, 10, 14), from = at(14, 22, 30)).isEmpty())
    }

    @Test
    fun minMinutesIsRespected() {
        val e = listOf(ev("A", at(15, 8, 45), at(15, 21, 15)))
        assertEquals(2, ScheduleRules.freeGaps(e, day, minMinutes = 45).size)
        assertEquals(0, ScheduleRules.freeGaps(e, day, minMinutes = 50).size)
    }

    // ─── suggestions ─────────────────────────────────────────────────────────

    @Test
    fun billDueSoonGetsAReminder() {
        val out = ScheduleRules.suggest(now, emptyList(), listOf(Bill(name = "Rent", amount = 500.0, nextDueDate = LocalDate.of(2026, 10, 17))))
        assertEquals(1, out.size)
        assertEquals("Pay Rent", out[0].title)
        assertEquals("TASK", out[0].type)
        assertEquals("2026-10-16T09:00:00", out[0].startAt)
        assertTrue(out[0].reason!!.startsWith("Rules"))
    }

    @Test
    fun overdueBillIsRemindedWithinTheHour() {
        val out = ScheduleRules.suggest(now, emptyList(), listOf(Bill(name = "Internet", amount = 30.0, nextDueDate = LocalDate.of(2026, 10, 10))))
        assertEquals("2026-10-14T13:00:00", out[0].startAt)
        assertTrue(out[0].reason!!.contains("overdue"))
    }

    @Test
    fun billDueTodayIsRemindedLater() {
        val out = ScheduleRules.suggest(now, emptyList(), listOf(Bill(name = "Rent", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 14))))
        assertEquals("2026-10-14T13:00:00", out[0].startAt)
    }

    @Test
    fun farBillIsSkipped() {
        assertTrue(ScheduleRules.suggest(now, emptyList(), listOf(Bill(name = "Rent", amount = 1.0, nextDueDate = LocalDate.of(2026, 11, 20)))).isEmpty())
    }

    @Test
    fun billWithExistingReminderIsSkipped() {
        val existing = listOf(ev("Pay Rent", at(16, 9), at(16, 9), CalendarEventKind.TASK))
        assertTrue(ScheduleRules.suggest(now, existing, listOf(Bill(name = "Rent", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 17)))).isEmpty())
    }

    @Test
    fun inactiveBillIsSkipped() {
        assertTrue(ScheduleRules.suggest(now, emptyList(), listOf(Bill(name = "Old", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 15), isActive = false))).isEmpty())
    }

    @Test
    fun examGetsAStudyTask() {
        val exam = ev("Chemistry", at(20, 9), at(20, 11), CalendarEventKind.EXAM)
        val out = ScheduleRules.suggest(now, listOf(exam), emptyList())
        assertEquals("Study for Chemistry", out[0].title)
        assertEquals("2026-10-19T08:00:00", out[0].startAt)
        assertTrue(out[0].reason!!.contains("Chemistry"))
    }

    @Test
    fun examPrepUsesAFreeSlotNotABusyOne() {
        val exam = ev("Chemistry", at(20, 9), at(20, 11), CalendarEventKind.EXAM)
        val busy = ev("Shift", at(19, 8), at(19, 20))
        val out = ScheduleRules.suggest(now, listOf(exam, busy), emptyList())
        assertEquals("Study for Chemistry", out[0].title)
        assertTrue(out[0].startAt!!.startsWith("2026-10-19T20") || out[0].startAt!!.startsWith("2026-10-18"))
    }

    @Test
    fun examWithPrepAlreadyPlannedIsSkipped() {
        val exam = ev("Chemistry", at(20, 9), at(20, 11), CalendarEventKind.EXAM)
        val prep = ev("Study Chemistry", at(18, 16), at(18, 17), CalendarEventKind.TASK)
        assertTrue(ScheduleRules.suggest(now, listOf(exam, prep), emptyList()).isEmpty())
    }

    @Test
    fun pastExamIsSkipped() {
        assertTrue(ScheduleRules.suggest(now, listOf(ev("Old", at(10, 9), at(10, 11), CalendarEventKind.EXAM)), emptyList()).isEmpty())
    }

    @Test
    fun classDayGetsAStudyBlock() {
        val cls = ev("Math", at(15, 10), at(15, 11, 30), CalendarEventKind.CLASS)
        val out = ScheduleRules.suggest(now, listOf(cls), emptyList())
        assertEquals("Study block", out[0].title)
        assertEquals("EVENT", out[0].type)
        val start = LocalDateTime.parse(out[0].startAt!!)
        assertTrue(start.toLocalTime() >= LocalTime.of(14, 0))
        assertEquals(15, start.dayOfMonth)
    }

    @Test
    fun classDayWithStudyAlreadyIsSkipped() {
        val cls = ev("Math", at(15, 10), at(15, 11, 30), CalendarEventKind.CLASS)
        val study = ev("Study session", at(15, 16), at(15, 17))
        assertTrue(ScheduleRules.suggest(now, listOf(cls, study), emptyList()).isEmpty())
    }

    @Test
    fun studyBlocksAreCappedAtTwo() {
        val classes = (15..19).map { ev("Math", at(it, 10), at(it, 11), CalendarEventKind.CLASS) }
        assertEquals(2, ScheduleRules.suggest(now, classes, emptyList()).count { it.title == "Study block" })
    }

    @Test
    fun clashProducesAFixTask() {
        val out = ScheduleRules.suggest(now, listOf(ev("Dentist", at(15, 10), at(15, 11)), ev("Lab", at(15, 10, 30), at(15, 12))), emptyList())
        assertTrue(out[0].title!!.startsWith("Fix clash"))
        assertTrue(out[0].reason!!.contains("overlap"))
    }

    @Test
    fun clashComesBeforeBills() {
        val clash = listOf(ev("Dentist", at(15, 10), at(15, 11)), ev("Lab", at(15, 10, 30), at(15, 12)))
        val out = ScheduleRules.suggest(now, clash, listOf(Bill(name = "Rent", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 15))))
        assertTrue(out[0].title!!.startsWith("Fix clash"))
        assertEquals("Pay Rent", out[1].title)
    }

    @Test
    fun suggestionsAreCapped() {
        val bills = (1..9).map { Bill(name = "Bill$it", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 15)) }
        assertEquals(5, ScheduleRules.suggest(now, emptyList(), bills).size)
        assertEquals(3, ScheduleRules.suggest(now, emptyList(), bills, max = 3).size)
    }

    @Test
    fun emptyScheduleHasNoSuggestions() = assertTrue(ScheduleRules.suggest(now, emptyList(), emptyList()).isEmpty())

    @Test
    fun suggestionsAreDeterministic() {
        val exam = ev("Chemistry", at(20, 9), at(20, 11), CalendarEventKind.EXAM)
        assertEquals(ScheduleRules.suggest(now, listOf(exam), emptyList()), ScheduleRules.suggest(now, listOf(exam), emptyList()))
    }

    @Test
    fun everySuggestionHasAParsableStart() {
        val bills = listOf(Bill(name = "Rent", amount = 1.0, nextDueDate = LocalDate.of(2026, 10, 15)))
        val exam = ev("Chemistry", at(20, 9), at(20, 11), CalendarEventKind.EXAM)
        ScheduleRules.suggest(now, listOf(exam), bills).forEach {
            LocalDateTime.parse(it.startAt!!)
            assertFalse(it.title.isNullOrBlank())
        }
    }

    // ─── timetable text ──────────────────────────────────────────────────────

    @Test
    fun jsonTimetableIsReadByRules() {
        val json = """{"classes":[{"day":"Monday","dayOfWeek":1,"startTime":"09:00","endTime":"10:30","title":"Math","courseCode":"M1","location":"R1"}]}"""
        val rows = TimetableRules.parse(json)
        assertEquals(1, rows.size)
        assertEquals("Math", rows[0].title)
        assertEquals(LocalTime.of(9, 0), rows[0].startTime)
    }

    @Test
    fun csvTimetableIsReadByRules() {
        val csv = "day,start,end,title,code,room\nMonday,09:00,10:30,Math,M1,R1\nTuesday,11:00,12:00,Physics,P1,R2"
        val rows = TimetableRules.parse(csv)
        assertEquals(listOf("Math", "Physics"), rows.map { it.title })
        assertEquals(listOf(1, 2), rows.map { it.dayOfWeek })
    }

    @Test
    fun oneLinePerClassIsReadByRules() {
        val rows = TimetableRules.parse("Monday 9:00-10:30 Math\nWednesday 14:00-15:30 Physics")
        assertEquals(2, rows.size)
        assertEquals(3, rows[1].dayOfWeek)
    }

    @Test
    fun dayHeadingBlocksAreReadByRules() {
        val rows = TimetableRules.parseDayBlocks("Monday\n09:00-10:30 Math\n11:00-12:00 Physics\nTuesday:\n10:00-11:00 Chemistry")
        assertEquals(listOf("Math", "Physics", "Chemistry"), rows.map { it.title })
        assertEquals(listOf(1, 1, 2), rows.map { it.dayOfWeek })
    }

    @Test
    fun gibberishTimetableIsEmpty() = assertTrue(TimetableRules.parse("hello world nothing to see").isEmpty())

    @Test
    fun emptyTimetableIsEmpty() = assertTrue(TimetableRules.parse("  ").isEmpty())
}
