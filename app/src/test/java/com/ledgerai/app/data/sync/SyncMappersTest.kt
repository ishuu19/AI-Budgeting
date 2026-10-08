package com.ledgerai.app.data.sync

import com.ledgerai.app.data.local.room.BudgetEntity
import com.ledgerai.app.data.local.room.CalendarEventEntity
import com.ledgerai.app.data.local.room.EventReminderEntity
import com.ledgerai.app.data.local.room.TransactionEntity
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SyncMappersTest {

    @Test
    fun eventEntity_roundTrip_preservesFields() {
        val entity = CalendarEventEntity(
            id = 3,
            remoteId = "ev-1",
            userId = "user-1",
            title = "Wake",
            notes = "n",
            location = "Home",
            links = "https://x.test",
            startAt = LocalDateTime.of(2026, 10, 8, 6, 30),
            endAt = LocalDateTime.of(2026, 10, 8, 6, 30),
            kind = CalendarEventKind.ALARM,
            isEnabled = true,
            alarmToneUri = "content://tone",
            alarmRepeatDays = 62,
            recurrenceFrequency = RecurrenceFrequency.WEEKLY,
            recurrenceWeekdays = "1,2,3,4,5",
            recurrenceUntil = LocalDate.of(2027, 1, 1),
            excludedDatesJson = "2026-10-12",
            updatedAt = 1_700_000_000_000L,
        )
        val back = entity.toRemoteDto(remoteId = "ev-1", userId = "user-1").toEntity(localId = 3)
        assertEquals(entity, back)
    }

    @Test
    fun eventDto_unknownKindFallsBackToEvent() {
        val dto = RemoteEventDto(id = "x", kind = "SOMETHING", startAt = "2026-10-08T09:00:00")
        assertEquals(CalendarEventKind.EVENT, dto.toEntity().kind)
    }

    @Test
    fun eventDto_endBeforeStartIsClamped() {
        val dto = RemoteEventDto(id = "x", startAt = "2026-10-08T09:00:00", endAt = "2026-10-08T08:00:00")
        val entity = dto.toEntity()
        assertEquals(entity.startAt, entity.endAt)
    }

    @Test
    fun reminderEntity_roundTrip_keepsOffsetAndAbsoluteTime() {
        val offset = EventReminderEntity(id = 1, remoteId = "r1", eventId = 4, label = "10 min before", offsetMinutes = 10)
        assertEquals(offset, offset.toRemoteDto("r1", "u", "ev").toEntity(localId = 1, localEventId = 4).copy(userId = null))
        val absolute = EventReminderEntity(
            id = 2, remoteId = "r2", eventId = 4, label = "x",
            remindAt = LocalDateTime.of(2026, 10, 8, 9, 0)
        )
        assertEquals(absolute, absolute.toRemoteDto("r2", "u", "ev").toEntity(localId = 2, localEventId = 4).copy(userId = null))
    }

    @Test
    fun transactionEntity_roundTrip_preservesCoreFields() {
        val created = LocalDateTime.of(2026, 10, 8, 10, 0, 0)
        val entity = TransactionEntity(
            id = 9,
            remoteId = "tx-1",
            userId = "user-1",
            amount = 12.5,
            type = TransactionType.EXPENSE,
            category = TransactionCategory.FOOD,
            merchant = "Cafe",
            note = "coffee",
            date = LocalDate.of(2026, 10, 8),
            createdAt = created,
            isRecurring = false,
            currency = "USD",
            updatedAt = 1_700_000_100_000L,
            deletedAt = null,
        )
        val dto = entity.toRemoteDto(remoteId = "tx-1", userId = "user-1")
        val back = dto.toEntity(localId = 9)

        assertEquals(entity.amount, back.amount, 0.0)
        assertEquals(entity.type, back.type)
        assertEquals(entity.category, back.category)
        assertEquals(entity.merchant, back.merchant)
        assertEquals(entity.note, back.note)
        assertEquals(entity.date, back.date)
        assertEquals(entity.createdAt, back.createdAt)
        assertEquals(entity.currency, back.currency)
        assertEquals(entity.updatedAt, back.updatedAt)
    }

    @Test
    fun budgetEntity_roundTrip_preservesNumbers() {
        val entity = BudgetEntity(
            id = 2,
            remoteId = "b-1",
            userId = "user-1",
            category = TransactionCategory.FOOD,
            monthlyLimit = 400.0,
            spent = 120.0,
            month = 10,
            year = 2026,
            alertThreshold = 80,
            updatedAt = 1_700_000_200_000L,
        )
        val back = entity.toRemoteDto("b-1", "user-1").toEntity(localId = 2)
        assertEquals(entity.monthlyLimit, back.monthlyLimit, 0.0)
        assertEquals(entity.spent, back.spent, 0.0)
        assertEquals(entity.month, back.month)
        assertEquals(entity.year, back.year)
        assertEquals(entity.category, back.category)
        assertEquals(entity.updatedAt, back.updatedAt)
    }

    @Test
    fun remoteWins_matchesMapperUpdatedAt() {
        val entity = CalendarEventEntity(
            title = "x",
            startAt = LocalDateTime.of(2026, 10, 8, 12, 0),
            endAt = LocalDateTime.of(2026, 10, 8, 12, 0),
            updatedAt = 1_000L,
        )
        val dto = entity.toRemoteDto("a", "u")
        assertTrueRemoteWins(dto.updatedAt, entity.updatedAt)
        assertFalseRemoteWins(SyncTime.millisToIso(500L), entity.updatedAt)
    }

    private fun assertTrueRemoteWins(remoteIso: String?, local: Long) {
        org.junit.Assert.assertTrue(SyncTime.remoteWins(remoteIso, local))
    }

    private fun assertFalseRemoteWins(remoteIso: String?, local: Long) {
        org.junit.Assert.assertFalse(SyncTime.remoteWins(remoteIso, local))
    }
}
