package com.ledgerai.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

// ─── Transaction ────────────────────────────────────────────────────────────

enum class TransactionType { INCOME, EXPENSE }

enum class TransactionCategory(val displayName: String, val colorHex: String) {
    FOOD("Food", "#F97316"),
    TRANSPORT("Transport", "#3B82F6"),
    ENTERTAINMENT("Entertainment", "#A855F7"),
    SHOPPING("Shopping", "#EC4899"),
    HEALTH("Health", "#22C55E"),
    RENT("Rent / Housing", "#EF4444"),
    UTILITIES("Utilities", "#F59E0B"),
    SUBSCRIPTIONS("Subscriptions", "#6366F1"),
    EDUCATION("Education", "#14B8A6"),
    SALARY("Salary", "#22C55E"),
    FREELANCE("Freelance", "#84CC16"),
    OTHER("Other", "#9CA3AF");

    companion object {
        fun fromDisplayName(name: String): TransactionCategory =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: OTHER
    }
}

data class Transaction(
    val id: Long = 0,
    /** Server id after Supabase sync (Phase 2); null while local-only. */
    val remoteId: String? = null,
    val userId: String? = null,
    val amount: Double,
    val type: TransactionType,
    val category: TransactionCategory,
    val merchant: String = "",
    val note: String = "",
    val location: String = "",
    val date: LocalDate = LocalDate.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isRecurring: Boolean = false,
    val currency: String = "USD",
    /** Epoch millis; set by repository on write. */
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
)

// ─── Budget ──────────────────────────────────────────────────────────────────

data class Budget(
    val id: Long = 0,
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
) {
    val remaining: Double get() = monthlyLimit - spent
    val usagePercent: Int get() = if (monthlyLimit > 0) ((spent / monthlyLimit) * 100).toInt() else 0
    val isOverBudget: Boolean get() = spent > monthlyLimit
    val isNearLimit: Boolean get() = usagePercent >= alertThreshold
}

// ─── Debt ────────────────────────────────────────────────────────────────────

enum class DebtDirection { I_OWE, THEY_OWE }

data class Debt(
    val id: Long = 0,
    val remoteId: String? = null,
    val userId: String? = null,
    val friendName: String,
    val amount: Double,
    val direction: DebtDirection,
    val dateLent: LocalDate = LocalDate.now(),
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

// ─── Goal ────────────────────────────────────────────────────────────────────

data class Goal(
    val id: Long = 0,
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
    val createdAt: LocalDate = LocalDate.now(),
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
) {
    val progressPercent: Int get() =
        if (targetAmount > 0) ((savedAmount / targetAmount) * 100).toInt().coerceIn(0, 100) else 0
    val remaining: Double get() = (targetAmount - savedAmount).coerceAtLeast(0.0)
}

// ─── Bill ────────────────────────────────────────────────────────────────────

enum class BillFrequency(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

data class Bill(
    val id: Long = 0,
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

// ─── AI / Chat ────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ParsedTransaction(
    val amount: Double?,
    val category: TransactionCategory,
    val merchant: String,
    val date: LocalDate,
    val note: String,
    val location: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val confidence: Float = 1.0f
)

data class FinancialForecast(
    val month: String,
    val predictedSpend: Double,
    val recommendedBudget: Double,
    val riskLevel: RiskLevel,
    val insight: String
)

enum class RiskLevel(val label: String, val colorHex: String) {
    LOW("Low Risk", "#22C55E"),
    MEDIUM("Medium Risk", "#F59E0B"),
    HIGH("High Risk", "#EF4444")
}

data class FinancialHealthScore(
    val score: Int,
    val savingsRate: Int,
    val debtRatio: Int,
    val budgetAdherence: Int,
    val spendingVolatility: Int,
    val summary: String
) {
    val grade: String get() = when {
        score >= 80 -> "Excellent"
        score >= 65 -> "Good"
        score >= 50 -> "Fair"
        else -> "Needs Work"
    }
}
