package com.ledgerai.app.data.ai

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A phrase the phone learned from past voice lines. [kind] is a [VoiceResultKind] name. */
data class LearnedRule(val phrase: String, val kind: String) {
    fun matches(text: String): Boolean {
        val needle = phrase.trim().lowercase()
        if (needle.length < 4) return false
        return Regex("""(?<!\w)${Regex.escape(needle)}(?!\w)""").containsMatchIn(text.lowercase())
    }
}

/**
 * Phrase rules mined on the phone from saved voice lines. They fill a gap only when the built-in
 * parse does not already know the sentence.
 */
object LearnedRules {
    @Volatile
    var current: List<LearnedRule> = emptyList()

    fun parse(raw: String): List<LearnedRule> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { '\t' in it }
            .map { line ->
                val phrase = line.substringBefore('\t').trim()
                val kind = line.substringAfter('\t').trim()
                LearnedRule(phrase, kind)
            }
            .filter { it.phrase.length >= 4 && VoiceResultKind.fromName(it.kind) != VoiceResultKind.Unsorted }
            .distinctBy { it.phrase.lowercase() }
            .take(40)
            .toList()

    fun encode(rules: List<LearnedRule>): String =
        rules.take(40).joinToString("\n") { "${it.phrase.trim()}\t${it.kind}" }

    fun match(
        text: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): ParsedIntent? {
        val rule = current.firstOrNull { it.matches(text) } ?: return null
        val kind = VoiceResultKind.fromName(rule.kind)
        if (kind == VoiceResultKind.Unsorted || kind == VoiceResultKind.Edit) return null
        val parsed = QuickParse.asKind(kind, text, today, now)
        return parsed.takeUnless { it is ParsedIntent.Unmatched }
    }
}

/**
 * On-phone agent. No download. It reads saved voice lines and keeps phrases that keep landing
 * on the same kind. A cloud call is optional and only sends a few short lines.
 */
object PhoneRuleAgent {
    private val STOP = setOf(
        "this", "that", "with", "from", "have", "just", "please", "today", "tomorrow",
        "spent", "spend", "added", "add", "the", "and", "for", "job", "task", "alarm"
    )

    fun propose(rows: List<Pair<String, String>>): List<LearnedRule> {
        val usable = rows.filter { it.first.isNotBlank() && VoiceResultKind.fromName(it.second) != VoiceResultKind.Unsorted }
        val out = mutableListOf<LearnedRule>()
        for ((kind, samples) in usable.groupBy { it.second }) {
            if (samples.size < 2) continue
            val counts = mutableMapOf<String, Int>()
            for ((text, _) in samples) {
                val words = text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 3 && it !in STOP }
                words.windowed(2) { it.joinToString(" ") }.distinct().forEach { phrase ->
                    counts[phrase] = (counts[phrase] ?: 0) + 1
                }
            }
            counts.filter { it.value >= 2 && it.key.length >= 6 }
                .entries
                .sortedByDescending { it.value }
                .take(2)
                .forEach { out += LearnedRule(it.key, kind) }
        }
        return out.distinctBy { it.phrase }.take(24)
    }

    /** Turns a tiny model reply into rules. Anything that is not `phrase<TAB>Kind` is ignored. */
    fun parseModel(raw: String): List<LearnedRule> {
        val jsonish = Regex("""\{"p"\s*:\s*"([^"]{4,40})"\s*,\s*"k"\s*:\s*"([A-Za-z]+)"}""")
        val fromJson = jsonish.findAll(raw).map { LearnedRule(it.groupValues[1], it.groupValues[2]) }
        val fromLines = raw.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('\t', limit = 2)
            if (parts.size < 2) null else LearnedRule(parts[0], parts[1])
        }
        return (fromJson + fromLines)
            .filter { VoiceResultKind.fromName(it.kind) != VoiceResultKind.Unsorted && it.phrase.length >= 4 }
            .distinctBy { it.phrase.lowercase() }
            .take(4)
            .toList()
    }
}
