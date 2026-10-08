package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickParseIntentTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun parseVoiceIntent_alarmWithWeekdays() {
        val intent = QuickParse.parseVoiceIntent(
            "Set an alarm for 7:30 am on weekdays",
            today = today,
        )
        assertTrue(intent is ParsedIntent.Alarm)
        val alarm = intent as ParsedIntent.Alarm
        assertEquals(LocalTime.of(7, 30), alarm.time)
        assertEquals(QuickParse.MASK_WEEKDAYS, alarm.repeatDays)
    }

    @Test
    fun parseVoiceIntent_routineWeekly() {
        val intent = QuickParse.parseVoiceIntent(
            "Add a weekly routine to stretch",
            today = today,
        )
        assertTrue(intent is ParsedIntent.Routine)
        val routine = intent as ParsedIntent.Routine
        assertEquals("WEEKLY", routine.repeatRule)
        assertTrue(routine.title.lowercase().contains("stretch"))
    }

    @Test
    fun parseVoiceIntent_task() {
        val intent = QuickParse.parseVoiceIntent("Add task Buy milk", today = today)
        assertTrue(intent is ParsedIntent.Task)
        assertEquals("Buy milk", (intent as ParsedIntent.Task).title)
    }

    @Test
    fun parseVoiceIntent_fallsBackToTransaction() {
        val intent = QuickParse.parseVoiceIntent("Coffee 4.50 at Starbucks", today = today)
        assertTrue(intent is ParsedIntent.Transaction)
    }

    @Test
    fun parseRepeatDays_everyDayAndWeekends() {
        assertEquals(QuickParse.MASK_EVERY_DAY, QuickParse.parseRepeatDays("alarm every day at 8"))
        assertEquals(QuickParse.MASK_WEEKENDS, QuickParse.parseRepeatDays("weekend alarm"))
        assertEquals(0, QuickParse.parseRepeatDays("alarm at 8 am"))
    }

    @Test
    fun parseRepeatDays_namedDays() {
        val mask = QuickParse.parseRepeatDays("alarm on monday and friday")
        assertEquals(QuickParse.BIT_MON or QuickParse.BIT_FRI, mask)
    }

    @Test
    fun parseRepeatRule_tokens() {
        assertEquals("WEEKDAYS", QuickParse.parseRepeatRule("weekday habit"))
        assertEquals("WEEKLY", QuickParse.parseRepeatRule("once a week routine"))
        assertEquals("DAILY", QuickParse.parseRepeatRule("daily routine"))
        assertEquals("DAILY", QuickParse.parseRepeatRule("custom habit"))
    }

    @Test
    fun parseClockTimeFromText_amPm() {
        assertEquals(LocalTime.of(7, 30), QuickParse.parseClockTimeFromText("wake me at 7:30 am"))
        assertEquals(LocalTime.of(19, 0), QuickParse.parseClockTimeFromText("alarm at 7 pm"))
        assertEquals(LocalTime.of(0, 15), QuickParse.parseClockTimeFromText("alarm at 12:15 am"))
    }

    @Test
    fun parseVoiceIntent_billMonthlyDefault() {
        val intent = QuickParse.parseVoiceIntent("Netflix bill 15 dollars", today = today)
        assertTrue(intent is ParsedIntent.Bill)
        val bill = intent as ParsedIntent.Bill
        assertEquals(15.0, bill.amount, 0.001)
        assertEquals(BillFrequency.MONTHLY, bill.frequency)
        assertTrue(bill.name.contains("Netflix", ignoreCase = true))
        assertEquals(today, bill.nextDueDate)
    }

    @Test
    fun parseVoiceIntent_billWeeklySubscription() {
        val intent = QuickParse.parseVoiceIntent("weekly Spotify subscription 9.99", today = today)
        assertTrue(intent is ParsedIntent.Bill)
        val bill = intent as ParsedIntent.Bill
        assertEquals(BillFrequency.WEEKLY, bill.frequency)
        assertEquals(9.99, bill.amount, 0.001)
        assertTrue(bill.name.contains("Spotify", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_billYearly() {
        val intent = QuickParse.parseVoiceIntent("yearly domain bill 120", today = today)
        assertTrue(intent is ParsedIntent.Bill)
        assertEquals(BillFrequency.YEARLY, (intent as ParsedIntent.Bill).frequency)
        assertEquals(120.0, intent.amount, 0.001)
    }

    @Test
    fun parseVoiceIntent_billDueTomorrow() {
        val intent = QuickParse.parseVoiceIntent("electric bill 80 due tomorrow", today = today)
        assertTrue(intent is ParsedIntent.Bill)
        val bill = intent as ParsedIntent.Bill
        assertEquals(today.plusDays(1), bill.nextDueDate)
        assertTrue(bill.name.contains("electric", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_spentOnNetflixStaysTransaction() {
        val intent = QuickParse.parseVoiceIntent("spent 15 on netflix", today = today)
        assertTrue(intent is ParsedIntent.Transaction)
        val tx = intent as ParsedIntent.Transaction
        assertEquals(15.0, tx.amount!!, 0.001)
    }

    @Test
    fun parseVoiceIntent_debtIOwe() {
        val intent = QuickParse.parseVoiceIntent("I owe Alex 40", today = today)
        assertTrue(intent is ParsedIntent.Debt)
        val debt = intent as ParsedIntent.Debt
        assertEquals(DebtDirection.I_OWE, debt.direction)
        assertEquals(40.0, debt.amount, 0.001)
        assertTrue(debt.friendName.contains("Alex", ignoreCase = true))
        assertNull(debt.dueDate)
    }

    @Test
    fun parseVoiceIntent_debtTheyOwe() {
        val intent = QuickParse.parseVoiceIntent("they owe Jordan 25", today = today)
        assertTrue(intent is ParsedIntent.Debt)
        val debt = intent as ParsedIntent.Debt
        assertEquals(DebtDirection.THEY_OWE, debt.direction)
        assertEquals(25.0, debt.amount, 0.001)
        assertTrue(debt.friendName.contains("Jordan", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_debtLent() {
        val intent = QuickParse.parseVoiceIntent("I lent Maya 50", today = today)
        assertTrue(intent is ParsedIntent.Debt)
        val debt = intent as ParsedIntent.Debt
        assertEquals(DebtDirection.THEY_OWE, debt.direction)
        assertEquals(50.0, debt.amount, 0.001)
        assertTrue(debt.friendName.contains("Maya", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_debtBorrowed() {
        val intent = QuickParse.parseVoiceIntent("I borrowed 20 from Tom", today = today)
        assertTrue(intent is ParsedIntent.Debt)
        val debt = intent as ParsedIntent.Debt
        assertEquals(DebtDirection.I_OWE, debt.direction)
        assertEquals(20.0, debt.amount, 0.001)
        assertTrue(debt.friendName.contains("Tom", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_debtDueNextWeek() {
        val intent = QuickParse.parseVoiceIntent("I owe Alex 40 due next week", today = today)
        assertTrue(intent is ParsedIntent.Debt)
        assertEquals(today.plusWeeks(1), (intent as ParsedIntent.Debt).dueDate)
    }

    @Test
    fun parseVoiceIntent_goalSave() {
        val intent = QuickParse.parseVoiceIntent("save 500 for vacation", today = today)
        assertTrue(intent is ParsedIntent.Goal)
        val goal = intent as ParsedIntent.Goal
        assertEquals(500.0, goal.targetAmount, 0.001)
        assertTrue(goal.name.contains("vacation", ignoreCase = true))
    }

    @Test
    fun parseVoiceIntent_goalExplicit() {
        val intent = QuickParse.parseVoiceIntent("goal 2000 for a car", today = today)
        assertTrue(intent is ParsedIntent.Goal)
        val goal = intent as ParsedIntent.Goal
        assertEquals(2000.0, goal.targetAmount, 0.001)
        assertTrue(goal.name.contains("car", ignoreCase = true))
    }

    @Test
    fun parse_yesterdayTomorrowNextWeek() {
        val yesterday = QuickParse.parse("spent 12 yesterday", today = today)
        assertEquals(today.minusDays(1), yesterday.date)
        val tomorrow = QuickParse.parse("spent 12 tomorrow", today = today)
        assertEquals(today.plusDays(1), tomorrow.date)
        val nextWeek = QuickParse.parse("spent 12 next week", today = today)
        assertEquals(today.plusWeeks(1), nextWeek.date)
    }

    @Test
    fun parseVoiceIntent_incomeStillTransaction() {
        val intent = QuickParse.parseVoiceIntent("Got paid salary 3200", today = today)
        assertTrue(intent is ParsedIntent.Transaction)
        assertEquals(TransactionType.INCOME, (intent as ParsedIntent.Transaction).type)
        assertEquals(3200.0, intent.amount!!, 0.001)
    }

    @Test
    fun parseVoiceIntent_alarmStillBeatsMoney() {
        val intent = QuickParse.parseVoiceIntent("Set an alarm for 7 am", today = today)
        assertTrue(intent is ParsedIntent.Alarm)
    }

    @Test
    fun parseVoiceIntent_taskStillBeatsBillWords() {
        val intent = QuickParse.parseVoiceIntent("Add task pay the netflix bill", today = today)
        assertTrue(intent is ParsedIntent.Task)
    }
}
