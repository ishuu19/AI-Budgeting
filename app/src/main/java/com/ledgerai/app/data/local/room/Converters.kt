package com.ledgerai.app.data.local.room

import androidx.room.TypeConverter
import com.ledgerai.app.domain.model.ActivityEntrySource
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.domain.model.HabitCategory
import com.ledgerai.app.domain.model.HabitOutcome
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.model.NudgeProposalState
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromTransactionCategory(value: TransactionCategory): String = value.name

    @TypeConverter
    fun toTransactionCategory(value: String): TransactionCategory = TransactionCategory.valueOf(value)

    @TypeConverter
    fun fromDebtDirection(value: DebtDirection): String = value.name

    @TypeConverter
    fun toDebtDirection(value: String): DebtDirection = DebtDirection.valueOf(value)

    @TypeConverter
    fun fromBillFrequency(value: BillFrequency): String = value.name

    @TypeConverter
    fun toBillFrequency(value: String): BillFrequency = BillFrequency.valueOf(value)

    @TypeConverter
    fun fromCalendarEventKind(value: CalendarEventKind): String = value.name

    @TypeConverter
    fun toCalendarEventKind(value: String): CalendarEventKind = CalendarEventKind.parse(value)

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String = value.name

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(value)

    @TypeConverter fun fromPlanBlockKind(v: PlanBlockKind) = v.name
    @TypeConverter fun toPlanBlockKind(v: String) = PlanBlockKind.valueOf(v)

    @TypeConverter fun fromPlanBlockStatus(v: PlanBlockStatus) = v.name
    @TypeConverter fun toPlanBlockStatus(v: String) = PlanBlockStatus.valueOf(v)

    @TypeConverter fun fromHabitCategory(v: HabitCategory) = v.name
    @TypeConverter fun toHabitCategory(v: String) = HabitCategory.valueOf(v)

    @TypeConverter fun fromHabitOutcome(v: HabitOutcome) = v.name
    @TypeConverter fun toHabitOutcome(v: String) = HabitOutcome.valueOf(v)

    @TypeConverter fun fromNudgeProposalState(v: NudgeProposalState) = v.name
    @TypeConverter fun toNudgeProposalState(v: String) = NudgeProposalState.valueOf(v)

    @TypeConverter fun fromSpeculationDirection(v: SpeculationDirection) = v.name
    @TypeConverter fun toSpeculationDirection(v: String) = SpeculationDirection.valueOf(v)

    @TypeConverter fun fromSpeculationConfidence(v: SpeculationConfidence) = v.name
    @TypeConverter fun toSpeculationConfidence(v: String) = SpeculationConfidence.valueOf(v)

    @TypeConverter fun fromLeaveRefType(v: LeaveRefType) = v.name
    @TypeConverter fun toLeaveRefType(v: String) = LeaveRefType.valueOf(v)

    @TypeConverter fun fromActivityEntrySource(v: ActivityEntrySource) = v.name
    @TypeConverter fun toActivityEntrySource(v: String) = ActivityEntrySource.valueOf(v)

    @TypeConverter fun fromCheckinWindowState(v: CheckinWindowState) = v.name
    @TypeConverter fun toCheckinWindowState(v: String) = CheckinWindowState.valueOf(v)

    @TypeConverter fun fromJobApplicationStatus(v: JobApplicationStatus) = v.name
    @TypeConverter fun toJobApplicationStatus(v: String) = JobApplicationStatus.valueOf(v)
}
