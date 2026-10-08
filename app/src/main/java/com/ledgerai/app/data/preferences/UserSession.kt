package com.ledgerai.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("user_session")

data class UserInfo(
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    /** Supabase access JWT; empty for local-only sessions. */
    val accessToken: String = "",
    /** Supabase refresh token; empty for local-only sessions. */
    val refreshToken: String = "",
    /** Access-token expiry instant (epoch millis); 0 if unknown. */
    val expiresAtEpochMs: Long = 0L,
    /**
     * Remote user id whose Room rows were kept because sign-out sync failed.
     * Empty after a successful [com.ledgerai.app.data.sync.SyncRepository.syncAll].
     */
    val pendingUploadUserId: String = "",
) {
    /** Cloud session: non-local user id + Bearer JWT present. */
    val hasRemoteUser: Boolean
        get() = isLoggedIn &&
            userId.isNotBlank() &&
            !userId.startsWith("local-") &&
            accessToken.isNotBlank()
}

@Singleton
class UserSession @Inject constructor(@ApplicationContext private val context: Context) {

    private val store = context.sessionDataStore

    companion object {
        val KEY_LOGGED_IN = booleanPreferencesKey("logged_in")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NAME = stringPreferencesKey("display_name")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_PHOTO = stringPreferencesKey("photo_url")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_EXPIRES_AT = longPreferencesKey("expires_at_epoch_ms")
        val KEY_PENDING_UPLOAD_USER_ID = stringPreferencesKey("pending_upload_user_id")
    }

    val userInfo: Flow<UserInfo> = store.data.map { prefs ->
        UserInfo(
            isLoggedIn = prefs[KEY_LOGGED_IN] ?: false,
            userId = prefs[KEY_USER_ID] ?: "",
            displayName = prefs[KEY_NAME] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            photoUrl = prefs[KEY_PHOTO] ?: "",
            accessToken = prefs[KEY_ACCESS_TOKEN] ?: "",
            refreshToken = prefs[KEY_REFRESH_TOKEN] ?: "",
            expiresAtEpochMs = prefs[KEY_EXPIRES_AT] ?: 0L,
            pendingUploadUserId = prefs[KEY_PENDING_UPLOAD_USER_ID] ?: "",
        )
    }

    /** Supabase JWT for Edge Function calls; empty when local-only. */
    val accessToken: Flow<String> = userInfo.map { it.accessToken }

    suspend fun saveUser(
        userId: String,
        name: String,
        email: String,
        photoUrl: String,
        accessToken: String = "",
        refreshToken: String = "",
        expiresAtEpochMs: Long = 0L,
    ) {
        store.edit { prefs ->
            prefs[KEY_LOGGED_IN] = true
            prefs[KEY_USER_ID] = userId
            prefs[KEY_NAME] = name
            prefs[KEY_EMAIL] = email
            prefs[KEY_PHOTO] = photoUrl
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_EXPIRES_AT] = expiresAtEpochMs
        }
    }

    suspend fun clearSession() {
        store.edit { it.clear() }
    }

    suspend fun setPendingUploadUserId(userId: String) {
        store.edit { prefs ->
            if (userId.isBlank()) prefs.remove(KEY_PENDING_UPLOAD_USER_ID)
            else prefs[KEY_PENDING_UPLOAD_USER_ID] = userId
        }
    }

    suspend fun clearPendingUploadUserId() {
        store.edit { it.remove(KEY_PENDING_UPLOAD_USER_ID) }
    }
}
