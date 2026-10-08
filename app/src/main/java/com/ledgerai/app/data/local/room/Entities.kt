package com.ledgerai.app.data.local.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime

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

@Entity(
    tableName = "calendar_events",
    indices = [
        Index("startAt"),
        Index("kind"),
        Index("remoteId"),
        Index("updatedAt")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val title: String,
    val notes: String = "",
    val location: String = "",
    val links: String = "",
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean = false,
    /** False only for undated tasks; [startAt] then holds the creation day for ordering. */
    val hasDate: Boolean = true,
    val kind: CalendarEventKind = CalendarEventKind.EVENT,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    /** Alarm on/off, routine or class active flag. */
    val isEnabled: Boolean = true,
    val alarmToneUri: String? = null,
    /** Bitmask Sun=1 ... Sat=64; 0 = one-shot. Only for ALARM events. */
    val alarmRepeatDays: Int = 0,
    val recurrenceFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: String = "",
    val specificDatesJson: String = "",
    val recurrenceUntil: LocalDate? = null,
    val excludedDatesJson: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

/**
 * Reminder attached to one calendar event. Max [com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT]
 * active rows per event (enforced in the repository and on sync pull).
 * [offsetMinutes] is relative to each occurrence start; when null, [remindAt] is a one-off absolute time.
 */
@Entity(
    tableName = "event_reminders",
    indices = [Index("eventId"), Index("remoteId")]
)
data class EventReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val eventId: Long,
    val label: String,
    val offsetMinutes: Int? = null,
    val remindAt: LocalDateTime? = null,
    val isEnabled: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

data class EventWithReminders(
    @Embedded val event: CalendarEventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "eventId",
        entity = EventReminderEntity::class
    )
    val reminders: List<EventReminderEntity> = emptyList()
)
