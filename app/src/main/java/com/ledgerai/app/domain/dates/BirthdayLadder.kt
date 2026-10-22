package com.ledgerai.app.domain.dates

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Year

/**
 * The nine birthday reminders (docs/life-os/app-design.md section 9.1).
 * Day-level rungs fire at the reminder hour on the date shown. Clock-level rungs count back from
 * the celebration time. All times are local wall-clock times.
 */
enum class Rung(val key: String) {
    MONTH_1("1mo"), WEEK_2("2w"), WEEK_1("1w"), DAY_3("3d"), DAY_1("1d"),
    HOUR_5("5h"), MIN_30("30m"), MIN_10("10m"), AT_TIME("at");

    /** True for rungs that fire at the reminder hour rather than relative to the celebration time. */
    val dayLevel: Boolean get() = ordinal <= DAY_1.ordinal
}

data class LadderSwitches(
    val month1: Boolean = true,
    val week2: Boolean = true,
    val week1: Boolean = true,
    val day3: Boolean = true,
    val day1: Boolean = true,
    val hour5: Boolean = true,
    val min30: Boolean = true,
    val min10: Boolean = true,
    val atTime: Boolean = true,
) {
    fun isOn(rung: Rung): Boolean = when (rung) {
        Rung.MONTH_1 -> month1
        Rung.WEEK_2 -> week2
        Rung.WEEK_1 -> week1
        Rung.DAY_3 -> day3
        Rung.DAY_1 -> day1
        Rung.HOUR_5 -> hour5
        Rung.MIN_30 -> min30
        Rung.MIN_10 -> min10
        Rung.AT_TIME -> atTime
    }
}

data class RungInstant(val rung: Rung, val at: LocalDateTime, val celebration: LocalDateTime)

object BirthdayLadder {

    const val DEFAULT_CELEBRATE_MINUTE = 0
    val DEFAULT_REMINDER_HOUR: LocalTime = LocalTime.of(9, 0)

    /** A reminder missed while the phone was off still fires once if it is less than this old. */
    val CATCH_UP_WINDOW: Duration = Duration.ofHours(6)

    /** 29 Feb in a year without one is celebrated on 28 Feb. */
    fun dateIn(year: Int, month: Int, day: Int): LocalDate {
        require(month in 1..12) { "month" }
        val last = Month.of(month).length(Year.isLeap(year.toLong()))
        require(day in 1..31) { "day" }
        return LocalDate.of(year, month, minOf(day, last))
    }

    fun celebration(date: LocalDate, celebrateMinute: Int): LocalDateTime =
        date.atStartOfDay().plusMinutes(celebrateMinute.coerceIn(0, 24 * 60 - 1).toLong())

    /** The next celebration that has not yet happened at [now]. */
    fun nextCelebration(month: Int, day: Int, celebrateMinute: Int, now: LocalDateTime): LocalDateTime {
        val thisYear = celebration(dateIn(now.year, month, day), celebrateMinute)
        return if (!thisYear.isBefore(now)) thisYear else celebration(dateIn(now.year + 1, month, day), celebrateMinute)
    }

    /** Every enabled rung for one celebration, earliest first. */
    fun instants(
        celebration: LocalDateTime,
        switches: LadderSwitches,
        reminderHour: LocalTime = DEFAULT_REMINDER_HOUR,
    ): List<RungInstant> {
        val date = celebration.toLocalDate()
        fun day(d: LocalDate) = d.atTime(reminderHour)
        return Rung.entries
            .filter { switches.isOn(it) }
            .map { rung ->
                val at = when (rung) {
                    // minusMonths keeps the clock time and clamps 31 Mar to 28 or 29 Feb.
                    Rung.MONTH_1 -> day(date.minusMonths(1))
                    Rung.WEEK_2 -> day(date.minusDays(14))
                    Rung.WEEK_1 -> day(date.minusDays(7))
                    Rung.DAY_3 -> day(date.minusDays(3))
                    Rung.DAY_1 -> day(date.minusDays(1))
                    Rung.HOUR_5 -> celebration.minusHours(5)
                    Rung.MIN_30 -> celebration.minusMinutes(30)
                    Rung.MIN_10 -> celebration.minusMinutes(10)
                    Rung.AT_TIME -> celebration
                }
                RungInstant(rung, at, celebration)
            }
            .sortedBy { it.at }
    }

    /**
     * What to schedule now: every rung still in the future, plus at most one catch-up (the most recent
     * rung that fell inside [CATCH_UP_WINDOW]) when [allowCatchUp] is true. Older missed rungs are dropped.
     */
    fun pending(all: List<RungInstant>, now: LocalDateTime, allowCatchUp: Boolean = true): List<RungInstant> {
        val future = all.filter { !it.at.isBefore(now) }
        if (!allowCatchUp) return future
        val missed = all
            .filter { it.at.isBefore(now) && Duration.between(it.at, now) <= CATCH_UP_WINDOW }
            .maxByOrNull { it.at }
        return if (missed == null) future else listOf(missed) + future
    }

    /** Age being turned on this celebration, or null when the birth year is unknown. */
    fun turning(birthYear: Int?, celebration: LocalDateTime): Int? =
        birthYear?.let { celebration.year - it }?.takeIf { it > 0 }

    /** Short distance text for a notification body: "in 3 days", "tomorrow", "now". */
    fun distance(rung: Rung): String = when (rung) {
        Rung.MONTH_1 -> "in 1 month"
        Rung.WEEK_2 -> "in 2 weeks"
        Rung.WEEK_1 -> "in 1 week"
        Rung.DAY_3 -> "in 3 days"
        Rung.DAY_1 -> "tomorrow"
        Rung.HOUR_5 -> "in 5 hours"
        Rung.MIN_30 -> "in 30 minutes"
        Rung.MIN_10 -> "in 10 minutes"
        Rung.AT_TIME -> "now"
    }

    fun body(rung: Rung, birthYear: Int?, celebration: LocalDateTime): String {
        val age = turning(birthYear, celebration)
        return distance(rung) + (age?.let { " · turns $it" } ?: "")
    }
}
