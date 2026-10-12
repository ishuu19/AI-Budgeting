package com.ledgerai.app.domain.clips

import com.ledgerai.app.data.ai.DateRules
import com.ledgerai.app.data.ai.JobPasteParser
import com.ledgerai.app.data.ai.NoteRules
import com.ledgerai.app.data.ai.QuickParse
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What to do with text before it is stored. */
sealed class ClipGate {
    /** Save it. */
    data object Ok : ClipGate()

    /** Password-manager flag. Do not save. */
    data object Password : ClipGate()

    /** Card, one-time code, IBAN or private key. Save only if the user asks. */
    data object ConfirmSensitive : ClipGate()
}

/** Offline read of a clip. Hints are for the sheet. [confidentKind] skips the AI until the user asks. */
data class ClipRead(
    val hints: List<String>,
    val confidentKind: String?,
    val tags: List<String>,
    val urls: List<String>,
    val emails: List<String>,
    val phones: List<String>,
    val sensitive: Boolean,
    val jobTrack: Boolean,
)

object ClipRules {
    const val MAX_TEXT = 10_000
    const val AI_TEXT = 2_000
    const val DEDUPE_MS = 10 * 60 * 1000L

    private val URL = Regex("""https?://[^\s<>"']+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val PHONE = Regex("""(?<!\d)(?:\+\d{1,3}[\s-]?)?(?:\(\d{2,4}\)[\s-]?|\d{2,4}[\s-]?)?\d{3,4}[\s-]?\d{3,4}(?!\d)""")
    private val CURRENCY = Regex("""(?i)[$£€৳₹¥]|\b(?:usd|eur|gbp|bdt|inr|jpy|taka|dollars?|euros?|pounds?|yen)\b""")
    private val OTP = Regex(
        """(?i)(?:\b(?:otp|one[- ]time(?:\s+(?:password|code))?|passcode|verification\s+code|security\s+code|login\s+code|pin|code)\b[^\d]{0,16}(\d{4,8})\b|\b(\d{4,8})\b[^\d]{0,16}\b(?:otp|one[- ]time|passcode|pin)\b)"""
    )
    private val IBAN = Regex("""\b([A-Z]{2}\d{2}[A-Z0-9]{11,30})\b""")
    private val PRIVATE_KEY = Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----""")
    private val CARD = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun gate(text: String, descriptionSensitive: Boolean): ClipGate = when {
        descriptionSensitive -> ClipGate.Password
        isSensitiveText(text) -> ClipGate.ConfirmSensitive
        else -> ClipGate.Ok
    }

    fun isSensitiveText(text: String): Boolean =
        hasCardNumber(text) || OTP.containsMatchIn(text) || hasIban(text) || PRIVATE_KEY.containsMatchIn(text)

    fun hasCardNumber(text: String): Boolean =
        CARD.findAll(text).any { raw ->
            val digits = raw.value.filter { it.isDigit() }
            digits.length in 13..19 && luhn(digits)
        }

    fun plain(raw: CharSequence): String {
        var s = raw.toString()
        if (s.contains('<') && s.contains('>')) {
            val links = Regex("""(?i)href\s*=\s*["']([^"']+)["']""").findAll(s).map { it.groupValues[1] }.toList()
            s = s.replace(Regex("<[^>]+>"), " ")
            if (links.isNotEmpty()) s = s + " " + links.joinToString(" ")
        }
        return s.replace(Regex("[\\t\\x0B\\f\\r]+"), " ").trim()
    }

    /** First [MAX_TEXT] characters. The second value is true when the rest was cut. */
    fun limit(text: String): Pair<String, Boolean> {
        val trimmed = text.trim()
        if (trimmed.length <= MAX_TEXT) return trimmed to false
        return trimmed.take(MAX_TEXT) to true
    }

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun firstUrl(text: String, subject: String? = null): String? {
        URL.find(text)?.value?.trimEnd('.', ',', ')')?.let { return it }
        val sub = subject?.trim().orEmpty()
        return sub.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    fun read(
        text: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ): ClipRead {
        val hints = mutableListOf<String>()
        val urls = URL.findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().toList()
        val emails = EMAIL.findAll(text).map { it.value }.distinct().toList()
        val phones = PHONE.findAll(text).map { it.value.trim() }.filter { it.count { c -> c.isDigit() } >= 7 }.distinct().toList()
        val job = JobPasteParser.parse(text, today)
        val tasks = NoteRules.actionItems(text, now).filter { it.kind == "TASK" }
        val jobTrack = !job.needsHelp
        if (jobTrack) hints += "Looks like a job"
        if (DateRules.hasDateAnchor(text)) {
            val date = runCatching {
                DateRules.parseEventDate(text, today, LocalTime.MAX, now)
            }.getOrNull()
            val time = DateRules.parseClockTimeFromText(text.lowercase(Locale.ENGLISH))
            if (date != null) {
                val label = buildString {
                    append(date.format(DAY_MONTH))
                    if (time != null) append(' ').append(time.format(CLOCK))
                }
                hints += "Has a date: $label"
            }
        }
        if (tasks.isNotEmpty()) hints += "Looks like a task"
        if (CURRENCY.containsMatchIn(text)) {
            val amount = QuickParse.extractAmount(text)
            if (amount != null) hints += "Has an amount"
        }
        val tags = NoteRules.suggestTags("", text)
        val confident = when {
            jobTrack && tasks.isEmpty() -> "job"
            tasks.size == 1 && !jobTrack -> "task"
            else -> null
        }
        return ClipRead(
            hints = hints,
            confidentKind = confident,
            tags = tags,
            urls = urls,
            emails = emails,
            phones = phones,
            sensitive = isSensitiveText(text),
            jobTrack = jobTrack,
        )
    }

    /** Title for a note made from this clip. A URL uses the top tag and the date. */
    fun noteTitle(text: String, tags: List<String>, today: LocalDate = LocalDate.now()): String {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val url = first.startsWith("http://") || first.startsWith("https://")
        if (url) {
            val tag = tags.firstOrNull()
            val date = today.format(DAY_MONTH)
            return if (tag != null) "$tag · $date" else date
        }
        return first.take(60).ifBlank { "Note" }
    }

    fun appendBlock(body: String, text: String): String {
        val block = text.trim()
        if (body.isBlank()) return block
        return body.trimEnd() + "\n\n" + block
    }

    fun removeBlock(body: String, text: String): String {
        val block = text.trim()
        val marker = "\n\n$block"
        val at = body.lastIndexOf(marker)
        if (at >= 0) return body.removeRange(at, at + marker.length).trimEnd()
        if (body.trim() == block) return ""
        return body
    }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        var alt = false
        for (i in digits.lastIndex downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }

    private fun hasIban(text: String): Boolean =
        IBAN.findAll(text).any { ibanOk(it.groupValues[1]) }

    private fun ibanOk(iban: String): Boolean {
        val moved = iban.drop(4) + iban.take(4)
        val digits = buildString {
            for (c in moved) {
                if (c.isDigit()) append(c)
                else if (c.isLetter()) append(c.uppercaseChar().code - 'A'.code + 10)
                else return false
            }
        }
        var rem = 0
        for (ch in digits) {
            rem = (rem * 10 + (ch - '0')) % 97
        }
        return rem == 1
    }
}
