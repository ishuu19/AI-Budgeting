package com.ledgerai.app.data.sync

import com.ledgerai.app.data.local.room.BillEntity
import com.ledgerai.app.data.local.room.BudgetEntity
import com.ledgerai.app.data.local.room.DebtEntity
import com.ledgerai.app.data.local.room.GoalEntity
import com.ledgerai.app.data.local.room.NoteEntity
import com.ledgerai.app.data.local.room.CalendarEventEntity
import com.ledgerai.app.data.local.room.EventReminderEntity
import com.ledgerai.app.data.local.room.TransactionEntity
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.RecurrenceFrequency
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

internal fun CalendarEventEntity.toRemoteDto(remoteId: String, userId: String): RemoteEventDto =
    RemoteEventDto(
        id = remoteId,
        userId = userId,
        title = title,
        notes = notes,
        location = location,
        links = links,
        startAt = SyncTime.floatingToString(startAt),
        endAt = SyncTime.floatingToString(endAt),
        allDay = allDay,
        hasDate = hasDate,
        kind = kind.name,
        isCompleted = isCompleted,
        completedAt = completedAt?.let(SyncTime::floatingToString),
        isEnabled = isEnabled,
        alarmToneUri = alarmToneUri,
        alarmRepeatDays = alarmRepeatDays,
        recurrenceFrequency = recurrenceFrequency.name,
        recurrenceInterval = recurrenceInterval,
        recurrenceWeekdays = recurrenceWeekdays,
        specificDates = specificDatesJson,
        recurrenceUntil = recurrenceUntil?.let(SyncTime::dateToString),
        excludedDates = excludedDatesJson,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteEventDto.toEntity(localId: Long = 0): CalendarEventEntity {
    val start = SyncTime.isoToDateTime(startAt) ?: LocalDateTime.now()
    return CalendarEventEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        title = title,
        notes = notes,
        location = location,
        links = links,
        startAt = start,
        endAt = SyncTime.isoToDateTime(endAt)?.takeIf { !it.isBefore(start) } ?: start,
        allDay = allDay,
        hasDate = hasDate,
        kind = CalendarEventKind.parse(kind),
        isCompleted = isCompleted,
        completedAt = SyncTime.isoToDateTime(completedAt),
        isEnabled = isEnabled,
        alarmToneUri = alarmToneUri,
        alarmRepeatDays = alarmRepeatDays,
        recurrenceFrequency = enumOr(recurrenceFrequency, RecurrenceFrequency.NONE),
        recurrenceInterval = recurrenceInterval.coerceAtLeast(1),
        recurrenceWeekdays = recurrenceWeekdays,
        specificDatesJson = specificDates,
        recurrenceUntil = SyncTime.optionalDate(recurrenceUntil),
        excludedDatesJson = excludedDates,
        updatedAt = SyncTime.isoToMillis(updatedAt),
        deletedAt = deletedAt?.let { SyncTime.isoToMillis(it).takeIf { ms -> ms > 0 } }
    )
}

internal fun EventReminderEntity.toRemoteDto(
    remoteId: String,
    userId: String,
    remoteEventId: String
): RemoteEventReminderDto =
    RemoteEventReminderDto(
        id = remoteId,
        userId = userId,
        eventId = remoteEventId,
        label = label,
        offsetMinutes = offsetMinutes,
        remindAt = remindAt?.let(SyncTime::floatingToString),
        isEnabled = isEnabled,
        updatedAt = SyncTime.millisToIso(updatedAt),
        deletedAt = deletedAt?.let(SyncTime::millisToIso)
    )

internal fun RemoteEventReminderDto.toEntity(localId: Long = 0, localEventId: Long): EventReminderEntity =
    EventReminderEntity(
        id = localId,
        remoteId = id,
        userId = userId,
        eventId = localEventId,
        label = label,
        offsetMinutes = offsetMinutes,
        remindAt = SyncTime.isoToDateTime(remindAt),
        isEnabled = isEnabled,
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

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
    if (raw.isNullOrBlank()) return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}
