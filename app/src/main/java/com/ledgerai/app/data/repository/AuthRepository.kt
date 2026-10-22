package com.ledgerai.app.data.repository

import android.util.Log
import androidx.annotation.Nullable
import com.ledgerai.app.data.ai.InsightStore
import com.ledgerai.app.data.local.room.LedgerDatabase
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.sync.SyncCursorStore
import com.ledgerai.app.data.sync.SyncRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.user.UserSession as SupabaseUserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @Nullable private val supabase: SupabaseClient?,
    private val userSession: UserSession,
    private val database: LedgerDatabase,
    private val syncRepository: SyncRepository,
    private val cursorStore: SyncCursorStore,
    private val insightStore: InsightStore,
) {

    val isSupabaseConfigured: Boolean
        get() = supabase != null

    /**
     * Exchange a Google ID token for a Supabase session and persist access/refresh tokens + user id.
     */
    suspend fun signInWithIdToken(idToken: String) {
        val client = supabase
            ?: error("Supabase is not configured (SUPABASE_URL / SUPABASE_ANON_KEY empty)")

        val previous = userSession.userInfo.first()
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }

        val session = client.auth.currentSessionOrNull()
            ?: error("No Supabase session after Google sign-in")
        val newUserId = session.user?.id.orEmpty()
        val pendingId = previous.pendingUploadUserId
        if (pendingId.isNotBlank() && pendingId == newUserId) {
            persistSupabaseSession(session, newSignIn = true)
            syncUploadingPending()
            return
        }

        val switching = previous.userId.isNotBlank() && previous.userId != newUserId
        val foreignPending = pendingId.isNotBlank() && pendingId != newUserId
        if (switching && previous.hasRemoteUser) {
            syncUploadingPending()
        }
        // Local rows from before sign-in are kept and uploaded under this account; only a different account clears them.
        if (switching || foreignPending) {
            wipeLocal()
            if (foreignPending) userSession.clearPendingUploadUserId()
        }
        persistSupabaseSession(session, newSignIn = true)
        syncUploadingPending()
    }

    /**
     * Refresh the access JWT when it is missing expiry metadata or within [REFRESH_SKEW_MS] of expiry.
     * Uses supabase-kt [io.github.jan.supabase.gotrue.Auth.refreshSession] with the persisted refresh token.
     *
     * @return true if the session is usable (fresh, refreshed, or local-only); false if refresh failed.
     */
    suspend fun refreshIfNeeded(): Boolean = refreshLock.withLock {
        val info = userSession.userInfo.first()
        if (!info.isLoggedIn || info.userId.startsWith("local-")) return@withLock true
        if (info.refreshToken.isBlank()) {
            // Legacy sessions without a refresh token: keep using the access token if present.
            return@withLock info.accessToken.isNotBlank()
        }

        val now = System.currentTimeMillis()
        if (com.ledgerai.app.data.auth.sessionTooOld(info.signedInAtEpochMs, now)) {
            endDeadSession(info.userId, "30 days since sign-in")
            return@withLock false
        }
        if (info.expiresAtEpochMs > 0L && now + REFRESH_SKEW_MS < info.expiresAtEpochMs) {
            return@withLock true
        }

        val client = supabase ?: return@withLock info.accessToken.isNotBlank()
        try {
            val session = client.auth.refreshSession(info.refreshToken)
            persistSupabaseSession(session, fallbackUserId = info.userId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "JWT refresh failed: ${e.message}")
            val status = (e as? io.github.jan.supabase.exceptions.RestException)?.statusCode
            if (com.ledgerai.app.data.auth.isSessionDead(e.message, status)) endDeadSession(info.userId)
            false
        }
    }

    /**
     * One refresh at a time. Supabase rotates the refresh token on every use, so two callers refreshing
     * together (sync, AI calls, widgets) would spend the same token twice and kill the session
     * ("Already Used"). The second caller waits, then sees the fresh token and returns.
     */
    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The refresh token was refused, so this sign-in cannot be renewed. Log out so the sign-in screen shows.
     * Local data is kept and marked as waiting to upload: signing in to the same account sends it up.
     */
    private suspend fun endDeadSession(userId: String, reason: String = "refresh token refused") {
        Log.w(TAG, "Sign-in ended ($reason): logging out. Local data is kept for the next sign-in.")
        userSession.clearSession()
        if (userId.isNotBlank() && !userId.startsWith("local-")) userSession.setPendingUploadUserId(userId)
    }

    /** Logs out once the sign-in is 30 days old. No network. Called whenever the app opens and before each refresh. */
    suspend fun enforceSessionLimit() {
        val info = userSession.userInfo.first()
        if (info.hasRemoteUser && com.ledgerai.app.data.auth.sessionTooOld(info.signedInAtEpochMs, System.currentTimeMillis())) {
            endDeadSession(info.userId, "30 days since sign-in")
        }
    }

    suspend fun signOut() {
        val info = userSession.userInfo.first()
        val uploaded = if (info.hasRemoteUser) syncUploadingPending() else true
        runCatching { supabase?.auth?.signOut() }
        userSession.clearSession()
        if (uploaded) {
            wipeLocal()
        } else {
            userSession.setPendingUploadUserId(info.userId)
        }
    }

    /** @return true when [SyncRepository.syncAll] finished; then clears [UserSession] pending upload id. */
    private suspend fun syncUploadingPending(): Boolean {
        val uploaded = runCatching { syncRepository.syncAll() }.getOrDefault(false)
        if (uploaded) userSession.clearPendingUploadUserId()
        return uploaded
    }

    private suspend fun wipeLocal() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        cursorStore.clear()
        insightStore.clear()
    }

    private suspend fun persistSupabaseSession(
        session: SupabaseUserSession,
        fallbackUserId: String = "",
        newSignIn: Boolean = false,
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
            signedInAtEpochMs = if (newSignIn) System.currentTimeMillis() else null,
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
