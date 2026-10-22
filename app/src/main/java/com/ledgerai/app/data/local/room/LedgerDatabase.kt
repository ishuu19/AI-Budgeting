package com.ledgerai.app.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.achievements.AchievementEntity
import com.ledgerai.app.data.appusage.AppUsageDao
import com.ledgerai.app.data.appusage.AppUsageDayEntity
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayEntity
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.dates.TripEntity
import com.ledgerai.app.data.passwords.PasswordDao
import com.ledgerai.app.data.passwords.PasswordEntity
import com.ledgerai.app.data.reminders.ReminderDao
import com.ledgerai.app.data.reminders.ReminderEntity
import com.ledgerai.app.data.vault.FolderDao
import com.ledgerai.app.data.vault.FolderEntity
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishEntity
import com.ledgerai.app.data.household.HouseholdDao
import com.ledgerai.app.data.household.HouseholdEntity
import com.ledgerai.app.data.household.HouseholdMemberEntity
import com.ledgerai.app.data.inventory.ItemDao
import com.ledgerai.app.data.inventory.ItemEntity
import com.ledgerai.app.data.inventory.ShoppingItemDao
import com.ledgerai.app.data.inventory.ShoppingItemEntity
import com.ledgerai.app.data.inventory.ShoppingListDao
import com.ledgerai.app.data.inventory.ShoppingListEntity
import com.ledgerai.app.data.memory.MemoryDao
import com.ledgerai.app.data.memory.MemoryEntity
import com.ledgerai.app.data.people.CommitmentDao
import com.ledgerai.app.data.people.CommitmentEntity
import com.ledgerai.app.data.people.InteractionDao
import com.ledgerai.app.data.people.InteractionEntity
import com.ledgerai.app.data.people.PersonDao
import com.ledgerai.app.data.people.PersonEntity
import com.ledgerai.app.data.receipts.ReceiptDao
import com.ledgerai.app.data.receipts.ReceiptEntity
import com.ledgerai.app.data.receipts.ReceiptLineEntity
import com.ledgerai.app.data.subscriptions.SubscriptionDao
import com.ledgerai.app.data.subscriptions.SubscriptionEntity
import com.ledgerai.app.data.chat.ChatDao
import com.ledgerai.app.data.chat.ChatMessageEntity
import com.ledgerai.app.data.chat.ChatThreadEntity
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.data.media.MediaAssetDao
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.wardrobe.OutfitDao
import com.ledgerai.app.data.wardrobe.OutfitEntity
import com.ledgerai.app.data.wardrobe.WardrobeItemDao
import com.ledgerai.app.data.wardrobe.WardrobeItemEntity

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
        PersonEntity::class,
        InteractionEntity::class,
        CommitmentEntity::class,
        MemoryEntity::class,
        SubscriptionEntity::class,
        WardrobeItemEntity::class,
        OutfitEntity::class,
        MediaAssetEntity::class,
        ClipEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        FolderEntity::class,
        BirthdayEntity::class,
        TripEntity::class,
        AchievementEntity::class,
        WishEntity::class,
        PasswordEntity::class,
        AppUsageDayEntity::class,
        ReminderEntity::class,
    ],
    version = 22,
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
    abstract fun personDao(): PersonDao
    abstract fun interactionDao(): InteractionDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun wardrobeItemDao(): WardrobeItemDao
    abstract fun outfitDao(): OutfitDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun clipDao(): ClipDao
    abstract fun chatDao(): ChatDao
    abstract fun folderDao(): FolderDao
    abstract fun birthdayDao(): BirthdayDao
    abstract fun tripDao(): TripDao
    abstract fun achievementDao(): AchievementDao
    abstract fun wishDao(): WishDao
    abstract fun passwordDao(): PasswordDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun reminderDao(): ReminderDao
}
