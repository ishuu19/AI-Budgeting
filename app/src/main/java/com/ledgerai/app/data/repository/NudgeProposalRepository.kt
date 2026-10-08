package com.ledgerai.app.data.repository

import com.ledgerai.app.data.ai.NoteNudgeProposalDto
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
    private val aiRepo: AiRepository
) {
    fun observePending(): Flow<List<NudgeProposalEntity>> = dao.observePending()

    suspend fun scanNote(noteId: Long, body: String): List<NudgeProposalEntity> {
        if (!body.contains("[nudge]", ignoreCase = true) && !body.contains("#nudge", ignoreCase = true)) {
            return emptyList()
        }
        val proposals = aiRepo.scanNoteForNudges(body).getOrElse { heuristicNudges(body) }
        return proposals.take(3).map { p ->
            val id = dao.insert(
                NudgeProposalEntity(
                    noteId = noteId,
                    message = p.message.orEmpty(),
                    suggestedAt = parseWhen(p.suggestedAt),
                    reason = p.reason.orEmpty(),
                    state = NudgeProposalState.PENDING
                )
            )
            NudgeProposalEntity(
                id = id,
                noteId = noteId,
                message = p.message.orEmpty(),
                suggestedAt = parseWhen(p.suggestedAt),
                reason = p.reason.orEmpty()
            )
        }
    }

    suspend fun accept(id: Long) {
        dao.updateState(id, NudgeProposalState.ACCEPTED.name)
    }

    suspend fun dismiss(id: Long) {
        dao.updateState(id, NudgeProposalState.DISMISSED.name)
    }

    private fun heuristicNudges(body: String): List<NoteNudgeProposalDto> {
        val line = body.lines().firstOrNull { it.contains("remind", ignoreCase = true) } ?: body.take(120)
        return listOf(
            NoteNudgeProposalDto(
                message = line.trim(),
                suggestedAt = LocalDateTime.now().plusHours(2).toString(),
                reason = "From note"
            )
        )
    }

    private fun parseWhen(raw: String?): LocalDateTime {
        if (raw.isNullOrBlank()) return LocalDateTime.now().plusHours(1)
        return try {
            LocalDateTime.parse(raw)
        } catch (_: Exception) {
            LocalDateTime.now().plusHours(1)
        }
    }
}
