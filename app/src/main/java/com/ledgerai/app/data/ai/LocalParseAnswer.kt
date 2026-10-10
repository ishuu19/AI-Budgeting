package com.ledgerai.app.data.ai

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Reads the one-word answer from the on-phone model. The model only chooses the kind.
 * Dates, amounts, and names still come from the sentence.
 */
object LocalParseAnswer {
    fun read(
        raw: String,
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): ParsedIntent? {
        val kinds = listOf(
            "SPEND", "EXPENSE", "TRANSACTION", "INCOME", "TASK", "REMINDER", "ALARM", "EVENT",
            "NOTE", "BILL", "DEBT", "GOAL", "BUDGET", "JOB", "DELETE", "EDIT"
        )
        val line = raw.lineSequence().map { it.trim().uppercase() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val hits = kinds.filter { Regex("""\b$it\b""").containsMatchIn(line) }
        if (hits.size != 1) return null
        val token = hits.single()
        val kind = when (token) {
            "SPEND", "EXPENSE", "TRANSACTION" -> VoiceResultKind.Spend
            "INCOME" -> VoiceResultKind.Income
            "TASK" -> VoiceResultKind.Task
            "REMINDER" -> VoiceResultKind.Reminder
            "ALARM" -> VoiceResultKind.Alarm
            "EVENT" -> VoiceResultKind.Event
            "NOTE" -> VoiceResultKind.Note
            "BILL" -> VoiceResultKind.Bill
            "DEBT" -> VoiceResultKind.Debt
            "GOAL" -> VoiceResultKind.Goal
            "BUDGET" -> VoiceResultKind.Budget
            "JOB" -> VoiceResultKind.Job
            "DELETE", "EDIT" -> VoiceResultKind.Edit
            else -> return null
        }
        return QuickParse.asKind(kind, transcript, today, now).takeUnless { it is ParsedIntent.Unmatched }
    }
}
