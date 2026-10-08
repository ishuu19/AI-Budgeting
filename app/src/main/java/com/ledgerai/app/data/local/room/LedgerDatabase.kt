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
        NoteEntity::class
    ],
    version = 3,
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
}
