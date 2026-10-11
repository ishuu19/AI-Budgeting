package com.ledgerai.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.EventReminderDao
import com.ledgerai.app.data.local.room.MIGRATION_6_7
import com.ledgerai.app.data.local.room.MIGRATION_7_8
import com.ledgerai.app.data.local.room.MIGRATION_8_9
import com.ledgerai.app.data.local.room.AssistantAuditDao
import com.ledgerai.app.data.local.room.MIGRATION_9_10
import com.ledgerai.app.data.local.room.MIGRATION_10_11
import com.ledgerai.app.data.local.room.MIGRATION_11_12
import com.ledgerai.app.data.local.room.MIGRATION_12_13
import com.ledgerai.app.data.local.room.MIGRATION_13_14
import com.ledgerai.app.data.local.room.MIGRATION_14_15
import com.ledgerai.app.data.local.room.MIGRATION_15_16
import com.ledgerai.app.data.local.room.MIGRATION_16_17
import com.ledgerai.app.data.local.room.MIGRATION_17_18
import com.ledgerai.app.data.media.MediaAssetDao
import com.ledgerai.app.data.local.room.VoiceHistoryDao
import com.ledgerai.app.data.household.HouseholdDao
import com.ledgerai.app.data.household.HouseholdRepository
import com.ledgerai.app.data.memory.MemoryDao
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.memory.MemoryStore
import com.ledgerai.app.data.memory.RoomMemoryStore
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.inventory.ItemDao
import com.ledgerai.app.data.inventory.ShoppingItemDao
import com.ledgerai.app.data.inventory.ShoppingListDao
import com.ledgerai.app.data.people.CommitmentDao
import com.ledgerai.app.data.people.CommitmentRepository
import com.ledgerai.app.data.people.CommitmentStore
import com.ledgerai.app.data.people.InteractionDao
import com.ledgerai.app.data.people.InteractionRepository
import com.ledgerai.app.data.people.InteractionStore
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.people.PersonDao
import com.ledgerai.app.data.people.PersonStore
import com.ledgerai.app.data.people.RoomCommitmentStore
import com.ledgerai.app.data.people.RoomInteractionStore
import com.ledgerai.app.data.people.RoomPersonStore
import com.ledgerai.app.data.receipts.ReceiptDao
import com.ledgerai.app.data.receipts.ReceiptRepository
import com.ledgerai.app.data.receipts.RoomReceiptStore
import com.ledgerai.app.data.subscriptions.SubscriptionDao
import com.ledgerai.app.data.subscriptions.SubscriptionRepository
import com.ledgerai.app.data.wardrobe.OutfitDao
import com.ledgerai.app.data.wardrobe.WardrobeItemDao
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.LedgerDatabase
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.StudyPlanDao
import com.ledgerai.app.data.local.room.PlanBlockDao
import com.ledgerai.app.data.local.room.HabitDao
import com.ledgerai.app.data.local.room.HabitLogDao
import com.ledgerai.app.data.local.room.FocusSessionDao
import com.ledgerai.app.data.local.room.NudgeProposalDao
import com.ledgerai.app.data.local.room.SpendSpeculationDao
import com.ledgerai.app.data.local.room.SpendGuideDayDao
import com.ledgerai.app.data.local.room.LeaveRuleDao
import com.ledgerai.app.data.local.room.LocationPointDao
import com.ledgerai.app.data.local.room.VisitDao
import com.ledgerai.app.data.local.room.ActivityEntryDao
import com.ledgerai.app.data.local.room.CheckinWindowDao
import com.ledgerai.app.data.local.room.JobApplicationDao
import com.ledgerai.app.data.local.room.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLedgerDatabase(@ApplicationContext context: Context): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, "ledgerai.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides
    fun provideTransactionDao(db: LedgerDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideBudgetDao(db: LedgerDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideDebtDao(db: LedgerDatabase): DebtDao = db.debtDao()

    @Provides
    fun provideGoalDao(db: LedgerDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideBillDao(db: LedgerDatabase): BillDao = db.billDao()

    @Provides
    fun provideNoteDao(db: LedgerDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideCalendarEventDao(db: LedgerDatabase): CalendarEventDao = db.calendarEventDao()

    @Provides
    fun provideEventReminderDao(db: LedgerDatabase): EventReminderDao = db.eventReminderDao()

    @Provides fun provideStudyPlanDao(db: LedgerDatabase): StudyPlanDao = db.studyPlanDao()
    @Provides fun providePlanBlockDao(db: LedgerDatabase): PlanBlockDao = db.planBlockDao()
    @Provides fun provideHabitDao(db: LedgerDatabase): HabitDao = db.habitDao()
    @Provides fun provideHabitLogDao(db: LedgerDatabase): HabitLogDao = db.habitLogDao()
    @Provides fun provideFocusSessionDao(db: LedgerDatabase): FocusSessionDao = db.focusSessionDao()
    @Provides fun provideNudgeProposalDao(db: LedgerDatabase): NudgeProposalDao = db.nudgeProposalDao()
    @Provides fun provideSpendSpeculationDao(db: LedgerDatabase): SpendSpeculationDao =
        db.spendSpeculationDao()
    @Provides fun provideSpendGuideDayDao(db: LedgerDatabase): SpendGuideDayDao = db.spendGuideDayDao()
    @Provides fun provideLeaveRuleDao(db: LedgerDatabase): LeaveRuleDao = db.leaveRuleDao()
    @Provides fun provideLocationPointDao(db: LedgerDatabase): LocationPointDao = db.locationPointDao()
    @Provides fun provideVisitDao(db: LedgerDatabase): VisitDao = db.visitDao()
    @Provides fun provideActivityEntryDao(db: LedgerDatabase): ActivityEntryDao = db.activityEntryDao()
    @Provides fun provideCheckinWindowDao(db: LedgerDatabase): CheckinWindowDao = db.checkinWindowDao()
    @Provides fun provideJobApplicationDao(db: LedgerDatabase): JobApplicationDao = db.jobApplicationDao()
    @Provides fun provideVoiceHistoryDao(db: LedgerDatabase): VoiceHistoryDao = db.voiceHistoryDao()
    @Provides fun provideAssistantAuditDao(db: LedgerDatabase): AssistantAuditDao = db.assistantAuditDao()

    @Provides
    fun provideHouseholdDao(db: LedgerDatabase): HouseholdDao = db.householdDao()

    @Provides
    fun provideHouseholdRepository(dao: HouseholdDao): HouseholdRepository = HouseholdRepository(dao)

    @Provides
    fun provideItemDao(db: LedgerDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideShoppingListDao(db: LedgerDatabase): ShoppingListDao = db.shoppingListDao()

    @Provides
    fun provideShoppingItemDao(db: LedgerDatabase): ShoppingItemDao = db.shoppingItemDao()

    @Provides
    fun provideInventoryRepository(
        items: ItemDao,
        lists: ShoppingListDao,
        lines: ShoppingItemDao,
    ): InventoryRepository = InventoryRepository(items, lists, lines)

    @Provides
    fun provideReceiptDao(db: LedgerDatabase): ReceiptDao = db.receiptDao()

    @Provides
    fun provideReceiptRepository(dao: ReceiptDao): ReceiptRepository =
        ReceiptRepository(RoomReceiptStore(dao))

    @Provides
    fun providePersonDao(db: LedgerDatabase): PersonDao = db.personDao()

    @Provides
    fun provideInteractionDao(db: LedgerDatabase): InteractionDao = db.interactionDao()

    @Provides
    fun provideCommitmentDao(db: LedgerDatabase): CommitmentDao = db.commitmentDao()

    @Provides
    fun provideMemoryDao(db: LedgerDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun providePersonStore(dao: PersonDao): PersonStore = RoomPersonStore(dao)

    @Provides
    fun provideInteractionStore(dao: InteractionDao): InteractionStore = RoomInteractionStore(dao)

    @Provides
    fun provideCommitmentStore(dao: CommitmentDao): CommitmentStore = RoomCommitmentStore(dao)

    @Provides
    fun provideMemoryStore(dao: MemoryDao): MemoryStore = RoomMemoryStore(dao)

    @Provides
    fun providePeopleRepository(store: PersonStore): PeopleRepository = PeopleRepository(store)

    @Provides
    fun provideInteractionRepository(
        people: PeopleRepository,
        store: InteractionStore,
    ): InteractionRepository = InteractionRepository(people, store)

    @Provides
    fun provideCommitmentRepository(
        people: PeopleRepository,
        store: CommitmentStore,
    ): CommitmentRepository = CommitmentRepository(people, store)

    @Provides
    fun provideMemoryRepository(
        people: PeopleRepository,
        store: MemoryStore,
    ): MemoryRepository = MemoryRepository(people, store)

    @Provides
    fun provideSubscriptionDao(db: LedgerDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideSubscriptionRepository(dao: SubscriptionDao): SubscriptionRepository =
        SubscriptionRepository(dao)

    @Provides
    fun provideWardrobeItemDao(db: LedgerDatabase): WardrobeItemDao = db.wardrobeItemDao()

    @Provides
    fun provideOutfitDao(db: LedgerDatabase): OutfitDao = db.outfitDao()

    @Provides
    fun provideMediaAssetDao(db: LedgerDatabase): MediaAssetDao = db.mediaAssetDao()

    @Provides
    fun provideWardrobeRepository(
        items: WardrobeItemDao,
        outfits: OutfitDao,
    ): WardrobeRepository = WardrobeRepository(items, outfits)
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS study_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                topic TEXT NOT NULL,
                hoursTotal REAL NOT NULL,
                deadline TEXT,
                sessionLenMinutes INTEGER NOT NULL DEFAULT 50,
                status TEXT NOT NULL DEFAULT 'active',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plan_blocks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                topic TEXT NOT NULL DEFAULT '',
                startAt TEXT NOT NULL,
                endAt TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'SCHEDULED',
                sourcePlanId INTEGER,
                habitId INTEGER,
                actualMinutes INTEGER,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS habits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'CUSTOM',
                daysMask INTEGER NOT NULL DEFAULT 0,
                startTime TEXT NOT NULL,
                durationMinutes INTEGER NOT NULL DEFAULT 30,
                nudgeEnabled INTEGER NOT NULL DEFAULT 1,
                quietOverride INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS habit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                habitId INTEGER NOT NULL,
                date TEXT NOT NULL,
                outcome TEXT NOT NULL,
                minutes INTEGER,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                blockId INTEGER NOT NULL,
                startedAt TEXT NOT NULL,
                endedAt TEXT,
                preset TEXT NOT NULL DEFAULT 'deep'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nudge_proposals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                noteId INTEGER NOT NULL,
                message TEXT NOT NULL,
                suggestedAt TEXT NOT NULL,
                reason TEXT NOT NULL DEFAULT '',
                state TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS spend_speculations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                label TEXT NOT NULL,
                amount REAL NOT NULL,
                direction TEXT NOT NULL,
                expectedDate TEXT NOT NULL,
                confidence TEXT NOT NULL DEFAULT 'MEDIUM',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS spend_guide_days (
                date TEXT NOT NULL PRIMARY KEY,
                guideAmount REAL NOT NULL,
                spent REAL NOT NULL DEFAULT 0,
                buffer REAL NOT NULL DEFAULT 0,
                marginUsed REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS leave_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                refType TEXT NOT NULL,
                refId INTEGER NOT NULL,
                placeLabel TEXT NOT NULL DEFAULT '',
                lat REAL,
                lng REAL,
                travelMinutes INTEGER NOT NULL DEFAULT 15,
                bufferMinutes INTEGER NOT NULL DEFAULT 5,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ts TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                accuracyMeters REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS visits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                placeName TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                arrivedAt TEXT NOT NULL,
                leftAt TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS activity_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startAt TEXT NOT NULL,
                endAt TEXT NOT NULL,
                text TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT 'MANUAL',
                visitId INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS checkin_windows (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startAt TEXT NOT NULL,
                endAt TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT 'PENDING'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS job_applications (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                company TEXT NOT NULL,
                title TEXT NOT NULL,
                url TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'APPLIED',
                appliedOn TEXT NOT NULL,
                followUpOn TEXT,
                notes TEXT NOT NULL DEFAULT '',
                contact TEXT NOT NULL DEFAULT '',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_slots ADD COLUMN recurrenceUntil TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schedule_slot_exceptions (
                slotId INTEGER NOT NULL,
                exceptionDate TEXT NOT NULL,
                PRIMARY KEY(slotId, exceptionDate)
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN location TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN recurrenceFrequency TEXT NOT NULL DEFAULT 'NONE'"
        )
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN recurrenceWeekdays TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN specificDatesJson TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceUntil TEXT")
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN excludedDatesJson TEXT NOT NULL DEFAULT ''"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "transactions", "debts", "goals", "bills", "routines", "alarms", "notes"
        ).forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN location TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("ALTER TABLE tasks ADD COLUMN location TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tasks ADD COLUMN links TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN courseId INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN eventKind TEXT NOT NULL DEFAULT 'TASK'")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS courses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT,
                userId TEXT,
                code TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                defaultLocation TEXT NOT NULL DEFAULT '',
                colorToken TEXT NOT NULL DEFAULT 'emerald',
                notes TEXT NOT NULL DEFAULT '',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schedule_slots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT,
                userId TEXT,
                routineId INTEGER NOT NULL,
                courseId INTEGER,
                title TEXT NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL,
                location TEXT NOT NULL DEFAULT '',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routine_slot_reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT,
                userId TEXT,
                slotId INTEGER NOT NULL,
                label TEXT NOT NULL,
                remindAt TEXT NOT NULL,
                offsetMinutes INTEGER,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calendar_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT,
                userId TEXT,
                title TEXT NOT NULL,
                courseId INTEGER,
                taskId INTEGER,
                startAt TEXT NOT NULL,
                endAt TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'PERSONAL',
                updatedAt INTEGER NOT NULL DEFAULT 0,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
    }
}
