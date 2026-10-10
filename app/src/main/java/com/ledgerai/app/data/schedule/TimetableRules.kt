package com.ledgerai.app.data.schedule

/**
 * Rule-based timetable reader. Tries, in order: a JSON timetable, CSV or one-line-per-class text, and day
 * headings followed by time lines ("Monday" then "09:00-10:30 Math Room 101"). Empty when none of them fit,
 * and only then is the cloud worth asking.
 */
object TimetableRules {

    private val dayHeading = Regex("""(?i)^\s*(mon(day)?|tue(s(day)?)?|wed(nesday)?|thu(r(s(day)?)?)?|fri(day)?|sat(urday)?|sun(day)?)\s*:?\s*$""")

    private val timeRange = Regex("(?i)(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:am|pm)?)\\s*(?:-|\\u2013|\\u2014|to)\\s*(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:am|pm)?)")

    fun parse(raw: String): List<ParsedScheduleRow> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        if (text.startsWith("{") || text.startsWith("[")) {
            ScheduleTimetableJson.parseRowsFromAiJson(text).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val flat = runCatching { ScheduleCsvParser.parse(text) }.getOrDefault(emptyList())
        val grouped = parseDayBlocks(text)
        // A leading throwaway line stops the CSV parser from taking "Monday ..." as a header row.
        val perLine = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { ScheduleCsvParser.parse("x\n$it").firstOrNull() }.getOrNull() }
        return listOf(flat, grouped, perLine).maxBy { it.size }
    }

    fun parseDayBlocks(text: String): List<ParsedScheduleRow> {
        var day: String? = null
        val rows = mutableListOf<ParsedScheduleRow>()
        for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            if (dayHeading.matches(line)) {
                day = line.trimEnd(':', ' ')
                continue
            }
            val d = day ?: continue
            val m = timeRange.find(line) ?: continue
            val start = ScheduleTimeParser.parseTime(m.groupValues[1]) ?: continue
            val end = ScheduleTimeParser.parseTime(m.groupValues[2]) ?: continue
            val rest = line.removeRange(m.range).trim(' ', ',', '-', '–', ':')
            val parts = rest.split(Regex("(?i)\\s*(?:,|@|\\b(?:room|rm|lab|hall)\\b)\\s*"), limit = 2)
            val title = parts[0].trim()
            if (title.isEmpty()) continue
            val dow = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").indexOf(d.lowercase().take(3)) + 1
            if (dow == 0) continue
            rows += ParsedScheduleRow(dow, start, end, title, location = parts.getOrNull(1)?.trim().orEmpty())
        }
        return rows
    }
}
