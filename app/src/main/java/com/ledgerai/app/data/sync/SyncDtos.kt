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

/** One row per calendar event of any kind (event, task, exam, class, routine, alarm). */
data class RemoteEventDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("links") val links: String = "",
    @SerializedName("start_at") val startAt: String = "",
    @SerializedName("end_at") val endAt: String = "",
    @SerializedName("all_day") val allDay: Boolean = false,
    @SerializedName("has_date") val hasDate: Boolean = true,
    @SerializedName("kind") val kind: String = "EVENT",
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("is_enabled") val isEnabled: Boolean = true,
    @SerializedName("alarm_tone_uri") val alarmToneUri: String? = null,
    @SerializedName("alarm_repeat_days") val alarmRepeatDays: Int = 0,
    @SerializedName("recurrence_frequency") val recurrenceFrequency: String = "NONE",
    @SerializedName("recurrence_interval") val recurrenceInterval: Int = 1,
    @SerializedName("recurrence_weekdays") val recurrenceWeekdays: String = "",
    @SerializedName("specific_dates") val specificDates: String = "",
    @SerializedName("recurrence_until") val recurrenceUntil: String? = null,
    @SerializedName("excluded_dates") val excludedDates: String = "",
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class RemoteEventReminderDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("event_id") val eventId: String,
    @SerializedName("label") val label: String = "",
    @SerializedName("offset_minutes") val offsetMinutes: Int? = null,
    @SerializedName("remind_at") val remindAt: String? = null,
    @SerializedName("is_enabled") val isEnabled: Boolean = true,
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
