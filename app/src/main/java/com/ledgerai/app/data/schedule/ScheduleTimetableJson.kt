package com.ledgerai.app.data.schedule

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.format.DateTimeFormatter

/** Canonical JSON shape for AI timetable imports (includes human-readable [day]). */
data class TimetableDocument(
    val version: Int = 1,
    val classes: List<TimetableClassEntry> = emptyList()
)

data class TimetableClassEntry(
    val day: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val title: String,
    val courseCode: String = "",
    val location: String = ""
)

object ScheduleTimetableJson {

    private val gson = Gson()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun toDocument(rows: List<ParsedScheduleRow>): TimetableDocument =
        TimetableDocument(
            classes = rows.map { row ->
                TimetableClassEntry(
                    day = dayLabel(row.dayOfWeek),
                    dayOfWeek = row.dayOfWeek,
                    startTime = row.startTime.format(timeFmt),
                    endTime = row.endTime.format(timeFmt),
                    title = row.title,
                    courseCode = row.courseCode,
                    location = row.location
                )
            }
        )

    fun toJson(rows: List<ParsedScheduleRow>): String =
        gson.toJson(toDocument(rows))

    fun parseRowsFromAiJson(raw: String): List<ParsedScheduleRow> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val root = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return emptyList()
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                obj.getAsJsonArray("classes")
                    ?: obj.getAsJsonArray("timetable")
                    ?: obj.getAsJsonArray("schedule")
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        return buildList {
            for (el in array) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                parseEntry(o)?.let { add(it) }
            }
        }
    }

    private fun parseEntry(o: JsonObject): ParsedScheduleRow? {
        val dayStr = o.get("day")?.asString
        var dayNum = o.get("dayOfWeek")?.asInt
        if (dayNum == null && dayStr != null) {
            dayNum = ScheduleDayNames.toDayOfWeek(dayStr)
        }
        if (dayNum == null) return null
        val startRaw = o.get("startTime")?.asString ?: o.get("start")?.asString ?: return null
        val endRaw = o.get("endTime")?.asString ?: o.get("end")?.asString ?: return null
        val title = o.get("title")?.asString ?: o.get("name")?.asString ?: return null
        val startTime = ScheduleTimeParser.parseTime(startRaw)
            ?: ScheduleTimeParser.parseTimeRange(startRaw)?.first
            ?: return null
        val endTime = ScheduleTimeParser.parseTime(endRaw)
            ?: ScheduleTimeParser.parseTimeRange(endRaw)?.second
            ?: ScheduleTimeParser.parseTimeRange("$startRaw-$endRaw")?.second
            ?: return null
        return ParsedScheduleRow(
            dayOfWeek = dayNum.coerceIn(1, 7),
            startTime = startTime,
            endTime = endTime,
            title = title.trim(),
            courseCode = (o.get("courseCode")?.asString ?: o.get("code")?.asString ?: "").trim(),
            location = (o.get("location")?.asString ?: o.get("room")?.asString ?: "").trim()
        )
    }

    private fun dayLabel(dow: Int): String = ScheduleDayNames.label(dow)
}

object ScheduleDayNames {
    private val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun label(dayOfWeek: Int): String = names.getOrElse(dayOfWeek - 1) { "Day $dayOfWeek" }

    fun toDayOfWeek(day: String): Int? {
        val t = day.trim().lowercase()
        names.forEachIndexed { index, name ->
            if (name.lowercase().startsWith(t.take(3)) || name.lowercase() == t) return index + 1
        }
        return null
    }
}
