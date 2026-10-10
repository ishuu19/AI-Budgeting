package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TransactionType

/** What a voice capture became. Stored by name in `voice_history.resultKind`. */
enum class VoiceResultKind(val label: String) {
    Spend("Spend"),
    Income("Income"),
    Task("Task"),
    Reminder("Reminder"),
    Event("Event"),
    Exam("Exam"),
    Routine("Routine"),
    Alarm("Alarm"),
    Budget("Budget"),
    Bill("Bill"),
    Debt("Debt"),
    Goal("Goal"),
    Job("Job"),
    Edit("Edit"),
    Note("Note"),
    Unsorted("Unsorted");

    companion object {
        fun fromName(name: String?): VoiceResultKind =
            entries.firstOrNull { it.name == name } ?: Unsorted

        /** Kinds a user can pick when relabelling or redoing a capture. */
        val pickable: List<VoiceResultKind> = entries.filter { it != Unsorted }
    }
}

fun ParsedIntent.resultKind(): VoiceResultKind = when (this) {
    is ParsedIntent.Transaction -> if (type == TransactionType.INCOME) VoiceResultKind.Income else VoiceResultKind.Spend
    is ParsedIntent.Event -> when (kind) {
        CalendarEventKind.ALARM -> VoiceResultKind.Alarm
        CalendarEventKind.ROUTINE -> VoiceResultKind.Routine
        CalendarEventKind.EXAM -> VoiceResultKind.Exam
        CalendarEventKind.TASK ->
            if (reminders.any { it.offsetMinutes == 0 }) VoiceResultKind.Reminder else VoiceResultKind.Task
        else -> VoiceResultKind.Event
    }
    is ParsedIntent.Note -> VoiceResultKind.Note
    is ParsedIntent.Bill -> VoiceResultKind.Bill
    is ParsedIntent.Debt -> VoiceResultKind.Debt
    is ParsedIntent.Goal -> VoiceResultKind.Goal
    is ParsedIntent.Budget -> VoiceResultKind.Budget
    is ParsedIntent.Job -> VoiceResultKind.Job
    is ParsedIntent.Adjust -> VoiceResultKind.Edit
    is ParsedIntent.Unmatched -> VoiceResultKind.Unsorted
}
