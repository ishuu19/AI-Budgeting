package com.ledgerai.app.domain.assistant

import java.util.Locale

private const val CONTEXT_CAP = 5

/**
 * Chooses stored merchants and open debts for one utterance.
 * Copies facts from the inputs. An empty utterance yields an empty bundle.
 */
fun selectContext(
    utterance: String,
    merchants: List<KnownMerchant>,
    debts: List<OpenDebtFact>,
): ContextBundle {
    if (utterance.isBlank()) {
        return ContextBundle(emptyList(), emptyList())
    }
    return ContextBundle(
        merchants = selectMerchants(utterance, merchants),
        debts = selectDebts(utterance, debts),
    )
}

private fun selectMerchants(utterance: String, merchants: List<KnownMerchant>): List<KnownMerchant> {
    val usable = merchants.filter { it.name.isNotBlank() }
    val matches = usable.filter { containsWholePhrase(utterance, it.name) }
    if (matches.isNotEmpty()) {
        return matches.take(CONTEXT_CAP)
    }
    val seen = HashSet<String>()
    val recent = ArrayList<KnownMerchant>(CONTEXT_CAP)
    for (merchant in usable) {
        if (seen.add(merchant.name.lowercase(Locale.ENGLISH))) {
            recent += merchant
            if (recent.size == CONTEXT_CAP) break
        }
    }
    return recent
}

private fun selectDebts(utterance: String, debts: List<OpenDebtFact>): List<OpenDebtFact> =
    debts
        .filter { it.person.isNotBlank() && containsWholePhrase(utterance, it.person) }
        .take(CONTEXT_CAP)

private fun containsWholePhrase(utterance: String, phrase: String): Boolean {
    val haystack = utterance.lowercase(Locale.ENGLISH)
    val needle = phrase.lowercase(Locale.ENGLISH)
    var start = 0
    while (start <= haystack.length - needle.length) {
        val index = haystack.indexOf(needle, start)
        if (index < 0) return false
        val before = if (index == 0) null else haystack[index - 1]
        val end = index + needle.length
        val after = if (end == haystack.length) null else haystack[end]
        if (!isWordChar(before) && !isWordChar(after)) return true
        start = index + 1
    }
    return false
}

private fun isWordChar(char: Char?): Boolean = char != null && char.isLetterOrDigit()
