package com.ledgerai.app.data.ai

import com.ledgerai.app.data.local.room.VoiceHistoryDao
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * After a voice line is saved, the phone rereads recent lines and adds phrase rules.
 * A paid model is asked only after several new lines, and only with a few dozen words.
 */
@Singleton
class VoiceRuleLearner @Inject constructor(
    private val historyDao: VoiceHistoryDao,
    private val prefs: UserPreferences,
    private val ai: AiRepository,
) {
    suspend fun refresh() {
        val rows = historyDao.observe(40).first()
        val pairs = rows.map { it.transcript to it.resultKind }
        val local = PhoneRuleAgent.propose(pairs)
        var merged = (local + LearnedRules.parse(prefs.learnedRulesNow()))
            .distinctBy { it.phrase.lowercase() }
            .take(40)
        val cursor = prefs.learnedCursor()
        val fresh = rows.filter { it.createdAt > cursor && it.resultKind != VoiceResultKind.Unsorted.name }
        if (fresh.size >= 6 && prefs.cloudFallback.first()) {
            val compact = fresh.take(6).joinToString("\n") { "${it.resultKind}:${it.transcript.take(48)}" }
            val extra = runCatching { ai.learnRules(compact) }.getOrNull()
            if (!extra.isNullOrBlank()) {
                merged = (PhoneRuleAgent.parseModel(extra) + merged)
                    .distinctBy { it.phrase.lowercase() }
                    .take(40)
                prefs.setLearnedCursor(fresh.maxOf { it.createdAt })
            }
        }
        val encoded = LearnedRules.encode(merged)
        prefs.setLearnedRules(encoded)
        LearnedRules.current = LearnedRules.parse(encoded)
    }
}
