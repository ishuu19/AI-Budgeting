package com.ledgerai.app.presentation.screens.subscriptions

import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAmountTextTest {

    @Test
    fun figure_unknownIsNotAZeroAmount() {
        assertEquals("Unknown", figure(SubscriptionAmount.Unknown))
        assertTrue(figure(SubscriptionAmount.Unknown) != figure(SubscriptionAmount.Known(0.0)))
    }

    @Test
    fun parseAmount_blankIsUnknownAndZeroIsKnown() {
        assertEquals(SubscriptionAmount.Unknown, parseAmount("   "))
        assertEquals(SubscriptionAmount.Known(0.0), parseAmount("0"))
        assertNull(parseAmount("."))
        assertNull(parseAmount("-1"))
    }
}
