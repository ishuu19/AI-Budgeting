package com.ledgerai.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class PlanBlockKind { STUDY, HABIT }

enum class PlanBlockStatus { SCHEDULED, IN_PROGRESS, DONE, SKIPPED, MOVED }

enum class HabitCategory { EXERCISE, READING, PRAYER, CUSTOM }

enum class HabitOutcome { DONE, SKIPPED, MISSED }

enum class NudgeProposalState { PENDING, ACCEPTED, DISMISSED }

enum class SpeculationDirection { EXPENSE, INCOME }

enum class SpeculationConfidence { LOW, MEDIUM, HIGH }

enum class JobApplicationStatus {
    APPLIED, SCREENING, INTERVIEW, OFFER, REJECTED, WITHDRAWN
}

enum class ActivityEntrySource { MANUAL, VOICE, SUGGESTED }

enum class CheckinWindowState { PENDING, ANSWERED, GAP }

enum class LeaveRefType { CALENDAR_EVENT }

data class StudyPlan(
    val id: Long = 0,
    val topic: String,
    val hoursTotal: Double,
    val deadline: LocalDate? = null,
    val sessionLenMinutes: Int = 50,
    val status: String = "active"
)

data class PlanBlock(
    val id: Long = 0,
    val kind: PlanBlockKind,
    val title: String,
    val topic: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: PlanBlockStatus = PlanBlockStatus.SCHEDULED,
    val sourcePlanId: Long? = null,
    val habitId: Long? = null,
    val actualMinutes: Int? = null
)

data class Habit(
    val id: Long = 0,
    val title: String,
    val category: HabitCategory = HabitCategory.CUSTOM,
    val daysMask: Int = 0,
    val startTime: LocalTime,
    val durationMinutes: Int = 30,
    val nudgeEnabled: Boolean = true,
    val quietOverride: Boolean = false
)

data class SpendSpeculation(
    val id: Long = 0,
    val label: String,
    val amount: Double,
    val direction: SpeculationDirection,
    val expectedDate: LocalDate,
    val confidence: SpeculationConfidence = SpeculationConfidence.MEDIUM
)

data class JobApplication(
    val id: Long = 0,
    val company: String,
    val title: String,
    val url: String = "",
    val source: String = "",
    val status: JobApplicationStatus = JobApplicationStatus.APPLIED,
    val appliedOn: LocalDate,
    val followUpOn: LocalDate? = null,
    val notes: String = "",
    val contact: String = "",
    val location: String = "",
    /** Other dates, one per line: "Interview · 12 Oct 2026". */
    val extraDates: String = ""
)
