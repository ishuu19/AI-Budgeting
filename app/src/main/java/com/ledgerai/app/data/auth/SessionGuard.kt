package com.ledgerai.app.data.auth

import com.ledgerai.app.data.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures a usable Supabase access JWT before network work that needs auth
 * (sync, Edge AI). Delegates to [AuthRepository.refreshIfNeeded].
 */
@Singleton
class SessionGuard @Inject constructor(
    private val authRepository: AuthRepository,
) {
    /**
     * @return true if the session is usable (or local-only); false if refresh failed.
     */
    suspend fun ensureFreshSession(): Boolean = authRepository.refreshIfNeeded()
}
