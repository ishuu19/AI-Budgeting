package com.ledgerai.app.data.sync

import com.ledgerai.app.data.local.room.AlarmEntity
import com.ledgerai.app.data.local.room.BudgetEntity
import com.ledgerai.app.data.local.room.TransactionEntity
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SyncMappersTest {

    @Test
    fun alarmEntity_roundTrip_preservesFields() {
        val entity = AlarmEntity(
            id = 3,
            remoteId = "alarm-1",
            userId = "user-1",
            label = "Wake",
            time = LocalTime.of(6, 30, 0),
            isEnabled = true,
            repeatDays = 62, // weekdays
            toneUri = "content://tone",
            updatedAt = 1_700_000_000_000L,
            deletedAt = null,
        )
        val dto = entity.toRemoteDto(remoteId = "alarm-1", userId = "user-1")
        val back = dto.toEntity(localId = 3)

        assertEquals(entity.id, back.id)
        assertEquals(entity.remoteId, back.remoteId)
        assertEquals(entity.userId, back.userId)
        assertEquals(entity.label, back.label)
        assertEquals(entity.time, back.time)
        assertEquals(entity.isEnabled, back.isEnabled)
        assertEquals(entity.repeatDays, back.repeatDays)
        assertEquals(entity.toneUri, back.toneUri)
        assertEquals(entity.updatedAt, back.updatedAt)
        assertNull(back.deletedAt)
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
        val entity = AlarmEntity(
            label = "x",
            time = LocalTime.NOON,
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
