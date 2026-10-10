package com.ledgerai.app.data.repository

import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.data.ai.ScheduleRules
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleDraftRepository @Inject constructor(
    private val aiRepo: AiRepository,
    private val calendarRepo: CalendarRepository,
    private val billRepo: BillRepository,
) {
    /**
     * Rules first: clashes, bill reminders, exam prep and study blocks from local data.
     * The cloud is asked only when the rules have nothing to suggest. Each reason starts with Rules or AI.
     */
    suspend fun loadSuggestions(): Result<List<ScheduleDraftDto>> {
        val ruled = runCatching {
            ScheduleRules.suggest(
                now = LocalDateTime.now(),
                events = calendarRepo.listNextDays(14),
                bills = billRepo.getActiveBills().first(),
            )
        }.getOrDefault(emptyList())
        if (ruled.isNotEmpty()) return Result.success(ruled)
        return aiRepo.suggestScheduleDrafts().map { list ->
            list.map { it.copy(reason = "AI: " + it.reason.orEmpty().ifBlank { "suggested" }) }
        }
    }

    suspend fun acceptDraft(draft: ScheduleDraftDto): Long {
        val title = draft.title?.trim().orEmpty().ifBlank { "Item" }
        val start = parseStart(draft.startAt) ?: LocalDateTime.now().plusHours(1)
        val type = draft.type?.uppercase() ?: "TASK"
        val kind = when (type) {
            "EXAM" -> CalendarEventKind.EXAM
            "EVENT" -> CalendarEventKind.EVENT
            else -> CalendarEventKind.TASK
        }
        val isTask = kind == CalendarEventKind.TASK
        return calendarRepo.upsert(
            CalendarEvent(
                title = title,
                startAt = start,
                endAt = if (isTask) start else start.plusHours(1),
                kind = kind,
                recurrence = EventRecurrence(frequency = RecurrenceFrequency.NONE)
            ),
            withDefaultReminders = true
        )
    }

    private fun parseStart(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(raw)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } catch (_: Exception) {
                null
            }
        }
    }
}
