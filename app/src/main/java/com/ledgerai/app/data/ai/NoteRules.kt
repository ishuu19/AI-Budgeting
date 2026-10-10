package com.ledgerai.app.data.ai

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.math.sqrt

/** One note sentence that looks like something to do. */
data class NoteActionItem(
    val title: String,
    val due: LocalDateTime? = null,
    /** TASK for things to do, IDEA for loose thoughts. */
    val kind: String = "TASK",
)

data class NoteSummaryResult(val summary: String, val bullets: List<String>)

/** A suggested notification found in a note. */
data class NoteNudgeSuggestion(val message: String, val at: LocalDateTime, val reason: String)

/**
 * Rule-based note helpers: tags, extractive summary, action items and nudges.
 * Lexicons come from assets/rules/notes_tags.tsv (keyword, tab, tag) and nudge_triggers.tsv (one phrase per
 * line, optional second column ignored) when present, merged over the embedded defaults below.
 */
object NoteRules {

    private val DEFAULT_TAGS: Map<String, List<String>> = mapOf(
        "finance" to listOf("money", "budget", "bill", "bills", "rent", "salary", "paid", "pay", "expense", "taka", "loan", "bank", "invoice", "tax", "savings", "debt", "income", "dollar", "price", "cost"),
        "shopping" to listOf("buy", "shopping", "grocery", "groceries", "order", "cart", "store", "market", "bazar", "kinte", "purchase"),
        "work" to listOf("meeting", "deadline", "project", "client", "email", "report", "boss", "office", "presentation", "interview", "job", "resume", "cv", "salary negotiation", "standup", "sprint"),
        "ideas" to listOf("idea", "ideas", "brainstorm", "maybe", "what if", "concept", "startup", "invent", "prototype"),
        "goals" to listOf("goal", "goals", "plan", "target", "resolution", "habit", "achieve", "milestone", "lokkho"),
        "personal" to listOf("family", "mom", "dad", "friend", "birthday", "home", "wedding", "anniversary", "gift"),
        "study" to listOf("exam", "class", "lecture", "assignment", "homework", "study", "quiz", "course", "chapter", "syllabus", "thesis", "lab", "tutorial", "revision", "porashona"),
        "health" to listOf("doctor", "medicine", "gym", "workout", "diet", "sleep", "health", "run", "hospital", "dentist", "exercise", "vitamin"),
        "travel" to listOf("flight", "ticket", "hotel", "trip", "passport", "visa", "booking", "travel", "airport", "luggage"),
        "food" to listOf("recipe", "cook", "dinner", "lunch", "breakfast", "restaurant", "ingredient", "meal"),
    )

    private val DEFAULT_TRIGGERS = listOf(
        "remind me", "dont forget", "do not forget", "remember to", "follow up", "follow-up", "deadline", "due",
        "appointment", "meeting", "renew", "submit", "call", "pay", "book", "pick up", "send", "mail", "bring",
        "must", "need to", "have to", "by tomorrow", "by tonight", "mone rakhbo", "mone koriye", "korte hobe",
        "dite hobe", "kinte hobe", "jete hobe", "birthday", "exam on", "interview on",
    )

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "were",
        "be", "been", "it", "this", "that", "i", "my", "me", "we", "you", "your", "our", "so", "as", "by", "from",
        "will", "would", "can", "could", "should", "have", "has", "had", "do", "does", "did", "not", "no", "if",
        "then", "than", "also", "just", "some", "any", "about", "up", "out", "get", "got", "ami", "amar", "ta", "o", "e", "je",
    )

    /** Parses `keyword<TAB>tag` lines. Comment lines start with #. Bad lines are skipped. */
    fun parseTagTsv(text: String): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = line.split('\t')
            if (cols.size < 2) continue
            val keyword = cols[0].trim().lowercase(Locale.ENGLISH)
            val tag = cols[1].trim().lowercase(Locale.ENGLISH)
            if (keyword.isEmpty() || tag.isEmpty() || tag.contains(' ')) continue
            out.getOrPut(tag) { mutableListOf() }.add(keyword)
        }
        return out
    }

    /** One trigger phrase per line (first column). */
    fun parseTriggerTsv(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split('\t').first().trim().lowercase(Locale.ENGLISH) }.filter { it.isNotEmpty() }

    fun mergeTags(extra: Map<String, List<String>>): Map<String, List<String>> {
        val merged = DEFAULT_TAGS.mapValues { it.value.toMutableList() }.toMutableMap()
        extra.forEach { (tag, words) -> merged.getOrPut(tag) { mutableListOf() }.addAll(words) }
        return merged
    }

    fun mergeTriggers(extra: List<String>): List<String> = (DEFAULT_TRIGGERS + extra).distinct()

    // ─── tags ────────────────────────────────────────────────────────────────

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ENGLISH).split(Regex("[^a-z0-9ঀ-৿']+")).filter { it.isNotEmpty() }

    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    fun suggestTags(
        title: String,
        body: String,
        lexicon: Map<String, List<String>> = DEFAULT_TAGS,
        max: Int = 4,
    ): List<String> {
        val hashtags = Regex("#([A-Za-z][A-Za-z0-9_-]{1,24})").findAll("$title\n$body").map { it.groupValues[1].lowercase(Locale.ENGLISH) }
            .filter { it != "nudge" }.distinct().toList()
        val titleWords = words(title).map(::stem)
        val bodyWords = words(body).map(::stem)
        val titleText = " " + titleWords.joinToString(" ") + " "
        val bodyText = " " + bodyWords.joinToString(" ") + " "
        val scores = lexicon.mapValues { (_, keywords) ->
            keywords.distinct().sumOf { kw ->
                val key = " " + words(kw).map(::stem).joinToString(" ") + " "
                if (key.isBlank()) 0
                else countOccurrences(titleText, key) * 3 + minOf(countOccurrences(bodyText, key), 3)
            }
        }.filterValues { it > 0 }
        val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
        return (hashtags + ranked).distinct().take(max)
    }

    private fun countOccurrences(text: String, key: String): Int {
        var count = 0
        var idx = text.indexOf(key)
        while (idx >= 0) {
            count++
            idx = text.indexOf(key, idx + key.length - 1)
        }
        return count
    }

    // ─── summary ─────────────────────────────────────────────────────────────

    private val MARKER = Regex("^(?:\\s*(?:[-*•]\\s+|\\d+[.)]\\s+|\\[[ xX]?]\\s*))+")

    internal fun sentences(body: String): List<String> =
        body.lines().flatMap { line ->
            val clean = line.replace(MARKER, "").trim()
            if (clean.isEmpty()) emptyList()
            else clean.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length >= 3 }
        }

    private val SALIENT = setOf("deadline", "due", "must", "need", "todo", "buy", "call", "pay", "submit", "important", "urgent", "remember", "exam", "meeting", "tomorrow", "today", "decide", "decided", "plan")

    fun summarize(title: String, body: String, maxBullets: Int = 3): NoteSummaryResult {
        val sents = sentences(body)
        if (sents.isEmpty()) {
            val t = title.trim()
            return NoteSummaryResult(t, if (t.isEmpty()) emptyList() else listOf(t))
        }
        if (sents.size <= maxBullets) {
            val bullets = sents.map { it.take(140) }
            return NoteSummaryResult(sents.joinToString(" ").take(220), bullets)
        }
        val freq = HashMap<String, Int>()
        (words(title) + words(body)).map(::stem).filter { it !in STOP && it.length > 2 }.forEach { freq[it] = (freq[it] ?: 0) + 1 }
        val titleSet = words(title).map(::stem).toSet()
        val scored = sents.mapIndexed { i, s ->
            val ws = words(s).map(::stem).filter { it !in STOP && it.length > 2 }
            var score = ws.sumOf { (freq[it] ?: 0).toDouble() } / sqrt(ws.size.coerceAtLeast(1).toDouble())
            score += ws.count { it in titleSet } * 1.5
            score += ws.count { it in SALIENT } * 1.0
            if (s.any { it.isDigit() }) score += 0.8
            if (i == 0) score *= 1.3
            Triple(i, s, score)
        }
        val picked = scored.sortedByDescending { it.third }.take(maxBullets).sortedBy { it.first }
        val bullets = picked.map { it.second.take(140) }
        val summary = picked.joinToString(" ") { it.second }.take(220)
        return NoteSummaryResult(summary, bullets)
    }

    // ─── action items ────────────────────────────────────────────────────────

    private val TODO_START = Regex(
        "^(?:todo\\s*:?|to do\\s*:?|\\[\\s*]|task\\s*:|action\\s*:|buy|call|email|text|submit|pay|book|send|finish|prepare|schedule|bring|pick up|fix|renew|ask|clean|order|cancel|review|write|read|study|complete|return|visit|check|collect|register|apply|update|print|plan|meet)\\b",
        RegexOption.IGNORE_CASE
    )
    private val TODO_ANYWHERE = Regex(
        "\\b(?:need to|needs to|have to|has to|must|should|don'?t forget(?: to)?|do not forget(?: to)?|remember to|remind me to|korte hobe|dite hobe|kinte hobe|jete hobe|korbo|kinbo)\\b",
        RegexOption.IGNORE_CASE
    )
    private val IDEA_START = Regex("^(?:idea\\s*:?|maybe\\b|what if\\b|could\\b|someday\\b|wishlist\\s*:?|thought\\s*:)", RegexOption.IGNORE_CASE)
    private val LEAD = Regex("^(?:todo\\s*:?|to do\\s*:?|task\\s*:|action\\s*:|idea\\s*:|\\[\\s*]\\s*|remind me to\\s+|don'?t forget to\\s+|remember to\\s+)", RegexOption.IGNORE_CASE)

    fun actionItems(body: String, now: LocalDateTime = LocalDateTime.now()): List<NoteActionItem> {
        val today = now.toLocalDate()
        val seen = HashSet<String>()
        val out = mutableListOf<NoteActionItem>()
        for (s in sentences(body)) {
            val idea = IDEA_START.containsMatchIn(s)
            val todo = !idea && (TODO_START.containsMatchIn(s) || TODO_ANYWHERE.containsMatchIn(s))
            if (!idea && !todo) continue
            val title = s.replace(LEAD, "").trim().trimEnd('.', '!').take(90)
            if (title.length < 3 || !seen.add(title.lowercase(Locale.ENGLISH))) continue
            val due = if (todo && QuickParse.hasSpokenWhen(s)) QuickParse.resolveEventDateTimeFromText(s, today, now) else null
            out += NoteActionItem(title.replaceFirstChar { it.uppercase() }, due, if (idea) "IDEA" else "TASK")
        }
        return out
    }

    // ─── nudges ──────────────────────────────────────────────────────────────

    /** True when the note asked to be scanned. */
    fun optedIn(body: String): Boolean = body.contains("[nudge]", ignoreCase = true) || body.contains("#nudge", ignoreCase = true)

    /** Default time for a nudge that names a day but no clock: 09:00 next occurrence. */
    private fun defaultAt(now: LocalDateTime): LocalDateTime {
        val nine = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0))
        return if (nine.isAfter(now)) nine else nine.plusDays(1)
    }

    /**
     * Notification proposals found by rules alone: lines with a trigger phrase, dated by the spoken date parser.
     * At most [max] results, no duplicate messages.
     */
    fun nudges(
        body: String,
        now: LocalDateTime = LocalDateTime.now(),
        triggers: List<String> = DEFAULT_TRIGGERS,
        max: Int = 3,
    ): List<NoteNudgeSuggestion> {
        val today: LocalDate = now.toLocalDate()
        val seen = HashSet<String>()
        val out = mutableListOf<NoteNudgeSuggestion>()
        for (s in sentences(body.replace("[nudge]", "", ignoreCase = true).replace("#nudge", "", ignoreCase = true))) {
            val lower = s.lowercase(Locale.ENGLISH).replace("’", "'").replace("'", "")
            val hit = triggers.firstOrNull { t ->
                val key = t.lowercase(Locale.ENGLISH).replace("'", "")
                Regex("(?<![a-z0-9])" + Regex.escape(key) + "(?![a-z0-9])").containsMatchIn(lower)
            } ?: continue
            val message = s.replace(LEAD, "").trim().trimEnd('.', '!').take(120)
            if (message.length < 3 || !seen.add(message.lowercase(Locale.ENGLISH))) continue
            val dated = QuickParse.hasSpokenWhen(s)
            val at = if (dated) QuickParse.resolveEventDateTimeFromText(s, today, now) else defaultAt(now)
            out += NoteNudgeSuggestion(message.replaceFirstChar { it.uppercase() }, at, "Rules: matched \"$hit\"" + if (dated) "" else ", no date so 9am")
            if (out.size >= max) break
        }
        return out
    }
}
