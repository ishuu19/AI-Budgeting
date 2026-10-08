package com.ledgerai.app.data.schedule

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

object ScheduleTimeParser {

    private val amPm = Pattern.compile("""(?i)([0-9]{1,2})[:\.]?([0-9]{2})?\s*(a\.?m\.?|p\.?m\.?)""")
    private val twentyFour = Pattern.compile("""\b([01]?\d|2[0-3])[:.]([0-9]{2})\b""")
    private val compact = Pattern.compile("""\b([01]?\d|2[0-3])([0-9]{2})\b""") // 930 -> 09:30
    private val hourOnly = Pattern.compile("""(?i)\b([01]?\d|2[0-3])\s*(a\.?m\.?|p\.?m\.?)\b""")

    private val formatters = listOf(
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("h:mma", Locale.US),
        DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US),
        DateTimeFormatter.ofPattern("HHmm"),
    )

    fun parseTime(raw: String): LocalTime? {
        val token = raw.trim()
        if (token.isEmpty()) return null

        normalize(token)?.let { return it }

        val cleaned = token.uppercase(Locale.US)
            .replace("NOON", "12:00 PM")
            .replace("MIDNIGHT", "12:00 AM")
            .replace('.', ':')
            .replace(Regex("""\s+"""), " ")

        for (fmt in formatters) {
            try {
                return LocalTime.parse(cleaned, fmt)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    /** Parses "9:00-10:30", "9:00 AM – 10:30 AM", "9-10:30", etc. */
    fun parseTimeRange(text: String): Pair<LocalTime, LocalTime>? {
        val chunk = text.trim()
        val separators = listOf(" to ", " TO ", "–", "—", "-")
        for (sep in separators) {
            val idx = chunk.indexOf(sep)
            if (idx <= 0) continue
            val start = parseTime(chunk.substring(0, idx)) ?: continue
            val end = parseTime(chunk.substring(idx + sep.length)) ?: continue
            if (end.isAfter(start) || end == start) return start to end
            // overnight lab — still accept
            return start to end
        }
        return null
    }

    private fun normalize(token: String): LocalTime? {
        amPm.matcher(token).let { m ->
            if (m.find()) {
                val h = m.group(1)!!.toInt()
                val min = m.group(2)?.toIntOrNull() ?: 0
                val pm = m.group(3)!!.contains('p', ignoreCase = true)
                var hour = h % 12
                if (pm) hour += 12
                if (!pm && h == 12) hour = 0
                return LocalTime.of(hour.coerceIn(0, 23), min.coerceIn(0, 59))
            }
        }
        hourOnly.matcher(token).let { m ->
            if (m.find()) {
                val h = m.group(1)!!.toInt()
                val pm = m.group(2)!!.contains('p', ignoreCase = true)
                var hour = h % 12
                if (pm) hour += 12
                if (!pm && h == 12) hour = 0
                return LocalTime.of(hour.coerceIn(0, 23), 0)
            }
        }
        twentyFour.matcher(token).let { m ->
            if (m.find()) {
                return LocalTime.of(m.group(1)!!.toInt(), m.group(2)!!.toInt())
            }
        }
        compact.matcher(token.replace(":", "")).let { m ->
            if (m.matches()) {
                return LocalTime.of(m.group(1)!!.toInt(), m.group(2)!!.toInt())
            }
        }
        return null
    }
}
