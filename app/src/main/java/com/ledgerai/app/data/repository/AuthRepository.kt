package com.ledgerai.app.data.repository

import android.util.Log
import androidx.annotation.Nullable
import com.ledgerai.app.data.preferences.UserSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.user.UserSession as SupabaseUserSession
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @Nullable private val supabase: SupabaseClient?,
    private val userSession: UserSession
) {

    val isSupabaseConfigured: Boolean
        get() = supabase != null

    /**
     * Exchange a Google ID token for a Supabase session and persist access/refresh tokens + user id.
     */
    suspend fun signInWithIdToken(idToken: String) {
        val client = supabase
            ?: error("Supabase is not configured (SUPABASE_URL / SUPABASE_ANON_KEY empty)")

        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }

        val session = client.auth.currentSessionOrNull()
            ?: error("No Supabase session after Google sign-in")
        persistSupabaseSession(session)
    }

    /**
     * Refresh the access JWT when it is missing expiry metadata or within [REFRESH_SKEW_MS] of expiry.
     * Uses supabase-kt [io.github.jan.supabase.gotrue.Auth.refreshSession] with the persisted refresh token.
     *
     * @return true if the session is usable (fresh, refreshed, or local-only); false if refresh failed.
     */
    suspend fun refreshIfNeeded(): Boolean {
        val info = userSession.userInfo.first()
        if (!info.isLoggedIn || info.userId.startsWith("local-")) return true
        if (info.refreshToken.isBlank()) {
            // Legacy sessions without a refresh token: keep using the access token if present.
            return info.accessToken.isNotBlank()
        }

        val now = System.currentTimeMillis()
        if (info.expiresAtEpochMs > 0L && now + REFRESH_SKEW_MS < info.expiresAtEpochMs) {
            return true
        }

        val client = supabase ?: return info.accessToken.isNotBlank()
        return try {
            val session = client.auth.refreshSession(info.refreshToken)
            persistSupabaseSession(session, fallbackUserId = info.userId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "JWT refresh failed: ${e.message}")
            false
        }
    }

    suspend fun signOut() {
        runCatching {
            val client = supabase ?: return@runCatching
            client.auth.signOut()
        }
        userSession.clearSession()
    }

    private suspend fun persistSupabaseSession(
        session: SupabaseUserSession,
        fallbackUserId: String = "",
    ) {
        val user = session.user
        val meta = user?.userMetadata
        val name = metaString(meta, "full_name")
            ?: metaString(meta, "name")
            ?: user?.email
            ?: ""
        val photo = metaString(meta, "avatar_url")
            ?: metaString(meta, "picture")
            ?: ""
        val expiresAt = if (session.expiresIn > 0L) {
            System.currentTimeMillis() + session.expiresIn * 1000L
        } else {
            0L
        }

        userSession.saveUser(
            userId = user?.id?.takeIf { it.isNotBlank() } ?: fallbackUserId,
            name = name,
            email = user?.email ?: "",
            photoUrl = photo,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochMs = expiresAt,
        )
    }

    private fun metaString(
        meta: kotlinx.serialization.json.JsonObject?,
        key: String
    ): String? {
        val raw = meta?.get(key)?.toString()?.trim()?.trim('"')
        return raw?.takeIf { it.isNotBlank() && it != "null" }
    }

    companion object {
        private const val TAG = "AuthRepository"
        /** Refresh when the access token expires within one minute. */
        private const val REFRESH_SKEW_MS = 60_000L
    }
}
