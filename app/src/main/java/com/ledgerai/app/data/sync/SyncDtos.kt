package com.ledgerai.app.data.sync

import com.google.gson.annotations.SerializedName

data class RemoteTransactionDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("type") val type: String = "EXPENSE",
    @SerializedName("category") val category: String = "OTHER",
    @SerializedName("merchant") val merchant: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("is_recurring") val isRecurring: Boolean = false,
    @SerializedName("currency") val currency: String = "USD",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteBudgetDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("category") val category: String = "OTHER",
    @SerializedName("monthly_limit") val monthlyLimit: Double = 0.0,
    @SerializedName("spent") val spent: Double = 0.0,
    @SerializedName("month") val month: Int = 1,
    @SerializedName("year") val year: Int = 1970,
    @SerializedName("alert_threshold") val alertThreshold: Int = 80,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteDebtDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("friend_name") val friendName: String = "",
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("direction") val direction: String = "I_OWE",
    @SerializedName("date_lent") val dateLent: String = "",
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("phone") val phone: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("note") val note: String = "",
    @SerializedName("is_paid") val isPaid: Boolean = false,
    @SerializedName("currency") val currency: String = "USD",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteGoalDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("name") val name: String = "",
    @SerializedName("target_amount") val targetAmount: Double = 0.0,
    @SerializedName("saved_amount") val savedAmount: Double = 0.0,
    @SerializedName("target_date") val targetDate: String? = null,
    @SerializedName("emoji") val emoji: String = "🎯",
    @SerializedName("note") val note: String = "",
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteBillDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("name") val name: String = "",
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("frequency") val frequency: String = "MONTHLY",
    @SerializedName("next_due_date") val nextDueDate: String = "",
    @SerializedName("category") val category: String = "SUBSCRIPTIONS",
    @SerializedName("note") val note: String = "",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("currency") val currency: String = "USD",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteTaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteReminderDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("task_id") val taskId: String,
    @SerializedName("label") val label: String = "",
    @SerializedName("remind_at") val remindAt: String = "",
    @SerializedName("offset_minutes") val offsetMinutes: Int? = null,
    @SerializedName("is_enabled") val isEnabled: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteAlarmDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("label") val label: String = "Alarm",
    @SerializedName("time") val time: String = "00:00:00",
    @SerializedName("is_enabled") val isEnabled: Boolean = true,
    @SerializedName("repeat_days") val repeatDays: Int = 0,
    @SerializedName("tone_uri") val toneUri: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteNoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("tags") val tags: String = "",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("edited_at") val editedAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteRoutineDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("repeat_rule") val repeatRule: String = "",
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

/** Cloud row for a quote the user has already been shown (see migration 003). */
data class RemoteQuotesSeenDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("quote_hash") val quoteHash: String,
    @SerializedName("quote_text") val quoteText: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("seen_on") val seenOn: String = "",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)
