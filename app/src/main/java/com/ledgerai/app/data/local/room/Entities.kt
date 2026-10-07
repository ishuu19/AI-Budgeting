package com.ledgerai.app.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ledgerai.app.domain.model.BillFrequency
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
    val isActive: Boolean = true,
    val currency: String = "USD",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)
