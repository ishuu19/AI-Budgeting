package com.ledgerai.app.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun startsLocked() {
        assertFalse(AppLockPolicy().unlocked)
    }

    @Test
    fun aQuickTripToTheCameraStaysUnlocked() {
        val p = AppLockPolicy(graceMs = 15_000)
        p.unlock()
        p.leftApp(1_000)
        assertFalse(p.backInApp(10_000))
        assertTrue(p.unlocked)
    }

    @Test
    fun beingAwayForTheGraceOrLongerLocks() {
        val p = AppLockPolicy(graceMs = 15_000)
        p.unlock()
        p.leftApp(1_000)
        assertTrue(p.backInApp(16_000))
        assertFalse(p.unlocked)
    }

    @Test
    fun screenOffLocksAtOnce() {
        val p = AppLockPolicy()
        p.unlock()
        p.screenOff()
        assertFalse(p.unlocked)
        assertTrue(p.backInApp(0))
    }

    @Test
    fun leavingTwiceDoesNotRestartTheClock() {
        val p = AppLockPolicy(graceMs = 15_000)
        p.unlock()
        p.leftApp(0)
        p.leftApp(14_000)
        assertTrue(p.backInApp(15_000))
    }

    @Test
    fun aLockedAppStaysLockedWhateverHappens() {
        val p = AppLockPolicy()
        p.leftApp(0)
        assertTrue(p.backInApp(1))
        assertFalse(p.unlocked)
    }
}
