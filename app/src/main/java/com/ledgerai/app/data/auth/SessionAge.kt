package com.ledgerai.app.data.auth

/** A sign-in lasts at most 30 days, however often the token is renewed. Then the user signs in again. */
const val MAX_SESSION_MS = 30L * 24 * 60 * 60 * 1000

/** True when a sign-in made at [signedInAt] is [MAX_SESSION_MS] or more old. An unknown start (0) is not expired. */
fun sessionTooOld(signedInAt: Long, now: Long): Boolean = signedInAt > 0L && now - signedInAt >= MAX_SESSION_MS
