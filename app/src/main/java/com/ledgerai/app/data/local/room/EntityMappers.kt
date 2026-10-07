package com.ledgerai.app.data.local.room

import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.Goal
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
    isActive = isActive,
    currency = currency,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
