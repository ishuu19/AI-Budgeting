package com.ledgerai.app.domain.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BirthdayLadderTest {

    private fun at(m: Int, d: Int, h: Int, min: Int = 0, year: Int = 2026) = LocalDateTime.of(year, m, d, h, min)

    private fun ladder(celebration: LocalDateTime, switches: LadderSwitches = LadderSwitches()) =
        BirthdayLadder.instants(celebration, switches).associate { it.rung to it.at }

    @Test
    fun midnightCelebrationMatchesTheDesignTable() {
        val l = ladder(at(11, 14, 0))
        assertEquals(at(10, 14, 9), l[Rung.MONTH_1])
        assertEquals(at(10, 31, 9), l[Rung.WEEK_2])
        assertEquals(at(11, 7, 9), l[Rung.WEEK_1])
        assertEquals(at(11, 11, 9), l[Rung.DAY_3])
        assertEquals(at(11, 13, 9), l[Rung.DAY_1])
        assertEquals(at(11, 13, 19), l[Rung.HOUR_5])
        assertEquals(at(11, 13, 23, 30), l[Rung.MIN_30])
        assertEquals(at(11, 13, 23, 50), l[Rung.MIN_10])
        assertEquals(at(11, 14, 0), l[Rung.AT_TIME])
    }

    @Test
    fun partyTimeCountsClockRungsFromThatTime() {
        val l = ladder(at(11, 14, 18))
        assertEquals(at(11, 14, 13), l[Rung.HOUR_5])
        assertEquals(at(11, 14, 17, 30), l[Rung.MIN_30])
        assertEquals(at(11, 14, 17, 50), l[Rung.MIN_10])
        // Day-level rungs ignore the celebration time.
        assertEquals(at(11, 13, 9), l[Rung.DAY_1])
    }

    @Test
    fun aSwitchedOffRungNeverAppears() {
        val l = ladder(at(11, 14, 0), LadderSwitches(week1 = false, min10 = false))
        assertFalse(Rung.WEEK_1 in l)
        assertFalse(Rung.MIN_10 in l)
        assertEquals(7, l.size)
    }

    @Test
    fun instantsAreSortedEarliestFirst() {
        val all = BirthdayLadder.instants(at(11, 14, 0), LadderSwitches())
        assertEquals(all.sortedBy { it.at }, all)
        assertEquals(Rung.MONTH_1, all.first().rung)
        assertEquals(Rung.AT_TIME, all.last().rung)
    }

    @Test
    fun oneMonthBeforeThe31stClampsToTheLastDayOfTheShorterMonth() {
        assertEquals(at(2, 28, 9), ladder(at(3, 31, 0))[Rung.MONTH_1])
        assertEquals(at(2, 29, 9, year = 2028), ladder(at(3, 31, 0, year = 2028))[Rung.MONTH_1])
    }

    @Test
    fun feb29IsCelebratedOnFeb28InACommonYear() {
        assertEquals(LocalDate.of(2027, 2, 28), BirthdayLadder.dateIn(2027, 2, 29))
        assertEquals(LocalDate.of(2028, 2, 29), BirthdayLadder.dateIn(2028, 2, 29))
    }

    @Test
    fun nextCelebrationRollsToNextYearOnceTheMomentHasPassed() {
        val before = at(11, 13, 23, 59)
        val after = at(11, 14, 0, 1)
        assertEquals(at(11, 14, 0), BirthdayLadder.nextCelebration(11, 14, 0, before))
        assertEquals(at(11, 14, 0, year = 2027), BirthdayLadder.nextCelebration(11, 14, 0, after))
    }

    @Test
    fun pastRungsAreSkippedWhenTheBirthdayIsSavedLate() {
        val all = BirthdayLadder.instants(at(11, 14, 0), LadderSwitches())
        val now = at(11, 10, 8) // 4 days out: 1mo, 2w and 1w are long gone
        val pending = BirthdayLadder.pending(all, now, allowCatchUp = false)
        assertEquals(listOf(Rung.DAY_3, Rung.DAY_1, Rung.HOUR_5, Rung.MIN_30, Rung.MIN_10, Rung.AT_TIME), pending.map { it.rung })
    }

    @Test
    fun onlyTheMostRecentMissedRungCatchesUp_andOnlyWithinSixHours() {
        val all = BirthdayLadder.instants(at(11, 14, 0), LadderSwitches())
        // Phone was off from before 19:00 until 23:40: 5h and 30m were missed, 10m is still ahead.
        val now = at(11, 13, 23, 40)
        val pending = BirthdayLadder.pending(all, now)
        assertEquals(Rung.MIN_30, pending.first().rung)
        assertEquals(listOf(Rung.MIN_30, Rung.MIN_10, Rung.AT_TIME), pending.map { it.rung })
        // Two days later nothing catches up.
        val late = BirthdayLadder.pending(all, at(11, 16, 12))
        assertTrue(late.isEmpty())
    }

    @Test
    fun bodyAddsTheAgeOnlyWhenTheBirthYearIsKnown() {
        val c = at(11, 14, 0)
        assertEquals("in 3 days · turns 26", BirthdayLadder.body(Rung.DAY_3, 2000, c))
        assertEquals("in 3 days", BirthdayLadder.body(Rung.DAY_3, null, c))
        assertEquals("tomorrow", BirthdayLadder.body(Rung.DAY_1, null, c))
        assertNull(BirthdayLadder.turning(null, c))
    }

    @Test
    fun customReminderHourMovesDayLevelRungsOnly() {
        val l = BirthdayLadder.instants(at(11, 14, 0), LadderSwitches(), LocalTime.of(7, 30)).associate { it.rung to it.at }
        assertEquals(at(11, 13, 7, 30), l[Rung.DAY_1])
        assertEquals(at(11, 13, 19), l[Rung.HOUR_5])
    }
}
