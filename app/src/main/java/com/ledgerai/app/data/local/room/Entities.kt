package com.ledgerai.app.data.local.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val amount: Double,
    val type: TransactionType,
    val category: TransactionCategory,
    val merchant: String = "",
    val note: String = "",
    val location: String = "",
    val date: LocalDate,
    val createdAt: LocalDateTime,
    val isRecurring: Boolean = false,
    val currency: String = "USD",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val category: TransactionCategory,
    val monthlyLimit: Double,
    val spent: Double = 0.0,
    val month: Int,
    val year: Int,
    val alertThreshold: Int = 80,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val friendName: String,
    val amount: Double,
    val direction: DebtDirection,
    val dateLent: LocalDate,
    val dueDate: LocalDate? = null,
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val location: String = "",
    val isPaid: Boolean = false,
    val currency: String = "USD",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: LocalDate? = null,
    val emoji: String = "🎯",
    val note: String = "",
    val location: String = "",
    val isCompleted: Boolean = false,
    val createdAt: LocalDate,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val name: String,
    val amount: Double,
    val frequency: BillFrequency = BillFrequency.MONTHLY,
    val nextDueDate: LocalDate,
    val category: TransactionCategory = TransactionCategory.SUBSCRIPTIONS,
    val note: String = "",
    val location: String = "",
    val isActive: Boolean = true,
    val currency: String = "USD",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    val links: String = "",
    val dueAt: LocalDateTime? = null,
    val isCompleted: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

/**
 * Custom reminders attached to a task.
 * Hard limit: at most [com.ledgerai.app.domain.model.MAX_REMINDERS_PER_TASK] active rows per task
 * (enforced in app + Supabase trigger; Room has [TaskReminderDao.countActiveForTask]).
 */
@Entity(tableName = "task_reminders")
data class TaskReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val taskId: Long,
    val label: String,
    val remindAt: LocalDateTime,
    /** Optional minutes-before-due offset; null when [remindAt] is absolute-only. */
    val offsetMinutes: Int? = null,
    val isEnabled: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

/** Repeat routines (simple rule token or custom string). */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    /** Opaque repeat rule (e.g. RRULE or app-defined token). */
    val repeatRule: String = "",
    val isActive: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val label: String = "Alarm",
    val time: LocalTime,
    val isEnabled: Boolean = true,
    /** Bitmask Sun=1 … Sat=64; 0 = one-shot today/tomorrow. */
    val repeatDays: Int = 0,
    val toneUri: String? = null,
    val location: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val title: String,
    val body: String = "",
    val location: String = "",
    /** Comma-separated tags (domain [List] mapped in repository). */
    val tags: String = "",
    val createdAt: LocalDateTime,
    val editedAt: LocalDateTime,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

/** Task row with related reminder rows (filter soft-deletes in mappers/repos). */
data class TaskWithReminders(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
        entity = TaskReminderEntity::class
    )
    val reminders: List<TaskReminderEntity> = emptyList()
)
