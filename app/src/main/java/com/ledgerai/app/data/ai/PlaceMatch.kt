package com.ledgerai.app.data.ai

/**
 * Picks a place the user already used when the spoken name is a close misspelling.
 */
object PlaceMatch {
    fun snap(spoken: String, known: Collection<String>): String {
        val raw = spoken.trim().trimEnd('.', ',')
        if (raw.length < 3) return raw
        val choices = known.map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }
        if (choices.isEmpty()) return raw
        val best = choices.maxBy { similarity(raw, it) }
        val score = similarity(raw, best)
        val distance = damerau(raw.lowercase(), best.lowercase())
        val close = score >= 0.8 || (distance == 1 && raw.length >= 4)
        return if (close) best else raw
    }

    private fun similarity(a: String, b: String): Double {
        val left = a.lowercase()
        val right = b.lowercase()
        if (left == right) return 1.0
        val max = maxOf(left.length, right.length).coerceAtLeast(1)
        return 1.0 - damerau(left, right).toDouble() / max
    }

    /** Adjacent swaps count as one edit, so "Dahka" matches "Dhaka". */
    private fun damerau(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = minOf(best, d[i - 2][j - 2] + 1)
                }
                d[i][j] = best
            }
        }
        return d[n][m]
    }
}
