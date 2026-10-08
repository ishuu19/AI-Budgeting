package com.ledgerai.app.data.ai

import com.google.gson.annotations.SerializedName
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import com.ledgerai.app.domain.schedule.AlarmDays

/** OpenAI-compatible chat request (OpenRouter + DeepSeek). */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int? = 1024,
)

data class ChatMessageDto(
    val role: String,
    val content: String,
)

data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto>? = null,
    val error: ChatErrorDto? = null,
)

data class ChatChoiceDto(
    val message: ChatMessageDto? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

data class ChatErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

/** Gemini generateContent request/response. */
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inlineData") val inlineData: GeminiInlineData? = null,
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    val data: String,
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.3,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 1024,
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDto? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null,
)

data class GeminiErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

/** Structured parse payload expected from the model (JSON in content). */
data class ParsedTransactionDto(
    val amount: Double? = null,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val type: String? = null,
    val confidence: Float? = null,
)

/**
 * Multi-intent voice parse payload from the model (JSON in content).
 * [intent]: TRANSACTION | EVENT | TASK | EXAM | REMINDER | ALARM | ROUTINE | NOTE | BILL | DEBT | GOAL | BUDGET | JOB.
 * The model may answer with several of these in an `items` array (or a bare array), one per spoken item.
 * Everything that lands on the calendar (EVENT, TASK, EXAM, REMINDER, ALARM, ROUTINE) becomes one
 * [ParsedIntent.Event].
 */
data class ParsedVoiceIntentDto(
    val intent: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val type: String? = null,
    val confidence: Float? = null,
    val title: String? = null,
    val body: String? = null,
    /** Full local date and time (ISO-8601) of the event, task, reminder or first alarm. */
    @SerializedName("start_at") val startAt: String? = null,
    @SerializedName("end_at") val endAt: String? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("remind_at") val remindAt: String? = null,
    /** Minutes before the start for each reminder; 0 = at time. */
    @SerializedName("reminder_minutes") val reminderMinutes: List<Int>? = null,
    val label: String? = null,
    /** Alarm clock time as HH:mm (24h). */
    val time: String? = null,
    val tags: List<String>? = null,
    /** Routine repeat rule: DAILY | WEEKLY | WEEKDAYS | CUSTOM. */
    @SerializedName("repeat_rule") val repeatRule: String? = null,
    /** Alarm weekday bitmask Sun=1 … Sat=64; 0 = one-shot. */
    @SerializedName("repeat_days") val repeatDays: Int? = null,
    /** DEBT: I_OWE | THEY_OWE. */
    val direction: String? = null,
    /** DEBT: the other person. BILL, GOAL, BUDGET: the name. */
    val name: String? = null,
)

/** Result of [com.ledgerai.app.data.repository.AiRepository.parseVoiceIntent]. */
sealed class ParsedIntent {
    abstract val confidence: Float
    abstract val rawTranscript: String

    data class Transaction(
        val amount: Double?,
        val category: TransactionCategory,
        val merchant: String,
        val date: LocalDate,
        val note: String,
        val type: TransactionType = TransactionType.EXPENSE,
        override val confidence: Float = 1.0f,
        override val rawTranscript: String = note,
    ) : ParsedIntent() {
        fun toParsedTransaction() = ParsedTransaction(
            amount = amount,
            category = category,
            merchant = merchant,
            date = date,
            note = note,
            type = type,
            confidence = confidence,
        )

        companion object {
            fun from(parsed: ParsedTransaction, raw: String = parsed.note) = Transaction(
                amount = parsed.amount,
                category = parsed.category,
                merchant = parsed.merchant,
                date = parsed.date,
                note = parsed.note,
                type = parsed.type,
                confidence = parsed.confidence,
                rawTranscript = raw,
            )
        }
    }

    /**
     * Anything that goes on the calendar: event, task, exam, routine, alarm or a reminder
     * (a task with an at-time reminder). [startAt] is the full spoken date and time.
     * Empty [reminders] means the standard defaults for kinds that support reminders.
     */
    data class Event(
        val title: String,
        val notes: String = "",
        val startAt: LocalDateTime,
        val endAt: LocalDateTime? = null,
        val kind: CalendarEventKind = CalendarEventKind.TASK,
        val repeat: EventRecurrence? = null,
        val reminders: List<EventReminder> = emptyList(),
        override val confidence: Float = 0.7f,
        override val rawTranscript: String = "",
    ) : ParsedIntent() {
        /** Sun=1 ... Sat=64 mask for alarms, derived from [repeat]. */
        val alarmRepeatDays: Int get() = AlarmDays.fromIsoDays(repeat?.weekDays.orEmpty())

        fun toCalendarEvent(): CalendarEvent {
            val point = kind == CalendarEventKind.TASK || kind == CalendarEventKind.ALARM
            return CalendarEvent(
                title = title.trim(),
                notes = notes.trim(),
                startAt = startAt,
                endAt = endAt?.takeIf { it.isAfter(startAt) } ?: if (point) startAt else startAt.plusHours(1),
                kind = kind,
                recurrence = repeat,
                alarmRepeatDays = alarmRepeatDays,
                reminders = reminders
            )
        }
    }

    data class Note(
        val title: String,
        val body: String = "",
        val tags: List<String> = emptyList(),
        override val confidence: Float = 0.7f,
        override val rawTranscript: String = "",
    ) : ParsedIntent()

    data class Bill(
        val name: String,
        val amount: Double,
        val frequency: BillFrequency = BillFrequency.MONTHLY,
        val nextDueDate: LocalDate,
        val category: TransactionCategory = TransactionCategory.SUBSCRIPTIONS,
        override val rawTranscript: String = "",
        override val confidence: Float = 0.7f,
    ) : ParsedIntent()

    data class Debt(
        val friendName: String,
        val amount: Double,
        val direction: DebtDirection,
        val dueDate: LocalDate? = null,
        override val rawTranscript: String = "",
        override val confidence: Float = 0.7f,
    ) : ParsedIntent()

    data class Goal(
        val name: String,
        val targetAmount: Double,
        override val rawTranscript: String = "",
        override val confidence: Float = 0.7f,
    ) : ParsedIntent()

    /**
     * A job application. Kept off the main calendar. [followUpOn] is the date on the jobs calendar.
     */
    data class Job(
        val company: String,
        val title: String,
        val status: JobApplicationStatus = JobApplicationStatus.APPLIED,
        val appliedOn: LocalDate,
        val followUpOn: LocalDate? = null,
        val notes: String = "",
        override val rawTranscript: String = "",
        override val confidence: Float = 0.7f,
    ) : ParsedIntent()

    /** Monthly limit for a category, for the current month. */
    data class Budget(
        val category: TransactionCategory,
        val limit: Double,
        override val rawTranscript: String = "",
        override val confidence: Float = 0.7f,
    ) : ParsedIntent()

    /** Nothing in the utterance could be recognised. The user can edit it, retry or keep it as a note. */
    data class Unmatched(
        override val rawTranscript: String = "",
        override val confidence: Float = 0f,
    ) : ParsedIntent()
}

data class ForecastItemDto(
    val month: String? = null,
    @SerializedName("predicted_spend") val predictedSpend: Double? = null,
    @SerializedName("recommended_budget") val recommendedBudget: Double? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    val insight: String? = null,
)

data class ForecastListDto(
    val forecasts: List<ForecastItemDto>? = null,
)

/** Edge Function request / response. */
data class AudioPayload(
    val mimeType: String,
    /** Base64 (no wrapping) of the recorded audio. */
    val data: String,
)

data class AiProxyRequest(
    val type: String,
    val system: String? = null,
    val user: String? = null,
    val modelTier: String? = null,
    val audio: AudioPayload? = null,
)

data class AiProxyResponse(
    val type: String? = null,
    val data: com.google.gson.JsonElement? = null,
    val error: String? = null,
    val provider: String? = null,
)

data class ChatReplyDto(
    val reply: String? = null,
)

data class InsightDto(
    val title: String? = null,
    val body: String? = null,
    val severity: String? = null,
    val actions: List<String>? = null,
)

data class NoteSummaryDto(
    val summary: String? = null,
    val tags: List<String>? = null,
    val highlights: List<String>? = null,
)

data class ScheduleDraftListDto(
    val drafts: List<ScheduleDraftDto>? = null,
)

data class ScheduleDraftDto(
    val type: String? = null,
    val title: String? = null,
    @SerializedName("start_at") val startAt: String? = null,
    val reason: String? = null,
)

data class NoteNudgeScanDto(
    val proposals: List<NoteNudgeProposalDto>? = null,
)

data class NoteNudgeProposalDto(
    val message: String? = null,
    @SerializedName("suggested_at") val suggestedAt: String? = null,
    val reason: String? = null,
)

data class SpendGuideExplainDto(
    val headline: String? = null,
    val reasons: List<String>? = null,
)
