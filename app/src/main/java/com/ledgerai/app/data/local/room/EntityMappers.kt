package com.ledgerai.app.data.local.room

import com.ledgerai.app.domain.model.AlarmItem
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.RoutineItem
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import com.ledgerai.app.domain.model.Transaction

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    remoteId = remoteId,
    userId = userId,
    amount = amount,
    type = type,
    category = category,
    merchant = merchant,
    note = note,
    location = location,
    date = date,
    createdAt = createdAt,
    isRecurring = isRecurring,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    amount = amount,
    type = type,
    category = category,
    merchant = merchant,
    note = note,
    location = location,
    date = date,
    createdAt = createdAt,
    isRecurring = isRecurring,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    remoteId = remoteId,
    userId = userId,
    category = category,
    monthlyLimit = monthlyLimit,
    spent = spent,
    month = month,
    year = year,
    alertThreshold = alertThreshold,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Budget.toEntity() = BudgetEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    category = category,
    monthlyLimit = monthlyLimit,
    spent = spent,
    month = month,
    year = year,
    alertThreshold = alertThreshold,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun DebtEntity.toDomain() = Debt(
    id = id,
    remoteId = remoteId,
    userId = userId,
    friendName = friendName,
    amount = amount,
    direction = direction,
    dateLent = dateLent,
    dueDate = dueDate,
    phone = phone,
    email = email,
    note = note,
    location = location,
    isPaid = isPaid,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Debt.toEntity() = DebtEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    friendName = friendName,
    amount = amount,
    direction = direction,
    dateLent = dateLent,
    dueDate = dueDate,
    phone = phone,
    email = email,
    note = note,
    location = location,
    isPaid = isPaid,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun GoalEntity.toDomain() = Goal(
    id = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    targetAmount = targetAmount,
    savedAmount = savedAmount,
    targetDate = targetDate,
    emoji = emoji,
    note = note,
    location = location,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    targetAmount = targetAmount,
    savedAmount = savedAmount,
    targetDate = targetDate,
    emoji = emoji,
    note = note,
    location = location,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun BillEntity.toDomain() = Bill(
    id = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    amount = amount,
    frequency = frequency,
    nextDueDate = nextDueDate,
    category = category,
    note = note,
    location = location,
    isActive = isActive,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun Bill.toEntity() = BillEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    name = name,
    amount = amount,
    frequency = frequency,
    nextDueDate = nextDueDate,
    category = category,
    note = note,
    location = location,
    isActive = isActive,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun TaskEntity.toDomain(reminders: List<TaskReminder> = emptyList()) = TaskItem(
    id = id,
    remoteId = remoteId,
    title = title,
    notes = notes,
    location = location,
    links = links,
    dueAt = dueAt,
    isCompleted = isCompleted,
    reminders = reminders,
    createdAt = createdAt
)

fun TaskWithReminders.toDomain() = task.toDomain(
    reminders = reminders
        .filter { it.deletedAt == null }
        .sortedBy { it.remindAt }
        .map { it.toDomain() }
)

fun TaskItem.toEntity(
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
) = TaskEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    title = title,
    notes = notes,
    location = location,
    links = links,
    dueAt = dueAt,
    isCompleted = isCompleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun TaskReminderEntity.toDomain() = TaskReminder(
    id = id,
    label = label,
    remindAt = remindAt,
    offsetMinutes = offsetMinutes,
    isEnabled = isEnabled
)

fun TaskReminder.toEntity(
    taskId: Long,
    userId: String? = null,
    offsetMinutes: Int? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
) = TaskReminderEntity(
    id = id,
    remoteId = null,
    userId = userId,
    taskId = taskId,
    label = label,
    remindAt = remindAt,
    offsetMinutes = offsetMinutes ?: this.offsetMinutes,
    isEnabled = isEnabled,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun RoutineEntity.toDomain() = RoutineItem(
    id = id,
    remoteId = remoteId,
    title = title,
    notes = notes,
    location = location,
    repeatRule = repeatRule,
    isActive = isActive
)

fun RoutineItem.toEntity(
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
) = RoutineEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    title = title,
    notes = notes,
    location = location,
    repeatRule = repeatRule,
    isActive = isActive,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun NoteEntity.toDomain() = NoteItem(
    id = id,
    remoteId = remoteId,
    title = title,
    body = body,
    location = location,
    tags = tags.toTagList(),
    updatedAt = editedAt,
    createdAt = createdAt
)

fun NoteItem.toEntity(
    userId: String? = null,
    deletedAt: Long? = null,
    updatedAt: Long = System.currentTimeMillis()
) = NoteEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    title = title,
    body = body,
    location = location,
    tags = tags.toTagsStorage(),
    createdAt = createdAt,
    editedAt = this.updatedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun AlarmEntity.toDomain() = AlarmItem(
    id = id,
    remoteId = remoteId,
    label = label,
    time = time,
    isEnabled = isEnabled,
    repeatDays = repeatDays,
    toneUri = toneUri,
    location = location
)

fun AlarmItem.toEntity(
    userId: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null
) = AlarmEntity(
    id = id,
    remoteId = remoteId,
    userId = userId,
    label = label,
    time = time,
    isEnabled = isEnabled,
    repeatDays = repeatDays,
    toneUri = toneUri,
    location = location,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

private fun String.toTagList(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun List<String>.toTagsStorage(): String =
    joinToString(",") { it.trim() }.trim(',')
