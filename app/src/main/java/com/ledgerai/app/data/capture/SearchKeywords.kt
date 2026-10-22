package com.ledgerai.app.data.capture

import com.ledgerai.app.domain.capture.AskPrompts
import javax.inject.Inject
import javax.inject.Singleton

/** What to look for and where, as picked by the AI from a typed search. */
data class SearchPlan(val keywords: List<String>, val kinds: Set<String>)

/**
 * Global search by meaning. The AI first picks the key words and the kinds of records they belong to;
 * the app then searches only those sections for those words. Only the typed text is sent, no records.
 */
@Singleton
class SearchKeywords @Inject constructor(private val ai: AiGateway) {
    suspend fun plan(query: String): SearchPlan? {
        val raw = ai.fast(AskPrompts.SEARCH_SYSTEM, query.take(300)).getOrNull() ?: return null
        val (keywords, kinds) = AskPrompts.parseSearch(raw)
        return if (keywords.isEmpty()) null else SearchPlan(keywords, kinds)
    }
}
