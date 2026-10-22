package com.ledgerai.app.domain.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecordIntentRulesTest {
    private val today = LocalDate.of(2026, 10, 22) // a Thursday

    private fun parse(s: String) = RecordIntentRules.parse(s, today)

    @Test fun birthdayDayFirst() = assertEquals(BirthdayDraft("Maya", 11, 14), parse("Maya's birthday is 14 November"))
    @Test fun birthdayMonthFirst() = assertEquals(BirthdayDraft("Dad", 3, 5), parse("remember Dad's birthday is March 5"))
    @Test fun impossibleBirthdayDateIsNotGuessed() = assertNull(parse("Maya's birthday is 31 November"))

    @Test fun tripWithDates() =
        assertEquals(TripDraft("Cox Bazar", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24)), parse("trip to Cox Bazar from 20 to 24 November"))

    @Test fun achievementOnlyInThePast() {
        assertTrue(parse("I finished my thesis") is AchievementDraft)
        assertNull(parse("I will finish my thesis"))
    }

    @Test fun wishWithAndWithoutPrice() {
        assertEquals(WishDraft("A mechanical keyboard", 8500.0), parse("I want to buy a mechanical keyboard for 8,500"))
        assertEquals(WishDraft("A camera", null), parse("I want to buy a camera someday"))
    }

    @Test fun appointmentNeedsADayAndATime() {
        val d = parse("dentist appointment tomorrow at 3pm") as AppointmentDraft
        assertEquals(LocalDateTime.of(2026, 10, 23, 15, 0), d.start)
        assertNull(parse("dentist appointment sometime"))
    }

    @Test fun passwordSentenceOnlyOffersThePasswordsScreen() {
        assertEquals(PasswordGuard, parse("my gmail password is hunter2"))
        assertEquals(PasswordGuard, parse("the pin is 4321"))
    }

    @Test fun everydaySpendingIsLeftToTheOlderPipeline() {
        assertNull(parse("lunch 450"))
        assertNull(parse("add milk to the list"))
    }
}
