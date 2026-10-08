package com.ledgerai.app.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        GoalEntity::class,
        BillEntity::class,
        TaskEntity::class,
        TaskReminderEntity::class,
        RoutineEntity::class,
        AlarmEntity::class,
        NoteEntity::class,
        CourseEntity::class,
        ScheduleSlotEntity::class,
        RoutineSlotReminderEntity::class,
        CalendarEventEntity::class,
        ScheduleSlotExceptionEntity::class,
        StudyPlanEntity::class,
        PlanBlockEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FocusSessionEntity::class,
        NudgeProposalEntity::class,
        SpendSpeculationEntity::class,
        SpendGuideDayEntity::class,
        LeaveRuleEntity::class,
        LocationPointEntity::class,
        VisitEntity::class,
        ActivityEntryEntity::class,
        CheckinWindowEntity::class,
        JobApplicationEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun goalDao(): GoalDao
    abstract fun billDao(): BillDao
    abstract fun taskDao(): TaskDao
    abstract fun taskReminderDao(): TaskReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun alarmDao(): AlarmDao
    abstract fun noteDao(): NoteDao
    abstract fun courseDao(): CourseDao
    abstract fun scheduleSlotDao(): ScheduleSlotDao
    abstract fun routineSlotReminderDao(): RoutineSlotReminderDao
    abstract fun calendarEventDao(): CalendarEventDao

    abstract fun scheduleSlotExceptionDao(): ScheduleSlotExceptionDao

    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun planBlockDao(): PlanBlockDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun nudgeProposalDao(): NudgeProposalDao
    abstract fun spendSpeculationDao(): SpendSpeculationDao
    abstract fun spendGuideDayDao(): SpendGuideDayDao
    abstract fun leaveRuleDao(): LeaveRuleDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun visitDao(): VisitDao
    abstract fun activityEntryDao(): ActivityEntryDao
    abstract fun checkinWindowDao(): CheckinWindowDao
    abstract fun jobApplicationDao(): JobApplicationDao
}
