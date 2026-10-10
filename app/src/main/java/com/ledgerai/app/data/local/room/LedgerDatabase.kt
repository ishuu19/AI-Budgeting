package com.ledgerai.app.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ledgerai.app.data.household.HouseholdDao
import com.ledgerai.app.data.household.HouseholdEntity
import com.ledgerai.app.data.household.HouseholdMemberEntity
import com.ledgerai.app.data.inventory.ItemDao
import com.ledgerai.app.data.inventory.ItemEntity
import com.ledgerai.app.data.inventory.ShoppingItemDao
import com.ledgerai.app.data.inventory.ShoppingItemEntity
import com.ledgerai.app.data.inventory.ShoppingListDao
import com.ledgerai.app.data.inventory.ShoppingListEntity
import com.ledgerai.app.data.receipts.ReceiptDao
import com.ledgerai.app.data.receipts.ReceiptEntity
import com.ledgerai.app.data.receipts.ReceiptLineEntity

@Database(
    entities = [
        TransactionEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        GoalEntity::class,
        BillEntity::class,
        NoteEntity::class,
        CalendarEventEntity::class,
        EventReminderEntity::class,
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
        JobApplicationEntity::class,
        VoiceHistoryEntity::class,
        AssistantActionEntity::class,
        EventLogEntity::class,
        HouseholdEntity::class,
        HouseholdMemberEntity::class,
        ItemEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        ReceiptEntity::class,
        ReceiptLineEntity::class,
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun goalDao(): GoalDao
    abstract fun billDao(): BillDao
    abstract fun noteDao(): NoteDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun eventReminderDao(): EventReminderDao

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
    abstract fun voiceHistoryDao(): VoiceHistoryDao
    abstract fun assistantAuditDao(): AssistantAuditDao
    abstract fun householdDao(): HouseholdDao
    abstract fun itemDao(): ItemDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun receiptDao(): ReceiptDao
}
