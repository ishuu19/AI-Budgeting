package com.ledgerai.app.domain.security

/**
 * When the app is open for use and when it is locked. Pure, so the rules can be tested.
 * Locked at start. Unlocked only by [unlock], which the caller may call only after the fingerprint
 * check passed. Locks again when the screen turns off, when the app has been in the background for
 * [graceMs] or longer, and on any explicit [lock]. The short grace lets the camera, gallery and
 * speech dialog open over the app without asking again.
 */
class AppLockPolicy(private val graceMs: Long = DEFAULT_GRACE_MS) {
    var unlocked: Boolean = false
        private set

    /** Time the app went to the background, or null while it is in front. */
    private var leftAt: Long? = null

    fun unlock() {
        unlocked = true
        leftAt = null
    }

    fun lock() {
        unlocked = false
        leftAt = null
    }

    fun leftApp(now: Long) {
        if (unlocked && leftAt == null) leftAt = now
    }

    /** @return true if this return to the app found it locked. */
    fun backInApp(now: Long): Boolean {
        val gone = leftAt
        leftAt = null
        if (unlocked && gone != null && now - gone >= graceMs) unlocked = false
        return !unlocked
    }

    fun screenOff() = lock()

    companion object {
        const val DEFAULT_GRACE_MS = 15_000L
    }
}
