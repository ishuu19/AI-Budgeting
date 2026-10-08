package com.ledgerai.app.data.sync

import com.ledgerai.app.data.local.room.AlarmEntity
import com.ledgerai.app.data.local.room.BillEntity
import com.ledgerai.app.data.local.room.BudgetEntity
import com.ledgerai.app.data.local.room.DebtEntity
import com.ledgerai.app.data.local.room.GoalEntity
import com.ledgerai.app.data.local.room.NoteEntity
import com.ledgerai.app.data.local.room.RoutineEntity
import com.ledgerai.app.data.local.room.TaskEntity
import com.ledgerai.app.data.local.room.TaskReminderEntity
import com.ledgerai.app.data.local.room.TransactionEntity
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime

internal fun TransactionEntity.toRemoteDto(remoteId: String, userId: String): RemoteTransactionDto =
    RemoteTransactionDto(
        id = remoteId,
        userId = userId,
        amount = amount,
        type = type.name,
        category = category.name,
        merchant = merchant,
        note = note,
        date = SyncTime.dateToString(date),
        createdAt = SyncTime.dateTimeToIso(createdAt),
        isRecurring = isRecurring,
        currency = currency,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteTransactionDto.toEntity(localId: Long = 0): TransactionEntity =
    TransactionEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        amount = amount,
        type = enumOr(type, TransactionType.EXPENSE),
        category = enumOr(category, TransactionCategory.OTHER),
        merchant = merchant,
        note = note,
        date = SyncTime.stringToDate(date),
        createdAt = SyncTime.isoToDateTime(createdAt) ?: LocalDateTime.now(),
        isRecurring = isRecurring,
        currency = currency,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun BudgetEntity.toRemoteDto(remoteId: String, userId: String): RemoteBudgetDto =
    RemoteBudgetDto(
        id = remoteId,
        userId = userId,
        category = category.name,
        monthlyLimit = monthlyLimit,
        spent = spent,
        month = month,
        year = year,
        alertThreshold = alertThreshold,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteBudgetDto.toEntity(localId: Long = 0): BudgetEntity =
    BudgetEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        category = enumOr(category, TransactionCategory.OTHER),
        monthlyLimit = monthlyLimit,
        spent = spent,
        month = month,
        year = year,
        alertThreshold = alertThreshold,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun DebtEntity.toRemoteDto(remoteId: String, userId: String): RemoteDebtDto =
    RemoteDebtDto(
        id = remoteId,
        userId = userId,
        friendName = friendName,
        amount = amount,
        direction = direction.name,
        dateLent = SyncTime.dateToString(dateLent),
        dueDate = dueDate?.let(SyncTime::dateToString),
        phone = phone,
        email = email,
        note = note,
        isPaid = isPaid,
        currency = currency,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteDebtDto.toEntity(localId: Long = 0): DebtEntity =
    DebtEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        friendName = friendName,
        amount = amount,
        direction = enumOr(direction, DebtDirection.I_OWE),
        dateLent = SyncTime.stringToDate(dateLent),
        dueDate = dueDate?.let { SyncTime.stringToDate(it).takeIf { d -> d != LocalDate.EPOCH } },
        phone = phone,
        email = email,
        note = note,
        isPaid = isPaid,
        currency = currency,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun GoalEntity.toRemoteDto(remoteId: String, userId: String): RemoteGoalDto =
    RemoteGoalDto(
        id = remoteId,
        userId = userId,
        name = name,
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        targetDate = targetDate?.let(SyncTime::dateToString),
        emoji = emoji,
        note = note,
        isCompleted = isCompleted,
        createdAt = SyncTime.dateToString(createdAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteGoalDto.toEntity(localId: Long = 0): GoalEntity =
    GoalEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        name = name,
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        targetDate = targetDate?.let { SyncTime.stringToDate(it).takeIf { d -> d != LocalDate.EPOCH } },
        emoji = emoji,
        note = note,
        isCompleted = isCompleted,
        createdAt = SyncTime.stringToDate(createdAt),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun BillEntity.toRemoteDto(remoteId: String, userId: String): RemoteBillDto =
    RemoteBillDto(
        id = remoteId,
        userId = userId,
        name = name,
        amount = amount,
        frequency = frequency.name,
        nextDueDate = SyncTime.dateToString(nextDueDate),
        category = category.name,
        note = note,
        isActive = isActive,
        currency = currency,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteBillDto.toEntity(localId: Long = 0): BillEntity =
    BillEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        name = name,
        amount = amount,
        frequency = enumOr(frequency, BillFrequency.MONTHLY),
        nextDueDate = SyncTime.stringToDate(nextDueDate),
        category = enumOr(category, TransactionCategory.SUBSCRIPTIONS),
        note = note,
        isActive = isActive,
        currency = currency,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun TaskEntity.toRemoteDto(remoteId: String, userId: String): RemoteTaskDto =
    RemoteTaskDto(
        id = remoteId,
        userId = userId,
        title = title,
        notes = notes,
        dueAt = dueAt?.let(SyncTime::dateTimeToIso),
        isCompleted = isCompleted,
        createdAt = SyncTime.dateTimeToIso(createdAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteTaskDto.toEntity(localId: Long = 0): TaskEntity =
    TaskEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        title = title,
        notes = notes,
        dueAt = SyncTime.isoToDateTime(dueAt),
        isCompleted = isCompleted,
        createdAt = SyncTime.isoToDateTime(createdAt) ?: LocalDateTime.now(),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun TaskReminderEntity.toRemoteDto(
    remoteId: String,
    userId: String,
    remoteTaskId: String
): RemoteReminderDto =
    RemoteReminderDto(
        id = remoteId,
        userId = userId,
        taskId = remoteTaskId,
        label = label,
        remindAt = SyncTime.dateTimeToIso(remindAt),
        offsetMinutes = offsetMinutes,
        isEnabled = isEnabled,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteReminderDto.toEntity(localId: Long = 0, localTaskId: Long): TaskReminderEntity =
    TaskReminderEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        taskId = localTaskId,
        label = label,
        remindAt = SyncTime.isoToDateTime(remindAt) ?: LocalDateTime.now(),
        offsetMinutes = offsetMinutes,
        isEnabled = isEnabled,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun AlarmEntity.toRemoteDto(remoteId: String, userId: String): RemoteAlarmDto =
    RemoteAlarmDto(
        id = remoteId,
        userId = userId,
        label = label,
        time = SyncTime.timeToString(time),
        isEnabled = isEnabled,
        repeatDays = repeatDays,
        toneUri = toneUri,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteAlarmDto.toEntity(localId: Long = 0): AlarmEntity =
    AlarmEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        label = label,
        time = SyncTime.stringToTime(time),
        isEnabled = isEnabled,
        repeatDays = repeatDays,
        toneUri = toneUri,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun NoteEntity.toRemoteDto(remoteId: String, userId: String): RemoteNoteDto =
    RemoteNoteDto(
        id = remoteId,
        userId = userId,
        title = title,
        body = body,
        tags = tags,
        createdAt = SyncTime.dateTimeToIso(createdAt),
        editedAt = SyncTime.dateTimeToIso(editedAt),
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteNoteDto.toEntity(localId: Long = 0): NoteEntity =
    NoteEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        title = title,
        body = body,
        tags = tags,
        createdAt = SyncTime.isoToDateTime(createdAt) ?: LocalDateTime.now(),
        editedAt = SyncTime.isoToDateTime(editedAt) ?: LocalDateTime.now(),
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

internal fun RoutineEntity.toRemoteDto(remoteId: String, userId: String): RemoteRoutineDto =
    RemoteRoutineDto(
        id = remoteId,
        userId = userId,
        title = title,
        notes = notes,
        repeatRule = repeatRule,
        isActive = isActive,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteRoutineDto.toEntity(localId: Long = 0): RoutineEntity =
    RoutineEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        title = title,
        notes = notes,
        repeatRule = repeatRule,
        isActive = isActive,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
    if (raw.isNullOrBlank()) return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}
