package com.ledgerai.app.data.repository

import com.ledgerai.app.data.ai.NoteLexicon
import com.ledgerai.app.data.ai.NoteNudgeSuggestion
import com.ledgerai.app.data.ai.NoteRules
import com.ledgerai.app.data.local.room.NudgeProposalDao
import com.ledgerai.app.data.local.room.NudgeProposalEntity
import com.ledgerai.app.domain.model.NudgeProposalState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NudgeProposalRepository @Inject constructor(
    private val dao: NudgeProposalDao,
    private val aiRepo: AiRepository,
    private val lexicon: NoteLexicon,
) {
    fun observePending(): Flow<List<NudgeProposalEntity>> = dao.observePending()

    /**
     * Proposes nudges for an opted-in note. Rules run first ([NoteRules.nudges]); the cloud is asked only when
     * the rules find nothing. A proposal whose message already exists for the note is skipped, whatever its state.
     */
    suspend fun scanNote(noteId: Long, body: String): List<NudgeProposalEntity> {
        if (!NoteRules.optedIn(body)) return emptyList()
        val now = LocalDateTime.now()
        var found: List<NoteNudgeSuggestion> = NoteRules.nudges(body, now, lexicon.triggers)
        if (found.isEmpty()) {
            found = aiRepo.scanNoteForNudges(body).getOrNull().orEmpty()
                .filter { !it.message.isNullOrBlank() }
                .map {
                    NoteNudgeSuggestion(
                        message = it.message.orEmpty().trim(),
                        at = parseWhen(it.suggestedAt, now),
                        reason = it.reason.orEmpty().ifBlank { "AI" }
                    )
                }
        }
        val created = mutableListOf<NudgeProposalEntity>()
        for (p in found.take(3)) {
            if (dao.countFor(noteId, p.message) > 0) continue
            val entity = NudgeProposalEntity(
                noteId = noteId,
                message = p.message,
                suggestedAt = p.at,
                reason = p.reason,
                state = NudgeProposalState.PENDING
            )
            created += entity.copy(id = dao.insert(entity))
        }
        return created
    }

    suspend fun accept(id: Long) {
        dao.updateState(id, NudgeProposalState.ACCEPTED.name)
    }

    suspend fun dismiss(id: Long) {
        dao.updateState(id, NudgeProposalState.DISMISSED.name)
    }

    private fun parseWhen(raw: String?, now: LocalDateTime): LocalDateTime {
        if (raw.isNullOrBlank()) return now.plusHours(1)
        return try {
            LocalDateTime.parse(raw)
        } catch (_: Exception) {
            now.plusHours(1)
        }
    }
}
