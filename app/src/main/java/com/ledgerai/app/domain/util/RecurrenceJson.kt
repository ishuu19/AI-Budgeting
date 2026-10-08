package com.ledgerai.app.domain.util

import org.json.JSONArray
import java.time.LocalDate

object RecurrenceJson {
    fun encodeDates(dates: Set<LocalDate>): String =
        if (dates.isEmpty()) "" else dates.sorted().joinToString(",") { it.toString() }

    fun decodeDates(raw: String): Set<LocalDate> {
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { part ->
            part.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }.toSet()
    }

    fun encodeWeekDays(days: Set<Int>): String =
        if (days.isEmpty()) "" else days.sorted().joinToString(",")

    fun decodeWeekDays(raw: String): Set<Int> =
        raw.split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 } }.toSet()

    fun encodeSpecificDates(dates: Set<LocalDate>): String {
        if (dates.isEmpty()) return ""
        val arr = JSONArray()
        dates.sorted().forEach { arr.put(it.toString()) }
        return arr.toString()
    }

    fun decodeSpecificDates(raw: String): Set<LocalDate> {
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    LocalDate.parse(arr.getString(i)).let { add(it) }
                }
            }
        }.getOrElse { decodeDates(raw) }
    }
}
