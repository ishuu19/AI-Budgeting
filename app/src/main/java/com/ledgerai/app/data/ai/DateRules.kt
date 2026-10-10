package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Dates, clock times, durations and repeat phrases. [QuickParse] delegates here. */
internal object DateRules {

    val WEEKDAY_NAMES = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )
    private const val WEEKDAY_ALT = "(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)"
    private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    const val MONTH_ALT =
        "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|" +
            "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val IN_N = Regex("""(?i)\b(?:in|after|within)\s+(\d{1,3}|an?)\s+(day|week|month|year)s?\b""")
    private val N_FROM_NOW = Regex("""(?i)\b(\d{1,3}|an?)\s+(day|week|month)s?\s+(?:from\s+(?:now|today)|later)\b""")
    private val COUPLE = Regex("""(?i)\bin\s+a\s+couple\s+of\s+(day|week|month)s?\b""")
    private val DAY_AFTER = Regex("""(?i)\b(?:the\s+)?day\s+after\s+(?:tomorrow|tmrw|tomorow)\b""")
    private val TOMORROW = Regex("""(?i)\b(?:tomorrow|tomorow|tmrw|tmr)\b""")
    private val END_OF = Regex("""(?i)\b(?:by\s+|at\s+)?(?:the\s+)?end\s+of\s+(?:the\s+)?(next\s+|this\s+)?(month|week|year)\b""")
    private val START_NEXT_MONTH = Regex("""(?i)\b(?:the\s+)?(?:start|beginning|first)\s+of\s+next\s+month\b""")
    private val ORD_NEXT_MONTH = Regex("""(?i)\b(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+of\s+next\s+month\b""")
    private val NEXT_MONTH = Regex("""(?i)\bnext\s+month\b""")
    private val WEEKEND = Regex("""(?i)\b(this|next|the|on\s+the)\s+weekend\b""")
    private val MONTH_DAY = Regex("""(?i)\b($MONTH_ALT)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?!\s*(?::|am|pm))""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH_ALT)\b""")
    private val ORDINAL_DAY = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)\b""")
    private val NEXT_WEEKDAY = Regex("""(?i)\bnext\s+($WEEKDAY_ALT)\b""")
    private val NUM_DATE = Regex("""\b(\d{1,2})[/.](\d{1,2})(?:[/.](\d{2,4}))?\b""")
    private val AGO = Regex("""(?i)\b(\d{1,3}|an?)\s+(day|week|month)s?\s+ago\b""")

    fun firstWeekday(lower: String): DayOfWeek? =
        WEEKDAY_NAMES.entries.firstOrNull { Regex("""\b${it.key}s?\b""").containsMatchIn(lower) }?.value

    private fun count(token: String): Long = if (token.startsWith("a")) 1L else token.toLong()

    private fun plusUnit(d: LocalDate, n: Long, unit: String): LocalDate = when {
        unit.startsWith("day") -> d.plusDays(n)
        unit.startsWith("week") -> d.plusWeeks(n)
        unit.startsWith("month") -> d.plusMonths(n)
        else -> d.plusYears(n)
    }

    /** True when the text names a day by itself (not a clock time). */
    fun hasDateAnchor(input: String): Boolean {
        val lower = input.lowercase()
        return ISO_DATE.containsMatchIn(lower) || IN_N.containsMatchIn(lower) || N_FROM_NOW.containsMatchIn(lower) ||
            COUPLE.containsMatchIn(lower) || DAY_AFTER.containsMatchIn(lower) || TOMORROW.containsMatchIn(lower) ||
            END_OF.containsMatchIn(lower) || START_NEXT_MONTH.containsMatchIn(lower) || ORD_NEXT_MONTH.containsMatchIn(lower) ||
            NEXT_MONTH.containsMatchIn(lower) || WEEKEND.containsMatchIn(lower) ||
            MONTH_DAY.containsMatchIn(lower) || DAY_MONTH.containsMatchIn(lower) || ORDINAL_DAY.containsMatchIn(lower) ||
            lower.contains("next week") || Regex("""\b(?:today|tonight)\b""").containsMatchIn(lower) ||
            firstWeekday(lower) != null
    }

    /**
     * Date for calendar items. Missing parts stay in the current day, week, month, or year,
     * then move forward so the result is still ahead of [now].
     */
    fun parseEventDate(input: String, today: LocalDate, at: LocalTime, now: LocalDateTime): LocalDate {
        val lower = SpeechNorm.numbers(input).lowercase()
        ISO_DATE.find(lower)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { return it }
        }
        DAY_AFTER.find(lower)?.let { return today.plusDays(2) }
        COUPLE.find(lower)?.let { m -> return plusUnit(today, 2, m.groupValues[1]) }
        IN_N.find(lower)?.let { m ->
            val unit = m.groupValues[2]
            return plusUnit(today, count(m.groupValues[1]), unit)
        }
        N_FROM_NOW.find(lower)?.let { m -> return plusUnit(today, count(m.groupValues[1]), m.groupValues[2]) }
        END_OF.find(lower)?.let { m ->
            val which = m.groupValues[1].trim()
            val unit = m.groupValues[2]
            return when (unit) {
                "month" -> (if (which == "next") today.plusMonths(1) else today).with(TemporalAdjusters.lastDayOfMonth())
                "year" -> (if (which == "next") today.plusYears(1) else today).with(TemporalAdjusters.lastDayOfYear())
                else -> {
                    val base = if (which == "next") today.plusWeeks(1) else today
                    base.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                }
            }
        }
        START_NEXT_MONTH.find(lower)?.let { return today.plusMonths(1).withDayOfMonth(1) }
        ORD_NEXT_MONTH.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val next = today.plusMonths(1)
            return next.withDayOfMonth(day.coerceIn(1, next.lengthOfMonth()))
        }
        WEEKEND.find(lower)?.let { m ->
            val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            return if (m.groupValues[1] == "next") sat.plusWeeks(1) else sat
        }
        if (NEXT_MONTH.containsMatchIn(lower)) {
            val next = today.plusMonths(1)
            val ordinal = ORDINAL_DAY.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            return if (ordinal != null && ordinal in 1..31) next.withDayOfMonth(ordinal.coerceAtMost(next.lengthOfMonth())) else next
        }
        val monthDay = MONTH_DAY.find(lower) ?: DAY_MONTH.find(lower)
        if (monthDay != null) {
            val values = monthDay.groupValues
            val monthToken = if (values[1].all { it.isDigit() }) values[2] else values[1]
            val dayToken = if (values[1].all { it.isDigit() }) values[1] else values[2]
            val month = MONTH_NAMES.indexOf(monthToken.take(3)) + 1
            val day = dayToken.toIntOrNull()
            if (month > 0 && day != null) {
                val year = Regex("""\b(19|20)\d{2}\b""").find(lower)?.value?.toIntOrNull() ?: today.year
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return it }
            }
        }
        if (TOMORROW.containsMatchIn(lower)) return today.plusDays(1)
        if (lower.contains("next week")) {
            val dow = firstWeekday(lower)
            val weekStart = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            return if (dow == null) today.plusWeeks(1) else weekStart.plusDays((dow.value - DayOfWeek.MONDAY.value).toLong())
        }
        NEXT_WEEKDAY.find(lower)?.let { m ->
            val dow = WEEKDAY_NAMES.getValue(m.groupValues[1])
            val d = today.with(TemporalAdjusters.next(dow))
            val nextWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
            return if (d.isBefore(nextWeekStart)) d.plusWeeks(1) else d
        }
        firstWeekday(lower)?.let { dow ->
            var d = today
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            return if (LocalDateTime.of(d, at).isBefore(now)) d.plusWeeks(1) else d
        }
        ORDINAL_DAY.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
            var month = today.withDayOfMonth(1)
            repeat(18) {
                val candidate = runCatching { month.withDayOfMonth(day) }.getOrNull()
                if (candidate != null && !LocalDateTime.of(candidate, at).isBefore(now)) return candidate
                month = month.plusMonths(1)
            }
        }
        return if (LocalDateTime.of(today, at).isBefore(now)) today.plusDays(1) else today
    }

    private fun rollUntil(start: LocalDate, at: LocalTime, now: LocalDateTime, step: (LocalDate) -> LocalDate): LocalDate {
        var d = start
        var guard = 0
        while (LocalDateTime.of(d, at).isBefore(now) && guard < 5) {
            d = step(d)
            guard++
        }
        return d
    }

    /**
     * Date of a past or present entry such as a purchase: "yesterday", "3 days ago", "last friday", "on the 3rd".
     * Dates ahead are kept for "tomorrow" and "next week" so bills and plans still read them.
     */
    fun parseRelativeDate(input: String, today: LocalDate): LocalDate {
        val lower = SpeechNorm.numbers(input).lowercase()
        ISO_DATE.find(lower)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { return it }
        }
        if (Regex("""\bday\s+before\s+yesterday\b""").containsMatchIn(lower)) return today.minusDays(2)
        AGO.find(lower)?.let { m -> return plusUnit(today, -count(m.groupValues[1]), m.groupValues[2]) }
        if (lower.contains("yesterday") || Regex("""\blast\s+night\b""").containsMatchIn(lower)) return today.minusDays(1)
        if (DAY_AFTER.containsMatchIn(lower)) return today.plusDays(2)
        if (TOMORROW.containsMatchIn(lower)) return today.plusDays(1)
        if (lower.contains("next week")) return today.plusWeeks(1)
        if (lower.contains("last week")) return today.minusWeeks(1)
        if (lower.contains("last month")) return today.minusMonths(1)
        Regex("""(?i)\blast\s+($WEEKDAY_ALT)\b""").find(lower)?.let { m ->
            return today.with(TemporalAdjusters.previous(WEEKDAY_NAMES.getValue(m.groupValues[1])))
        }
        val monthDay = MONTH_DAY.find(lower) ?: DAY_MONTH.find(lower)
        if (monthDay != null) {
            val v = monthDay.groupValues
            val numericFirst = v[1].all { it.isDigit() }
            val month = MONTH_NAMES.indexOf((if (numericFirst) v[2] else v[1]).take(3)) + 1
            val day = (if (numericFirst) v[1] else v[2]).toIntOrNull()
            if (month > 0 && day != null) {
                val year = Regex("""\b(19|20)\d{2}\b""").find(lower)?.value?.toIntOrNull() ?: today.year
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return it }
            }
        }
        if (END_OF.containsMatchIn(lower) || NEXT_MONTH.containsMatchIn(lower) || START_NEXT_MONTH.containsMatchIn(lower)) {
            return parseEventDate(lower, today, LocalTime.MAX, today.atTime(LocalTime.MAX))
        }
        Regex("""(?i)\bon\s+($WEEKDAY_ALT)\b""").find(lower)?.let { m ->
            return today.with(TemporalAdjusters.previousOrSame(WEEKDAY_NAMES.getValue(m.groupValues[1])))
        }
        Regex("""(?i)\bon\s+the\s+(\d{1,2})(?:st|nd|rd|th)\b""").find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            if (day in 1..31) {
                var d = today
                repeat(3) {
                    val c = runCatching { d.withDayOfMonth(day) }.getOrNull()
                    if (c != null && !c.isAfter(today)) return c
                    d = d.minusMonths(1)
                }
            }
        }
        return today
    }

    // --- clock ------------------------------------------------------------------------------

    private val RELATIVE_OFFSET = Regex(
        """(?i)\b(?:in|after)\s+(?:(\d{1,4})|an|a)\s*(minutes?|mins?|hours?|hrs?|seconds?|secs?)\b"""
    )
    private val RELATIVE_LATER = Regex(
        """(?i)\b(\d{1,4})\s*(minutes?|mins?|hours?|hrs?|seconds?|secs?)\s*(?:later|after|from now)\b"""
    )
    private val CLOCK_AMPM = Regex("""(?<![\d:])(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
    private val CLOCK_COLON = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val CLOCK_AT = Regex("""\b(?:at|for|by|around)\s+(\d{1,2})(?!\s*(?:min|mins|minute|minutes|hour|hours|hr|hrs|st|nd|rd|th|dollar|dollars|bucks|taka|tk|k))\b(?![.:]\d)""")
    private val BARE_CLOCK = Regex(
        """\b(\d{1,2})(?::(\d{2}))?(?!\s*(?:min|mins|minute|minutes|hour|hours|hr|hrs|sec|secs|second|seconds|day|days|week|weeks))\b"""
    )
    private val RANGE = Regex(
        """(?i)\b(?:from\s+|between\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:to|till|until|-|and)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b"""
    )
    private val DURATION = Regex(
        """(?i)\b(?:for|lasting|duration(?:\s+of)?)\s+(?:(\d{1,3})|an?)\s*(hours?|hrs?|minutes?|mins?)\b"""
    )
    private val DURATION_ADJ = Regex("""(?i)\b(\d{1,3})\s*[- ]?(hour|hr|minute|min)[- ]?long\b""")

    sealed interface SpokenTime {
        data class Fixed(val time: LocalTime) : SpokenTime
        data class Ambiguous(val hour: Int, val minute: Int) : SpokenTime
    }

    /** "from 3 to 5 pm" gives 15:00 and 17:00. The first clock borrows am/pm from the second when it has none. */
    fun timeRange(lower: String): Pair<LocalTime, LocalTime>? {
        val m = RANGE.find(lower) ?: return null
        var h1 = m.groupValues[1].toInt()
        val m1 = m.groupValues[2].toIntOrNull() ?: 0
        var h2 = m.groupValues[4].toInt()
        val m2 = m.groupValues[5].toIntOrNull() ?: 0
        if (h1 !in 1..12 || h2 !in 1..12 || m1 > 59 || m2 > 59) return null
        val pm2 = m.groupValues[6].lowercase() == "pm"
        val p1 = m.groupValues[3].lowercase().ifEmpty { null }
        val pm1 = when {
            p1 != null -> p1 == "pm"
            pm2 -> h1 <= h2 || h1 == 12
            else -> false
        }
        fun h24(h: Int, pm: Boolean) = if (pm) (if (h == 12) 12 else h + 12) else (if (h == 12) 0 else h)
        h1 = h24(h1, pm1)
        h2 = h24(h2, pm2)
        return LocalTime.of(h1, m1) to LocalTime.of(h2, m2)
    }

    fun durationMinutes(lower: String): Int? {
        DURATION.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            return if (m.groupValues[2].lowercase().startsWith("h")) n * 60 else n
        }
        DURATION_ADJ.find(lower)?.let { m ->
            val n = m.groupValues[1].toInt()
            return if (m.groupValues[2].lowercase().startsWith("h")) n * 60 else n
        }
        return null
    }

    fun hasSpokenWhen(input: String): Boolean =
        WHEN_PHRASES.any { it.containsMatchIn(input) } || ORDINAL_DAY.containsMatchIn(input) ||
            RELATIVE_OFFSET.containsMatchIn(input) || RELATIVE_LATER.containsMatchIn(input)

    /** True when a clock time (not only a day) was spoken. */
    fun hasClock(input: String): Boolean {
        val lower = SpeechNorm.times(SpeechNorm.numbers(input)).lowercase()
        return relativeFromNow(lower, LocalDateTime.MIN) != null || readSpokenTime(lower, false) != null ||
            timeRange(lower) != null
    }

    fun resolveEventDateTimeFromText(input: String, today: LocalDate, now: LocalDateTime): LocalDateTime =
        resolveSpokenDateTime(input, today, now, false) ?: futureOnDate(defaultTime(input), input, today, now)

    /** Time of day when only a part of the day was spoken. Nine in the morning otherwise. */
    fun defaultTime(input: String): LocalTime {
        val lower = input.lowercase()
        return when {
            Regex("""\b(?:afternoon)\b""").containsMatchIn(lower) -> LocalTime.of(15, 0)
            Regex("""\b(?:evening)\b""").containsMatchIn(lower) -> LocalTime.of(18, 0)
            Regex("""\b(?:tonight|night)\b""").containsMatchIn(lower) -> LocalTime.of(20, 0)
            else -> LocalTime.of(9, 0)
        }
    }

    fun resolveSpokenDateTime(input: String, today: LocalDate, now: LocalDateTime, allowBareHour: Boolean): LocalDateTime? {
        val lower = SpeechNorm.times(SpeechNorm.numbers(input)).lowercase()
        relativeFromNow(lower, now)?.let { return it }
        timeRange(lower)?.let { (start, _) -> return futureOnDate(start, lower, today, now) }
        val spoken = readSpokenTime(lower, allowBareHour) ?: return null
        return when (spoken) {
            is SpokenTime.Fixed -> futureOnDate(spoken.time, input, today, now)
            is SpokenTime.Ambiguous -> nextTwelveHour(spoken.hour, spoken.minute, input, today, now)
        }
    }

    fun futureOnDate(time: LocalTime, input: String, today: LocalDate, now: LocalDateTime): LocalDateTime {
        val dt = LocalDateTime.of(parseEventDate(input, today, time, now), time)
        if (hasExplicitCalendarDate(input)) return dt
        var cursor = dt
        var guard = 0
        while (cursor.isBefore(now) && guard++ < 400) cursor = cursor.plusDays(1)
        return cursor
    }

    /** A month and day, a numeric date, or an ISO date. Those stay on the spoken day. */
    private fun hasExplicitCalendarDate(input: String): Boolean {
        val lower = SpeechNorm.numbers(input).lowercase()
        return ISO_DATE.containsMatchIn(lower) || MONTH_DAY.containsMatchIn(lower) ||
            DAY_MONTH.containsMatchIn(lower) || NUM_DATE.containsMatchIn(lower)
    }

    private fun relativeFromNow(lower: String, now: LocalDateTime): LocalDateTime? {
        val ahead = RELATIVE_OFFSET.find(lower)
        val later = RELATIVE_LATER.find(lower)
        val n = ahead?.groupValues?.get(1)?.toLongOrNull()
            ?: later?.groupValues?.get(1)?.toLongOrNull()
            ?: if (ahead != null) 1L else return null
        val unit = ahead?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
            ?: later?.groupValues?.get(2)
            ?: return null
        if (n < 0) return null
        val at = when {
            unit.startsWith("h") -> now.plusHours(n)
            unit.startsWith("s") -> now.plusSeconds(n)
            else -> now.plusMinutes(n)
        }
        return if (at.isBefore(now)) now else at
    }

    private fun nextTwelveHour(hour: Int, minute: Int, input: String, today: LocalDate, now: LocalDateTime): LocalDateTime {
        val am = twelveHour(hour, minute, pm = false)
        val pm = twelveHour(hour, minute, pm = true)
        if (hasExplicitCalendarDate(input)) {
            val day = parseEventDate(input, today, LocalTime.MAX, now)
            return listOf(LocalDateTime.of(day, am), LocalDateTime.of(day, pm))
                .firstOrNull { !it.isBefore(now) }
                ?: LocalDateTime.of(day, pm)
        }
        val day = if (hasDateAnchor(input)) parseEventDate(input, today, LocalTime.MAX, now) else now.toLocalDate()
        val candidates = listOf(0, 1).flatMap { add ->
            val date = day.plusDays(add.toLong())
            listOf(LocalDateTime.of(date, am), LocalDateTime.of(date, pm))
        }
        return candidates.firstOrNull { !it.isBefore(now) } ?: futureOnDate(am, input, day, now)
    }

    private fun twelveHour(hour: Int, minute: Int, pm: Boolean): LocalTime {
        val h = when {
            hour == 12 && !pm -> 0
            hour == 12 && pm -> 12
            pm && hour < 12 -> hour + 12
            else -> hour
        }
        return LocalTime.of(h, minute.coerceIn(0, 59))
    }

    private fun readSpokenTime(lower: String, allowBareHour: Boolean): SpokenTime? {
        CLOCK_AMPM.find(lower)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h !in 1..12 || min !in 0..59) return@let
            val pm = m.groupValues[3] == "pm"
            if (pm && h < 12) h += 12
            if (!pm && h == 12) h = 0
            return SpokenTime.Fixed(LocalTime.of(h, min))
        }
        fun fromHourMinute(h: Int, min: Int): SpokenTime? = when {
            h == 0 && min in 0..59 -> SpokenTime.Fixed(LocalTime.of(0, min))
            h in 13..23 && min in 0..59 -> SpokenTime.Fixed(LocalTime.of(h, min))
            h in 1..12 && min in 0..59 -> SpokenTime.Ambiguous(h, min)
            else -> null
        }
        CLOCK_COLON.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            fromHourMinute(h, min)?.let { return it }
        }
        CLOCK_AT.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            fromHourMinute(h, 0)?.let { return it }
        }
        if (!allowBareHour) return null
        BARE_CLOCK.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return fromHourMinute(h, min)
        }
        return null
    }

    fun parseClockTimeFromText(lower: String): LocalTime? {
        val l = SpeechNorm.times(SpeechNorm.numbers(lower)).lowercase()
        CLOCK_AMPM.find(l)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val pm = m.groupValues[3] == "pm"
            if (pm && h < 12) h += 12
            if (!pm && h == 12) h = 0
            return runCatching { LocalTime.of(h, min) }.getOrNull()
        }
        CLOCK_COLON.find(l)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            return runCatching { LocalTime.of(h, min) }.getOrNull()
        }
        CLOCK_AT.find(l)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            return runCatching { LocalTime.of(h, 0) }.getOrNull()
        }
        return null
    }

    // --- repeat -----------------------------------------------------------------------------

    private val EVERY_WEEKDAY = Regex("""(?i)\bevery\s+$WEEKDAY_ALT\b""")
    private val EVERY_N = Regex("""(?i)\bevery\s+(\d{1,2})\s*(days?|weeks?|months?|years?)\b""")
    private val EVERY_OTHER = Regex("""(?i)\bevery\s+(?:other|second|2nd)\s+(day|week|month|year|$WEEKDAY_ALT)\b""")
    private val EVERY_ORD_WEEKDAY = Regex("""(?i)\bevery\s+(\d)(?:st|nd|rd|th)\s+($WEEKDAY_ALT)\b""")
    private val TIMES_A_WEEK = Regex("""(?i)\b(twice|once|(\d)\s+times)\s+a\s+(week|month|day)\b""")
    private val EACH_DAYPART = Regex("""(?i)\b(?:every|each)\s+(?:morning|afternoon|evening|night|day)\b""")
    private val DAYS_LIST = Regex("""(?i)\b(?:every|each|on)\s+((?:$WEEKDAY_ALT)(?:\s*(?:,|and|&)\s*(?:$WEEKDAY_ALT))+)\b""")

    val REPEAT_PHRASES: List<Regex> = listOf(
        Regex("""(?i)\bevery\s+(?:other|second|2nd)\s+(?:day|week|month|year|$WEEKDAY_ALT)\b"""),
        Regex("""(?i)\bevery\s+\d{1,2}\s*(?:days?|weeks?|months?|years?)\b"""),
        Regex("""(?i)\bevery\s+\d(?:st|nd|rd|th)\s+$WEEKDAY_ALT\b"""),
        Regex("""(?i)\b(?:twice|once|\d\s+times)\s+a\s+(?:week|month|day)\b"""),
        Regex("""(?i)\b(?:every|each|on)\s+(?:$WEEKDAY_ALT)(?:\s*(?:,|and|&)\s*(?:$WEEKDAY_ALT))+\b"""),
        Regex("""(?i)\bevery\s+$WEEKDAY_ALT\b"""),
        Regex("""(?i)\b(?:every|each)\s+(?:morning|afternoon|evening|night|day|week|month|year|weekday|weekend)\b"""),
        Regex("""(?i)\b(?:weekdays?|weekends|monday\s+to\s+friday|mon\s+to\s+fri|mon-fri)\b"""),
        Regex("""(?i)\b(?:weekly|every\s+week|once\s+a\s+week|recurring|recursively|recursive|everyday|daily|nightly|monthly|yearly|annually|annual|fortnightly|biweekly|bi-weekly|quarterly)\b"""),
    )

    /**
     * Daily, weekly, monthly or yearly when the utterance asks to repeat. A one-off returns null.
     * Understands "every other day", "every 3 days", "every second monday", "twice a week", "weekdays".
     */
    fun explicitRepeat(input: String, start: LocalDate): EventRecurrence? {
        val lower = SpeechNorm.numbers(input).lowercase()
        val days = WEEKDAY_NAMES.filter { Regex("""\b${it.key}s?\b""").containsMatchIn(lower) }.map { it.value.value }.toSet()
        EVERY_ORD_WEEKDAY.find(lower)?.let { m ->
            return EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, interval = m.groupValues[1].toInt().coerceAtLeast(1),
                weekDays = setOf(WEEKDAY_NAMES.getValue(m.groupValues[2]).value))
        }
        EVERY_OTHER.find(lower)?.let { m ->
            val unit = m.groupValues[1]
            return when {
                unit == "day" -> EventRecurrence(frequency = RecurrenceFrequency.DAILY, interval = 2)
                unit == "week" -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, interval = 2, weekDays = setOf(start.dayOfWeek.value))
                unit == "month" -> EventRecurrence(frequency = RecurrenceFrequency.MONTHLY, interval = 2)
                unit == "year" -> EventRecurrence(frequency = RecurrenceFrequency.YEARLY, interval = 2)
                else -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, interval = 2, weekDays = setOf(WEEKDAY_NAMES.getValue(unit).value))
            }
        }
        EVERY_N.find(lower)?.let { m ->
            val n = m.groupValues[1].toInt().coerceAtLeast(1)
            return when {
                m.groupValues[2].startsWith("day") -> EventRecurrence(frequency = RecurrenceFrequency.DAILY, interval = n)
                m.groupValues[2].startsWith("week") -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, interval = n, weekDays = days.ifEmpty { setOf(start.dayOfWeek.value) })
                m.groupValues[2].startsWith("month") -> EventRecurrence(frequency = RecurrenceFrequency.MONTHLY, interval = n)
                else -> EventRecurrence(frequency = RecurrenceFrequency.YEARLY, interval = n)
            }
        }
        TIMES_A_WEEK.find(lower)?.let { m ->
            val times = when (m.groupValues[1].lowercase()) { "twice" -> 2; "once" -> 1; else -> m.groupValues[2].toInt() }
            when (m.groupValues[3]) {
                "week" -> {
                    val spread = when (times) { 1 -> setOf(start.dayOfWeek.value); 2 -> setOf(1, 4); 3 -> setOf(1, 3, 5); 4 -> setOf(1, 2, 4, 5); 5 -> setOf(1, 2, 3, 4, 5); else -> (1..7).toSet() }
                    return EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = if (days.isNotEmpty() && days.size == times) days else spread)
                }
                "month" -> return EventRecurrence(frequency = RecurrenceFrequency.MONTHLY)
                else -> return EventRecurrence(frequency = RecurrenceFrequency.DAILY)
            }
        }
        if (lower.containsAny("fortnightly", "biweekly", "bi-weekly")) {
            return EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, interval = 2, weekDays = days.ifEmpty { setOf(start.dayOfWeek.value) })
        }
        return when {
            lower.containsAny("weekdays", "weekday", "monday to friday", "mon to fri", "mon-fri") ->
                EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5))
            Regex("""\b(?:every\s+weekend|weekends)\b""").containsMatchIn(lower) ->
                EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(6, 7))
            lower.containsAny("every day", "everyday", "daily", "nightly") || EACH_DAYPART.containsMatchIn(lower) && !EVERY_WEEKDAY.containsMatchIn(lower) ->
                EventRecurrence(frequency = RecurrenceFrequency.DAILY)
            lower.containsAny("monthly", "every month", "once a month", "each month") ->
                EventRecurrence(frequency = RecurrenceFrequency.MONTHLY)
            lower.containsAny("quarterly") -> EventRecurrence(frequency = RecurrenceFrequency.MONTHLY, interval = 3)
            lower.containsAny("yearly", "annually", "annual", "every year", "each year") ->
                EventRecurrence(frequency = RecurrenceFrequency.YEARLY)
            DAYS_LIST.containsMatchIn(lower) && lower.contains("every") ->
                EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = days)
            lower.containsAny("weekly", "every week", "once a week", "each week", "recurring", "recursively", "recursive") ||
                EVERY_WEEKDAY.containsMatchIn(lower) ->
                EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = days.ifEmpty { setOf(start.dayOfWeek.value) })
            else -> null
        }
    }

    fun parseRepeatRule(transcript: String): String {
        val lower = SpeechNorm.numbers(transcript).lowercase()
        return when {
            lower.containsAny("weekdays", "weekday", "monday to friday", "mon-fri") -> "WEEKDAYS"
            lower.containsAny("weekly", "every week", "once a week", "each week", "recurring", "recursively", "recursive") ||
                EVERY_WEEKDAY.containsMatchIn(lower) || TIMES_A_WEEK.containsMatchIn(lower) -> "WEEKLY"
            lower.containsAny("monthly", "every month", "once a month", "each month") -> "MONTHLY"
            lower.containsAny("yearly", "annually", "annual", "every year") -> "YEARLY"
            else -> "DAILY"
        }
    }

    // --- phrases removed from titles --------------------------------------------------------

    val WHEN_PHRASES: List<Regex> = listOf(
        Regex("""(?i)\bfrom\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*(?:to|till|until|-)\s*\d{1,2}(?::\d{2})?\s*(?:am|pm)\b"""),
        Regex("""(?i)\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\s*(?:to|till|until|-)\s*\d{1,2}(?::\d{2})?\s*(?:am|pm)\b"""),
        Regex("""(?i)\b(?:for|lasting)\s+(?:\d{1,3}|an?)\s*(?:hours?|hrs?|minutes?|mins?)\b"""),
        Regex("""(?i)\b\d{1,3}\s*[- ]?(?:hour|hr|minute|min)[- ]?long\b"""),
        Regex("""(?i)\b(?:the\s+)?day\s+after\s+(?:tomorrow|tmrw|tomorow)\b"""),
        Regex("""(?i)\b(?:on\s+)?(?:next\s+|this\s+|coming\s+)?$WEEKDAY_ALT(?:'?s)?\b"""),
        Regex("""(?i)\b(?:in|after|within)\s+(?:\d{1,3}|an?|a\s+couple\s+of)\s+(?:days?|weeks?|months?|years?)\b"""),
        Regex("""(?i)\b(?:\d{1,3}|an?)\s+(?:days?|weeks?|months?)\s+(?:from\s+(?:now|today)|later)\b"""),
        Regex("""(?i)\b(?:in|after)\s+(?:\d{1,4}|an|a)\s*(?:minutes?|mins?|hours?|hrs?|seconds?|secs?)\b"""),
        Regex("""(?i)\b\d{1,4}\s*(?:minutes?|mins?|hours?|hrs?|seconds?|secs?)\s*(?:later|from now)\b"""),
        Regex("""(?i)\b(?:by\s+|at\s+)?(?:the\s+)?end\s+of\s+(?:the\s+)?(?:next\s+|this\s+)?(?:month|week|year)\b"""),
        Regex("""(?i)\b(?:the\s+)?(?:start|beginning|first)\s+of\s+next\s+month\b"""),
        Regex("""(?i)\b(?:on\s+)?(?:the\s+)?\d{1,2}(?:st|nd|rd|th)?\s+of\s+next\s+month\b"""),
        Regex("""(?i)\bnext\s+month\b"""),
        Regex("""(?i)\b(?:this|next|on\s+the)\s+weekend\b"""),
        Regex("""(?i)\b(?:today|tonight|tomorrow|tomorow|tmrw|next\s+week)\b"""),
        Regex("""(?i)\b(?:this|in\s+the|every|tomorrow)\s+(?:morning|afternoon|evening|night)\b"""),
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),
        Regex("""(?i)\b(?:on\s+)?$MONTH_ALT\.?\s+\d{1,2}(?:st|nd|rd|th)?\b"""),
        Regex("""(?i)\b(?:on\s+)?(?:the\s+)?\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH_ALT\b"""),
        Regex("""(?i)\b(?:on\s+)?the\s+\d{1,2}(?:st|nd|rd|th)\b"""),
        Regex("""(?i)\b(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm)\b"""),
        Regex("""(?i)\b(?:at\s+|by\s+)?\d{1,2}:\d{2}\b"""),
        Regex("""(?i)\bat\s+\d{1,2}\b"""),
        Regex("""(?i)\b(?:at\s+)?(?:noon|midnight)\b"""),
    )

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
}
