package com.ledgerai.app.domain.assistant

/**
 * One block of the data the assistant may read. An empty list still produces a line ("none") so the
 * model can tell "nothing there" from "not shown", and a long list says how many were left out.
 */
fun contextSection(title: String, lines: List<String>, cap: Int): String {
    if (lines.isEmpty()) return "$title: none"
    val shown = lines.take(cap)
    val header = if (lines.size > cap) "$title (${lines.size} total, first $cap shown):" else "$title (${lines.size}):"
    return (listOf(header) + shown).joinToString("\n")
}

/** One line, no newlines, at most [max] characters. Free text from notes or memories stays short. */
fun oneLine(text: String?, max: Int): String {
    val flat = text.orEmpty().replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= max) flat else flat.take(max - 3) + "..."
}

/** The lines that mention any of [keywords] (case-insensitive). No keywords means no filter. */
fun matching(lines: List<String>, keywords: List<String>): List<String> =
    if (keywords.isEmpty()) lines else lines.filter { line -> keywords.any { fuzzyContains(line, it) } }

/**
 * True when [text] contains [keyword], or a word of [text] is one typo away from it ("siam" finds "Sam").
 * Typos are only forgiven for keywords of four letters or more, so short words stay exact.
 */
fun fuzzyContains(text: String, keyword: String): Boolean {
    if (text.contains(keyword, ignoreCase = true)) return true
    if (keyword.length < 4) return false
    val k = keyword.lowercase()
    return text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).any { it.length >= 3 && withinOneEdit(it, k) }
}

private fun withinOneEdit(a: String, b: String): Boolean {
    if (Math.abs(a.length - b.length) > 1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    return when {
        a.length == b.length ->
            a.substring(i + 1) == b.substring(i + 1) ||
                (i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2))
        a.length > b.length -> a.substring(i + 1) == b.substring(i)
        else -> b.substring(i + 1) == a.substring(i)
    }
}
