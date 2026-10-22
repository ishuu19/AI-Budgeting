package com.ledgerai.app.domain.records

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Rules-first reading of a spoken or typed sentence into the rebuild's records
 * (docs/life-os/app-design.md section 14). Returns null when the sentence is none of them,
 * so the older pipeline handles it. A value that is not in the sentence stays null; nothing is guessed.
 */
sealed interface RecordDraft {
    val title: String
    val detail: String
}

data class BirthdayDraft(val name: String, val month: Int, val day: Int) : RecordDraft {
    override val title get() = "Birthday · $name"
    override val detail get() = "$day ${Month.of(month).name.lowercase().replaceFirstChar { it.uppercase() }}"
}

data class TripDraft(val place: String, val start: LocalDate, val end: LocalDate) : RecordDraft {
    override val title get() = "Trip · $place"
    override val detail get() = "$start to $end"
}

data class AchievementDraft(val text: String, val on: LocalDate) : RecordDraft {
    override val title get() = "Achievement"
    override val detail get() = text
}

data class WishDraft(val name: String, val amount: Double?) : RecordDraft {
    override val title get() = "Wish · $name"
    override val detail get() = amount?.let { "about ${if (it % 1.0 == 0.0) it.toLong() else it}" } ?: "price unknown"
}

data class AppointmentDraft(val text: String, val start: LocalDateTime) : RecordDraft {
    override val title get() = "Appointment · $text"
    override val detail get() = "$start".replace('T', ' ')
}

/** The sentence mentions a password or PIN. Nothing from it is kept; the only offer is to open Passwords. */
data object PasswordGuard : RecordDraft {
    override val title get() = "Open Passwords"
    override val detail get() = "Passwords are never saved from speech."
}

object RecordIntentRules {

    private val months = Month.values().associateBy { it.name.take(3).lowercase() }
    private fun month(word: String): Int? = months[word.trim().take(3).lowercase()]?.value.takeIf { word.trim().length >= 3 }

    private val password = Regex("""(?i)\b(pass\s?words?|passcodes?|pin)\b""")
    private val birthdayDayFirst = Regex("""(?i)(?:add\s+|remember\s+|note\s+)?([A-Za-z][A-Za-z .\-]{0,40}?)(?:'s|’s|s')\s+birthday\s+(?:is\s+|on\s+)*(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([A-Za-z]{3,9})""")
    private val birthdayMonthFirst = Regex("""(?i)(?:add\s+|remember\s+|note\s+)?([A-Za-z][A-Za-z .\-]{0,40}?)(?:'s|’s|s')\s+birthday\s+(?:is\s+|on\s+)*([A-Za-z]{3,9})\s+(\d{1,2})""")
    private val trip = Regex("""(?i)\btrip\s+to\s+([A-Za-z][A-Za-z ]{1,40}?)\s+(?:from\s+)?(\d{1,2})(?:st|nd|rd|th)?\s*(?:to|-|until|through)\s*(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3,9})""")
    private val done = Regex("""(?i)^(?:i|we)\s+(?:have\s+|just\s+)?(finished|passed|completed|graduated|won|achieved|submitted|launched|published|ran|built)\b(.*)$""")
    private val wish = Regex("""(?i)^(?:i\s+)?(?:want|wish|would like)\s+to\s+buy\s+(.+?)(?:\s+(?:for|around|about)\s+(\d[\d,.]*))?(?:\s+(?:someday|one day|later|eventually))?\s*$""")
    private val appointment = Regex("""(?i)\b(appointment|dentist|doctor|checkup|interview|meeting)\b""")
    private val clock = Regex("""(?i)\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")

    fun parse(raw: String, today: LocalDate = LocalDate.now()): RecordDraft? {
        val text = raw.trim().trimEnd('.', '!')
        if (text.isEmpty()) return null
        if (password.containsMatchIn(text)) return PasswordGuard

        birthdayDayFirst.find(text)?.let { m ->
            val mo = month(m.groupValues[3]); val d = m.groupValues[2].toIntOrNull()
            if (mo != null && d != null && d in 1..Month.of(mo).length(true)) return BirthdayDraft(name(m.groupValues[1]), mo, d)
        }
        birthdayMonthFirst.find(text)?.let { m ->
            val mo = month(m.groupValues[2]); val d = m.groupValues[3].toIntOrNull()
            if (mo != null && d != null && d in 1..Month.of(mo).length(true)) return BirthdayDraft(name(m.groupValues[1]), mo, d)
        }
        trip.find(text)?.let { m ->
            val mo = month(m.groupValues[4]); val a = m.groupValues[2].toIntOrNull(); val b = m.groupValues[3].toIntOrNull()
            if (mo != null && a != null && b != null) {
                val start = nextDate(mo, a, today)
                val end = runCatching { LocalDate.of(start.year, mo, b) }.getOrNull()
                if (end != null && !end.isBefore(start)) return TripDraft(m.groupValues[1].trim().replaceFirstChar { it.uppercase() }, start, end)
            }
        }
        done.find(text)?.let { m ->
            return AchievementDraft((m.groupValues[1] + m.groupValues[2]).trim().replaceFirstChar { it.uppercase() }, today)
        }
        wish.find(text)?.let { m ->
            val amount = m.groupValues[2].takeIf { it.isNotBlank() }?.replace(",", "")?.toDoubleOrNull()
            return WishDraft(m.groupValues[1].trim().replaceFirstChar { it.uppercase() }, amount)
        }
        if (appointment.containsMatchIn(text)) {
            val time = clock.find(text)?.let { c ->
                var h = c.groupValues[1].toInt(); val min = c.groupValues[2].toIntOrNull() ?: 0
                when (c.groupValues[3].lowercase()) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
                if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
            }
            val day = dayOf(text, today)
            if (time != null && day != null) {
                val title = text.replace(clock, "").replace(Regex("""(?i)\b(today|tomorrow|on|next)\b|\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""), "")
                    .replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() }
                return AppointmentDraft(title.ifBlank { "Appointment" }, LocalDateTime.of(day, time))
            }
        }
        return null
    }

    private fun name(raw: String): String =
        raw.trim().split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /** The next occurrence of month/day on or after today. */
    private fun nextDate(month: Int, day: Int, today: LocalDate): LocalDate {
        val this_ = runCatching { LocalDate.of(today.year, month, day) }.getOrElse { LocalDate.of(today.year, month, 28) }
        return if (this_.isBefore(today)) this_.plusYears(1) else this_
    }

    private fun dayOf(text: String, today: LocalDate): LocalDate? {
        val t = text.lowercase()
        if (Regex("\\btoday\\b").containsMatchIn(t)) return today
        if (Regex("\\btomorrow\\b").containsMatchIn(t)) return today.plusDays(1)
        DayOfWeek.values().firstOrNull { Regex("\\b${it.name.lowercase()}\\b").containsMatchIn(t) }
            ?.let { return today.with(TemporalAdjusters.next(it)) }
        Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([A-Za-z]{3,9})\b""").find(text)?.let { m ->
            val mo = month(m.groupValues[2]); val d = m.groupValues[1].toIntOrNull()
            if (mo != null && d != null) return runCatching { nextDate(mo, d, today) }.getOrNull()
        }
        return null
    }
}
