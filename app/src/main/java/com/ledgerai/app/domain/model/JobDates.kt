package com.ledgerai.app.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date on a job that is not the application date. The label says what it is for. */
data class JobDateNote(val label: String, val date: LocalDate)

object JobDates {
    private val show = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val line = Regex("""^(.{1,40}?)\s*[·:|\-]\s*(.+)$""")

    fun format(items: List<JobDateNote>): String =
        items.joinToString("\n") { "${it.label.trim()} · ${it.date.format(show)}" }

    fun parse(raw: String): List<JobDateNote> =
        raw.lines().mapNotNull { row ->
            val match = line.find(row.trim()) ?: return@mapNotNull null
            val label = match.groupValues[1].trim()
            val date = runCatching { LocalDate.parse(match.groupValues[2].trim(), show) }.getOrNull()
                ?: runCatching { LocalDate.parse(match.groupValues[2].trim()) }.getOrNull()
                ?: return@mapNotNull null
            if (label.isBlank()) null else JobDateNote(label, date)
        }
}
