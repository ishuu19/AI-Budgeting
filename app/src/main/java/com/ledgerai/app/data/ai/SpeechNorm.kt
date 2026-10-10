package com.ledgerai.app.data.ai

import java.util.Locale

/**
 * Turns spoken forms into the digits and clock strings the parser reads:
 * "twenty five" to 25, "one point five k" to 1500, "half past five" to 5:30, "fifty bucks" to 50 bucks.
 */
object SpeechNorm {

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val BIG = mapOf(
        "thousand" to 1e3, "grand" to 1e3, "lakh" to 1e5, "lakhs" to 1e5, "lac" to 1e5, "lacs" to 1e5,
        "crore" to 1e7, "crores" to 1e7, "million" to 1e6, "mil" to 1e6, "hajar" to 1e3, "hazar" to 1e3,
    )
    private val ORD_UNITS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6,
        "seventh" to 7, "eighth" to 8, "ninth" to 9,
    )
    private val ORD_OTHER = mapOf(
        "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "thirteenth" to 13, "fourteenth" to 14,
        "fifteenth" to 15, "sixteenth" to 16, "seventeenth" to 17, "eighteenth" to 18, "nineteenth" to 19,
        "twentieth" to 20, "thirtieth" to 30,
    )
    private val ORD_PREV = setOf("the", "on", "every", "each", "by", "till", "until", "of", "from", "before", "after")
    private val ORD_NEXT = setOf(
        "of", "january", "february", "march", "april", "may", "june", "july", "august", "september",
        "october", "november", "december", "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
    )
    private val SINGLE_PREV = setOf(
        "at", "by", "around", "in", "after", "spent", "paid", "pay", "cost", "costs", "owe", "owes", "owed",
        "lent", "lend", "borrowed", "budget", "limit", "save", "saving", "earned", "received", "got", "bought",
        "charged", "till", "until", "before", "past", "to", "of", "gave", "sent", "than", "for", "and", "every",
        "plus", "within", "about", "reach", "target", "set", "cap",
    )
    private val SINGLE_NEXT = setOf(
        "dollar", "dollars", "buck", "bucks", "taka", "tk", "rupee", "rupees", "pound", "pounds", "euro", "euros",
        "usd", "bdt", "inr", "rs", "min", "mins", "minute", "minutes", "hour", "hours", "hr", "hrs", "day", "days",
        "week", "weeks", "month", "months", "year", "years", "am", "pm", "percent", "k", "oclock", "cent", "cents",
        "sec", "secs", "second", "seconds", "quid", "grand", "times", "pm", "bangladeshi",
    )
    private val ONE_BAD_PREV = setOf("no", "any", "every", "each", "the", "which", "that", "this", "some", "any")
    private val ONE_BAD_NEXT = setOf("of", "more", "another", "else", "who", "which", "that", "to")

    private val WORD = Regex("""[A-Za-z]+""")
    private val GAP = Regex("""^[\s-]+$""")

    private class Run(val text: String, val endIdx: Int)

    private fun fmt(v: Double): String =
        if (v == Math.floor(v) && v < 1e15) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** Number words, ordinals and word multipliers to digits. Safe to run twice. */
    fun numbers(input: String, extra: Map<String, Double> = emptyMap()): String {
        val words = WORD.findAll(input).toList()
        if (words.isEmpty()) return finishDigits(input)
        val sb = StringBuilder()
        var pos = 0
        var wi = 0
        while (wi < words.size) {
            val run = readOrdinal(input, words, wi) ?: readRun(input, words, wi, extra)
            if (run == null) { wi++; continue }
            sb.append(input, pos, words[wi].range.first).append(run.text)
            pos = words[run.endIdx - 1].range.last + 1
            wi = run.endIdx
        }
        sb.append(input, pos, input.length)
        return finishDigits(sb.toString())
    }

    private fun lw(words: List<MatchResult>, i: Int) = words.getOrNull(i)?.value?.lowercase()

    private fun gapOk(input: String, words: List<MatchResult>, a: Int, b: Int): Boolean =
        GAP.matches(input.substring(words[a].range.last + 1, words[b].range.first))

    private fun readOrdinal(input: String, words: List<MatchResult>, wi: Int): Run? {
        val w = lw(words, wi) ?: return null
        val prev = lw(words, wi - 1)
        var n = ORD_UNITS[w] ?: ORD_OTHER[w]
        var end = wi + 1
        if (n == null && TENS[w] in listOf(20, 30)) {
            val next = lw(words, wi + 1)
            val u = ORD_UNITS[next] ?: return null
            if (!gapOk(input, words, wi, wi + 1)) return null
            n = TENS.getValue(w) + u
            end = wi + 2
        }
        if (n == null) return null
        val after = lw(words, end)
        val contextual = (prev != null && prev in ORD_PREV) || (after != null && after in ORD_NEXT)
        if (!contextual) return null
        if (n < 1 || n > 31) return null
        return Run(ordinal(n), end)
    }

    private fun readRun(input: String, words: List<MatchResult>, wi: Int, extra: Map<String, Double>): Run? {
        var i = wi
        var total = 0.0
        var cur = 0
        var any = false
        var seenBig = false
        var decimal: String? = null
        val first = lw(words, i) ?: return null
        if ((first == "a" || first == "an") && i + 1 < words.size && gapOk(input, words, i, i + 1)) {
            val nx = lw(words, i + 1)
            if (nx == "hundred" || nx in BIG) { cur = 1; any = true; i++ }
        }
        loop@ while (i < words.size) {
            val w = lw(words, i)!!
            if (i > wi && !gapOk(input, words, i - 1, i)) break
            val u = UNITS[w]
            val t = TENS[w]
            when {
                u != null -> {
                    val ok = cur % 100 == 0 || (cur % 100 in 20..90 && cur % 10 == 0 && u < 10 && u > 0)
                    if (!ok || (u == 0 && any)) break@loop
                    cur += u; any = true
                }
                t != null -> {
                    if (cur % 100 != 0) break@loop
                    cur += t; any = true
                }
                w == "hundred" -> {
                    if (!any && i == wi) break@loop
                    if (cur >= 100) break@loop
                    cur = (if (cur == 0) 1 else cur) * 100; any = true; seenBig = true
                }
                w in BIG -> {
                    if (!any) break@loop
                    total += (if (cur == 0) 1 else cur) * BIG.getValue(w)
                    cur = 0; seenBig = true
                }
                w == "and" -> {
                    val nx = lw(words, i + 1)
                    val ok = any && seenBig && nx != null && (nx in UNITS || nx in TENS) &&
                        gapOk(input, words, i, i + 1)
                    if (!ok) break@loop
                }
                w == "point" && any -> {
                    val sb = StringBuilder()
                    var j = i + 1
                    while (j < words.size && gapOk(input, words, j - 1, j)) {
                        val d = UNITS[lw(words, j)]
                        if (d == null || d > 9) break
                        sb.append(d); j++
                    }
                    if (sb.isEmpty()) break@loop
                    decimal = sb.toString()
                    i = j
                    break@loop
                }
                extra.containsKey(w) && extra.getValue(w) < 100 && cur % 100 == 0 -> {
                    cur += extra.getValue(w).toInt(); any = true
                }
                else -> break@loop
            }
            i++
        }
        if (!any) return null
        // Trailing "and" belongs to the sentence, not the number.
        var end = i
        if (end - 1 > wi && lw(words, end - 1) == "and") end--
        val value = total + cur + (decimal?.let { ("0.$it").toDouble() } ?: 0.0)
        val single = end - wi == 1
        if (single && decimal == null && value <= 10.0) {
            val w = lw(words, wi)!!
            val prev = lw(words, wi - 1)
            val next = lw(words, end)
            val okPrev = prev != null && prev in SINGLE_PREV
            val okNext = next != null && next in SINGLE_NEXT
            if (!okPrev && !okNext) return null
            if (w == "one" && ((prev != null && prev in ONE_BAD_PREV) || (next != null && next in ONE_BAD_NEXT))) return null
            if (w == "zero") return null
        }
        if (single && value == 1.0 && lw(words, wi) == "one") {
            val next = lw(words, end)
            val prev = lw(words, wi - 1)
            if (next == null && prev != "at" && prev != "by") return null
        }
        return Run(fmt(value), end)
    }

    private val K_SUFFIX = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\s?[kK](?![A-Za-z0-9])""")
    private val WORD_MULT = Regex(
        """(?i)(?<![\w.])(\d+(?:\.\d+)?)\s*(hundred|thousand|grand|lakhs?|lacs?|crores?|million|mil)\b"""
    )
    private val MULT_VALUE = mapOf(
        "hundred" to 100.0, "thousand" to 1e3, "grand" to 1e3, "lakh" to 1e5, "lakhs" to 1e5, "lac" to 1e5,
        "lacs" to 1e5, "crore" to 1e7, "crores" to 1e7, "million" to 1e6, "mil" to 1e6, "hajar" to 1e3, "hazar" to 1e3,
    )

    private fun finishDigits(text: String): String {
        var t = WORD_MULT.replace(text) { m ->
            fmt(m.groupValues[1].toDouble() * MULT_VALUE.getValue(m.groupValues[2].lowercase()))
        }
        t = K_SUFFIX.replace(t) { m -> fmt(m.groupValues[1].toDouble() * 1000) }
        return t
    }

    // --- time -------------------------------------------------------------------------------

    private val PRAYER_MINUTES = mapOf(
        "tahajjud" to 3 * 60 + 30, "fajr" to 5 * 60, "fojor" to 5 * 60, "dhuhr" to 13 * 60 + 15,
        "zuhr" to 13 * 60 + 15, "johor" to 13 * 60 + 15, "asr" to 16 * 60 + 30, "asor" to 16 * 60 + 30,
        "maghrib" to 18 * 60 + 15, "magrib" to 18 * 60 + 15, "isha" to 20 * 60, "esha" to 20 * 60,
    )
    private val PRAYER = Regex(
        """(?i)\b(?:(before|after|at|for|by|around)\s+)?(tahajjud|fajr|fojor|dhuhr|zuhr|johor|asr|asor|maghrib|magrib|isha|esha)\b(?!'s)(?:\s+(prayer|namaz|salah))?"""
    )
    private val HALF_PAST = Regex("""(?i)\bhalf\s+past\s+(\d{1,2})\b""")
    private val QUARTER_PAST = Regex("""(?i)\b(?:a\s+)?quarter\s+past\s+(\d{1,2})\b""")
    private val QUARTER_TO = Regex("""(?i)\b(?:a\s+)?quarter\s+(?:to|before)\s+(\d{1,2})\b""")
    private val MIN_PAST = Regex("""(?i)\b(\d{1,2})\s+(?:min(?:ute)?s?\s+)?past\s+(\d{1,2})\b""")
    private val MIN_TO = Regex("""(?i)\b(?<!from\s)(?<!between\s)(?:at\s+|by\s+)?(5|10|15|20|25)\s+(?:min(?:ute)?s?\s+)?(?:to|before|till)\s+(\d{1,2})\b(?!\s*(?:dollars?|bucks|am|pm|:))""")
    private val OCLOCK = Regex("""(?i)\b(\d{1,2})\s*o'?\s?clock\b""")
    private val OH_MIN = Regex("""(?i)\b(\d{1,2})\s+(?:oh|o)\s+(\d)\b""")
    private val SPLIT_CLOCK = Regex("""(?i)\b(at|by|around|before|till|until|from|to)\s+(\d{1,2})\s+([0-5]\d)\b(?!\s*(?:dollars?|bucks|taka|tk|%|percent|min|hour|hr|day|week|month|year))""")
    private val NOON = Regex("""(?i)\b(?:at\s+)?(?:noon|midday)\b""")
    private val MIDNIGHT = Regex("""(?i)\b(?:at\s+)?midnight\b""")
    private val HALF_HOUR = Regex("""(?i)\bhalf\s+an?\s+hour\b|\ba\s+half\s+hour\b""")
    private val AND_HALF = Regex("""(?i)\b(\d+)\s+and\s+a\s+half\s+(hours?|hrs?|days?|weeks?|months?|years?)\b""")
    private val AN_HOUR_HALF = Regex("""(?i)\b(?:an|1)\s+hour\s+and\s+a\s+half\b""")
    private val FRACTION_HOURS = Regex("""(?i)\b(\d+\.\d+)\s*(hours?|hrs?)\b""")
    private val A_HALF_UNIT = Regex("""(?i)\b(?:a\s+)?half\s+(?:a\s+)?(day|week|month)\b""")

    private fun clock(h: Int, m: Int): String = "$h:${m.toString().padStart(2, '0')}"

    private fun clock24(totalMinutes: Int): String {
        val mins = ((totalMinutes % 1440) + 1440) % 1440
        val h24 = mins / 60
        val m = mins % 60
        val ampm = if (h24 >= 12) "pm" else "am"
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return "${clock(h12, m)} $ampm"
    }

    /** Spoken clock phrases to "h:mm". Run after [numbers]. */
    fun times(input: String, extra: (String) -> String = { it }): String {
        var t = input
        t = extra(t)
        t = AND_HALF.replace(t) { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("h") -> "${n * 60 + 30} minutes"
                unit.startsWith("d") -> "${n * 24 + 12} hours"
                else -> "$n.5 $unit"
            }
        }
        t = AN_HOUR_HALF.replace(t, "90 minutes")
        t = HALF_HOUR.replace(t, "30 minutes")
        t = A_HALF_UNIT.replace(t) { m ->
            when (m.groupValues[1].lowercase()) { "day" -> "12 hours"; "week" -> "3 days"; else -> "2 weeks" }
        }
        t = FRACTION_HOURS.replace(t) { m ->
            "${Math.round(m.groupValues[1].toDouble() * 60)} minutes"
        }
        t = PRAYER.replace(t) { m ->
            if (m.groupValues[1].isEmpty() && m.groupValues[3].isEmpty()) return@replace m.value
            val shift = when (m.groupValues[1].lowercase()) { "before" -> -20; "after" -> 15; else -> 0 }
            "at " + clock24(PRAYER_MINUTES.getValue(m.groupValues[2].lowercase()) + shift)
        }
        t = QUARTER_PAST.replace(t) { clock(it.groupValues[1].toInt(), 15) }
        t = HALF_PAST.replace(t) { clock(it.groupValues[1].toInt(), 30) }
        t = QUARTER_TO.replace(t) { m ->
            val h = m.groupValues[1].toInt()
            clock(if (h <= 1) 12 else h - 1, 45)
        }
        t = MIN_PAST.replace(t) { m ->
            val mins = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (mins in 1..59 && h in 1..12) clock(h, mins) else m.value
        }
        t = MIN_TO.replace(t) { m ->
            val mins = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (h in 1..12) clock(if (h == 1) 12 else h - 1, 60 - mins) else m.value
        }
        t = OCLOCK.replace(t) { clock(it.groupValues[1].toInt(), 0) }
        t = OH_MIN.replace(t) { m -> "${m.groupValues[1]}:0${m.groupValues[2]}" }
        t = SPLIT_CLOCK.replace(t) { m ->
            val h = m.groupValues[2].toInt()
            if (h in 1..23) "${m.groupValues[1]} ${clock(h, m.groupValues[3].toInt())}" else m.value
        }
        t = NOON.replace(t, "at 12:00 pm")
        t = MIDNIGHT.replace(t, "at 12:00 am")
        return t
    }
}
