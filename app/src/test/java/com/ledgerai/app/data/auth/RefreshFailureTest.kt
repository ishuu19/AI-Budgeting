package com.ledgerai.app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshFailureTest {
    @Test
    fun aRefusedRefreshTokenIsADeadSession() {
        assertTrue(isSessionDead("Invalid Refresh Token: Already Used", null))
        assertTrue(isSessionDead("refresh_token_not_found", null))
        assertTrue(isSessionDead("anything", 400))
        assertTrue(isSessionDead(null, 401))
    }

    @Test
    fun noNetworkOrAServerErrorIsNot() {
        assertFalse(isSessionDead("Unable to resolve host", null))
        assertFalse(isSessionDead("timeout", null))
        assertFalse(isSessionDead("Internal error", 500))
        assertFalse(isSessionDead(null, 503))
    }
}
