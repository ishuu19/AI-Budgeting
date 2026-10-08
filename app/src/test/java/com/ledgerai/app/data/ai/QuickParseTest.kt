package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickParseTest {

    @Test
    fun parsesFoodExpenseWithAmount() {
        val parsed = QuickParse.parse("Coffee 4.50 at Starbucks", today = LocalDate.of(2026, 10, 8))
        assertEquals(4.50, parsed.amount!!, 0.001)
        assertEquals(TransactionCategory.FOOD, parsed.category)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(LocalDate.of(2026, 10, 8), parsed.date)
        assertEquals("Starbucks", parsed.merchant)
        assertEquals("", parsed.note)
    }

    @Test
    fun parsesSalaryAsIncome() {
        val parsed = QuickParse.parse("Got paid salary 3200")
        assertEquals(3200.0, parsed.amount!!, 0.001)
        assertEquals(TransactionCategory.SALARY, parsed.category)
        assertEquals(TransactionType.INCOME, parsed.type)
    }

    @Test
    fun fallsBackToOtherWhenUnknown() {
        val parsed = QuickParse.parse("misc thing 9")
        assertEquals(9.0, parsed.amount!!, 0.001)
        assertEquals(TransactionCategory.OTHER, parsed.category)
    }

    @Test
    fun parseRepeatDaysDefaultsToZero() {
        assertEquals(0, QuickParse.parseRepeatDays("Alarm at 7am"))
    }

    @Test
    fun parseRepeatDaysWeekdaysAndEveryDay() {
        assertEquals(QuickParse.MASK_WEEKDAYS, QuickParse.parseRepeatDays("Set alarm weekdays at 7"))
        assertEquals(QuickParse.MASK_EVERY_DAY, QuickParse.parseRepeatDays("Alarm every day at 8am"))
    }

    @Test
    fun parseRepeatDaysNamedDays() {
        assertEquals(
            QuickParse.BIT_MON or QuickParse.BIT_WED,
            QuickParse.parseRepeatDays("Alarm on Monday and Wednesday at 6")
        )
    }

    @Test
    fun parseVoiceIntentAlarmIncludesRepeatDays() {
        val intent = QuickParse.parseVoiceIntent("Set an alarm weekdays at 7am")
        assertTrue(intent is ParsedIntent.Alarm)
        val alarm = intent as ParsedIntent.Alarm
        assertEquals(LocalTime.of(7, 0), alarm.time)
        assertEquals(QuickParse.MASK_WEEKDAYS, alarm.repeatDays)
    }

    @Test
    fun parseVoiceIntentRoutine() {
        val intent = QuickParse.parseVoiceIntent("Daily routine to stretch")
        assertTrue(intent is ParsedIntent.Routine)
        val routine = intent as ParsedIntent.Routine
        assertEquals("DAILY", routine.repeatRule)
        assertTrue(routine.title.contains("stretch", ignoreCase = true))
    }

    @Test
    fun parseRelativeDatesOnTransactions() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals(today.minusDays(1), QuickParse.parse("spent 12 yesterday", today).date)
        assertEquals(today.plusDays(1), QuickParse.parse("spent 12 tomorrow", today).date)
        assertEquals(today.plusWeeks(1), QuickParse.parse("spent 12 next week", today).date)
        assertEquals(today, QuickParse.parse("Coffee 4.50 at Starbucks", today).date)
    }
}
