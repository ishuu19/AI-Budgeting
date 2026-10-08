package com.ledgerai.app.data.repository

import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.TaskEventKind
import com.ledgerai.app.domain.model.TaskItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleDraftRepository @Inject constructor(
    private val aiRepo: AiRepository,
    private val calendarRepo: CalendarRepository,
    private val taskRepo: TaskRepository,
) {
    suspend fun loadSuggestions(): Result<List<ScheduleDraftDto>> = aiRepo.suggestScheduleDrafts()

    suspend fun acceptDraft(draft: ScheduleDraftDto): Long {
        val title = draft.title?.trim().orEmpty().ifBlank { "Item" }
        val start = parseStart(draft.startAt) ?: LocalDateTime.now().plusHours(1)
        val type = draft.type?.uppercase() ?: "TASK"
        return when (type) {
            "EVENT", "EXAM" -> {
                val kind = if (type == "EXAM") CalendarEventKind.EXAM else CalendarEventKind.PERSONAL
                calendarRepo.upsert(
                    CalendarEvent(
                        title = title,
                        startAt = start,
                        endAt = start.plusHours(1),
                        kind = kind,
                        recurrence = EventRecurrence(frequency = RecurrenceFrequency.NONE)
                    )
                )
            }
            else -> {
                taskRepo.insert(
                    TaskItem(
                        title = title,
                        dueAt = start,
                        eventKind = TaskEventKind.TASK
                    )
                )
            }
        }
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
