package com.ledgerai.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ledgerai.app.data.local.room.AlarmDao
import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.LedgerDatabase
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.RoutineDao
import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.data.local.room.TaskReminderDao
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
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
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
    fun provideTaskDao(db: LedgerDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideTaskReminderDao(db: LedgerDatabase): TaskReminderDao = db.taskReminderDao()

    @Provides
    fun provideRoutineDao(db: LedgerDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideNoteDao(db: LedgerDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideAlarmDao(db: LedgerDatabase): AlarmDao = db.alarmDao()
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
