package com.ledgerai.app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAgeTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aSessionEndsAtThirtyDays() {
        assertFalse(sessionTooOld(1_000, 1_000 + 29 * day))
        assertTrue(sessionTooOld(1_000, 1_000 + 30 * day))
        assertTrue(sessionTooOld(1_000, 1_000 + 90 * day))
    }

    @Test
    fun anUnknownStartIsNotExpired() {
        assertFalse(sessionTooOld(0, 100 * day))
    }
}
