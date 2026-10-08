package com.ledgerai.app.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ledgerai.app.domain.model.ActivityEntrySource
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.domain.model.HabitCategory
import com.ledgerai.app.domain.model.HabitOutcome
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.NudgeProposalState
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val hoursTotal: Double,
    val deadline: LocalDate? = null,
    val sessionLenMinutes: Int = 50,
    val status: String = "active",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "plan_blocks")
data class PlanBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: PlanBlockKind,
    val title: String,
    val topic: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val status: PlanBlockStatus = PlanBlockStatus.SCHEDULED,
    val sourcePlanId: Long? = null,
    val habitId: Long? = null,
    val actualMinutes: Int? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: HabitCategory = HabitCategory.CUSTOM,
    val daysMask: Int = 0,
    val startTime: LocalTime,
    val durationMinutes: Int = 30,
    val nudgeEnabled: Boolean = true,
    val quietOverride: Boolean = false,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val outcome: HabitOutcome,
    val minutes: Int? = null,
    val updatedAt: Long = 0L
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime? = null,
    val preset: String = "deep"
)

@Entity(tableName = "nudge_proposals")
data class NudgeProposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val message: String,
    val suggestedAt: LocalDateTime,
    val reason: String = "",
    val state: NudgeProposalState = NudgeProposalState.PENDING
)

@Entity(tableName = "spend_speculations")
data class SpendSpeculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val amount: Double,
    val direction: SpeculationDirection,
    val expectedDate: LocalDate,
    val confidence: SpeculationConfidence = SpeculationConfidence.MEDIUM,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "spend_guide_days")
data class SpendGuideDayEntity(
    @PrimaryKey val date: LocalDate,
    val guideAmount: Double,
    val spent: Double = 0.0,
    val buffer: Double = 0.0,
    val marginUsed: Double = 0.0
)

@Entity(tableName = "leave_rules")
data class LeaveRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val refType: LeaveRefType,
    val refId: Long,
    val placeLabel: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val travelMinutes: Int = 15,
    val bufferMinutes: Int = 5,
    val enabled: Boolean = true
)

@Entity(tableName = "location_points")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: LocalDateTime,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float = 0f
)

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeName: String,
    val lat: Double,
    val lng: Double,
    val arrivedAt: LocalDateTime,
    val leftAt: LocalDateTime? = null
)

@Entity(tableName = "activity_entries")
data class ActivityEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val text: String,
    val source: ActivityEntrySource = ActivityEntrySource.MANUAL,
    val visitId: Long? = null
)

@Entity(tableName = "checkin_windows")
data class CheckinWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val state: CheckinWindowState = CheckinWindowState.PENDING
)

@Entity(tableName = "job_applications")
data class JobApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val company: String,
    val title: String,
    val url: String = "",
    val source: String = "",
    val status: JobApplicationStatus = JobApplicationStatus.APPLIED,
    val appliedOn: LocalDate,
    val followUpOn: LocalDate? = null,
    val notes: String = "",
    val contact: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)
