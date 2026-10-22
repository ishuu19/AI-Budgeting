package com.ledgerai.app.data.auth

/**
 * Whether a failed token refresh means the sign-in is gone for good (the refresh token was refused),
 * as opposed to a passing problem such as no network or a server hiccup. A dead sign-in logs the user out.
 *
 * @param status the HTTP status of the refusal, or null when there was none (offline, timeout).
 */
fun isSessionDead(message: String?, status: Int?): Boolean {
    if (status != null && status in 400..403) return true
    val text = message.orEmpty().lowercase()
    return DEAD_MARKERS.any { it in text }
}

private val DEAD_MARKERS = listOf(
    "invalid refresh token",
    "refresh token not found",
    "refresh_token_not_found",
    "refresh_token_already_used",
    "already used",
    "invalid_grant",
    "session_not_found",
    "session expired",
)
