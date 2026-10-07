package com.ledgerai.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val googleAccountEmail: String = ""   // the account used for calendar
)

@Singleton
class UserSession @Inject constructor(@ApplicationContext private val context: Context) {

    private val store = context.sessionDataStore

    companion object {
        val KEY_LOGGED_IN    = booleanPreferencesKey("logged_in")
        val KEY_USER_ID      = stringPreferencesKey("user_id")
        val KEY_NAME         = stringPreferencesKey("display_name")
        val KEY_EMAIL        = stringPreferencesKey("email")
        val KEY_PHOTO        = stringPreferencesKey("photo_url")
        val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_account_email")
    }

    val userInfo: Flow<UserInfo> = store.data.map { prefs ->
        UserInfo(
            isLoggedIn    = prefs[KEY_LOGGED_IN] ?: false,
            userId        = prefs[KEY_USER_ID] ?: "",
            displayName   = prefs[KEY_NAME] ?: "",
            email         = prefs[KEY_EMAIL] ?: "",
            photoUrl      = prefs[KEY_PHOTO] ?: "",
            googleAccountEmail = prefs[KEY_GOOGLE_EMAIL] ?: ""
        )
    }

    suspend fun saveUser(userId: String, name: String, email: String, photoUrl: String) {
        store.edit { prefs ->
            prefs[KEY_LOGGED_IN]    = true
            prefs[KEY_USER_ID]      = userId
            prefs[KEY_NAME]         = name
            prefs[KEY_EMAIL]        = email
            prefs[KEY_PHOTO]        = photoUrl
            prefs[KEY_GOOGLE_EMAIL] = email
        }
    }

    suspend fun clearSession() {
        store.edit { it.clear() }
    }
}
