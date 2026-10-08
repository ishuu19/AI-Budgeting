package com.ledgerai.app.data.schedule

import java.util.Locale
import java.util.regex.Pattern

/** One row to import into a weekly timetable. */
data class ParsedScheduleRow(
    val dayOfWeek: Int,
    val startTime: java.time.LocalTime,
    val endTime: java.time.LocalTime,
    val title: String,
    val courseCode: String = "",
    val location: String = ""
)

object ScheduleCsvParser {

    private val dayInLine = Pattern.compile(
        """(?i)\b(mon(day)?|tue(s(day)?)?|wed(nesday)?|thu(r(s(day)?)?)?|fri(day)?|sat(urday)?|sun(day)?)\b"""
    )
    private val roomInLine = Pattern.compile(
        """(?i)(?:room|rm|hall|bldg|building|lab)\s*[#:]?\s*([A-Za-z0-9\-]+)|\b([A-Z]{0,3}\d{2,4}[A-Z]?)\b"""
    )

    fun parse(text: String): List<ParsedScheduleRow> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val header = splitLine(lines.first())
        val hasHeader = header.any {
            it.contains("day", ignoreCase = true) ||
                it.contains("start", ignoreCase = true) ||
                it.contains("time", ignoreCase = true)
        }
        val dataLines = if (hasHeader) lines.drop(1) else lines
        val col = if (hasHeader) columnMap(header) else null

        val fromTable = dataLines.mapNotNull { line -> parseTableLine(line, col) }
        if (fromTable.isNotEmpty()) return fromTable

        return dataLines.mapNotNull { parseFlexibleLine(it) }
    }

    private fun parseTableLine(line: String, col: ColMap?): ParsedScheduleRow? {
        val cells = splitLine(line)
        if (cells.size < 2) return parseFlexibleLine(line)

        val map = col ?: defaultColumns(cells.size)

        val dayRaw = cells.getOrNull(map.day)
        val day = dayRaw?.let { parseDay(it) }
            ?: extractDayFromLine(line)
            ?: return parseFlexibleLine(line)

        val startCell = cells.getOrNull(map.start)?.trim().orEmpty()
        val endCell = cells.getOrNull(map.end)?.trim().orEmpty()

        val (start, end) = when {
            startCell.isNotBlank() && endCell.isNotBlank() -> {
                val s = ScheduleTimeParser.parseTime(startCell)
                val e = ScheduleTimeParser.parseTime(endCell)
                if (s != null && e != null) s to e else null
            }
            startCell.isNotBlank() -> ScheduleTimeParser.parseTimeRange(startCell)
            else -> ScheduleTimeParser.parseTimeRange(line)
        } ?: return parseFlexibleLine(line)

        val title = cells.getOrNull(map.title)?.takeIf { it.isNotBlank() }
            ?: cells.getOrNull(map.course)?.takeIf { it.isNotBlank() }
            ?: inferTitle(cells, map)
            ?: return null

        val location = cells.getOrNull(map.location)?.trim().orEmpty()
            .ifBlank { extractLocation(line) }

        return ParsedScheduleRow(
            dayOfWeek = day,
            startTime = start,
            endTime = end,
            title = title.trim(),
            courseCode = cells.getOrNull(map.course)?.trim().orEmpty(),
            location = location
        )
    }

    private fun parseFlexibleLine(line: String): ParsedScheduleRow? {
        val day = extractDayFromLine(line) ?: return null
        val range = ScheduleTimeParser.parseTimeRange(line)
            ?: findTwoTimes(line)
            ?: return null
        val title = inferTitleFromLine(line) ?: return null
        val location = extractLocation(line)
        return ParsedScheduleRow(
            dayOfWeek = day,
            startTime = range.first,
            endTime = range.second,
            title = title,
            location = location
        )
    }

    private fun findTwoTimes(line: String): Pair<java.time.LocalTime, java.time.LocalTime>? {
        val timePattern = Pattern.compile(
            """(?i)\b(\d{1,2}[:.]?\d{0,2}\s*(?:a\.?m\.?|p\.?m\.?)?|\d{3,4})\b"""
        )
        val found = mutableListOf<java.time.LocalTime>()
        val m = timePattern.matcher(line)
        while (m.find()) {
            ScheduleTimeParser.parseTime(m.group())?.let { found.add(it) }
            if (found.size >= 2) break
        }
        if (found.size >= 2) return found[0] to found[1]
        return null
    }

    private fun inferTitle(cells: List<String>, map: ColMap): String? {
        return cells.withIndex()
            .filter { it.index !in setOf(map.day, map.start, map.end, map.location) }
            .map { it.value.trim() }
            .firstOrNull { it.isNotBlank() && parseDay(it) == null && ScheduleTimeParser.parseTime(it) == null }
    }

    private fun inferTitleFromLine(line: String): String? {
        var s = line
        dayInLine.matcher(s).let { if (it.find()) s = s.replace(it.group(), "") }
        s = s.replace(Regex("""(?i)\b(room|rm|hall|lab|building)\s*[#:]?\s*\S+"""), "")
        s = s.replace(Regex("""\d{1,2}[:.]?\d{0,2}\s*(?i:a\.?m\.?|p\.?m\.?)"""), "")
        s = s.replace(Regex("""[-–—]"""), " ")
        val parts = s.split(Regex("""\s{2,}|,|\t""")).map { it.trim() }.filter { it.length > 1 }
        return parts.firstOrNull { ScheduleTimeParser.parseTime(it) == null }
            ?: parts.firstOrNull()
    }

    private fun extractDayFromLine(line: String): Int? {
        val m = dayInLine.matcher(line)
        if (!m.find()) return null
        return parseDay(m.group())
    }

    private fun extractLocation(line: String): String {
        val m = roomInLine.matcher(line)
        if (m.find()) {
            return (m.group(1) ?: m.group(2))?.trim().orEmpty()
        }
        val room = Regex("""(?i)(?:room|rm)\s*([A-Za-z0-9\-]+)""").find(line)?.groupValues?.getOrNull(1)
        return room?.trim().orEmpty()
    }

    private data class ColMap(val day: Int, val start: Int, val end: Int, val title: Int, val course: Int, val location: Int)

    private fun columnMap(header: List<String>): ColMap {
        fun idx(keys: List<String>, fallback: Int): Int {
            val i = header.indexOfFirst { h -> keys.any { k -> h.equals(k, ignoreCase = true) } }
            return if (i >= 0) i else fallback
        }
        val timeCol = idx(listOf("time", "hours", "slot"), -1)
        val start = if (timeCol >= 0) timeCol else idx(listOf("start", "from", "begin"), 1)
        val end = if (timeCol >= 0) timeCol else idx(listOf("end", "to", "until"), 2)
        return ColMap(
            day = idx(listOf("day", "dow", "weekday"), 0),
            start = start,
            end = end,
            title = idx(listOf("title", "subject", "class", "name", "course"), 3),
            course = idx(listOf("code", "coursecode", "course id"), 4),
            location = idx(listOf("location", "room", "place", "venue"), 5)
        )
    }

    private fun defaultColumns(size: Int): ColMap =
        ColMap(day = 0, start = 1, end = 2, title = 3, course = 4, location = if (size > 5) 5 else 4)

    private fun splitLine(line: String): List<String> {
        if (line.contains('\t')) return line.split('\t').map { it.trim() }
        if (line.contains('|')) return line.split('|').map { it.trim() }
        return line.split(',').map { it.trim().removeSurrounding("\"") }
    }

    private fun parseDay(raw: String): Int? {
        val t = raw.trim()
        t.toIntOrNull()?.let { n ->
            if (n in 1..7) return n
            if (n in 0..6) return if (n == 0) 7 else n
        }
        val lower = t.lowercase(Locale.US)
        return when {
            lower.startsWith("mon") -> 1
            lower.startsWith("tue") -> 2
            lower.startsWith("wed") -> 3
            lower.startsWith("thu") -> 4
            lower.startsWith("fri") -> 5
            lower.startsWith("sat") -> 6
            lower.startsWith("sun") -> 7
            else -> null
        }
    }
}
