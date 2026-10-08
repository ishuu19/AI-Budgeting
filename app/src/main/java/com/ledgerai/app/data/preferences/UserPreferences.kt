package com.ledgerai.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ledgerai.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val BUDGET_ALERT_THRESHOLD = stringPreferencesKey("budget_alert_threshold")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val VOICE_ONLY_WIDGET = booleanPreferencesKey("voice_only_widget")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val TRACK_MODE = stringPreferencesKey("track_mode")
        val CASH_ON_HAND = stringPreferencesKey("cash_on_hand")
        /** One-time local Room wipe (removed demo seed data). */
        val FRESH_LOCAL_RESET_DONE = booleanPreferencesKey("fresh_local_reset_oct_2026_done")
    }

    val currency: Flow<String> = context.dataStore.data.map {
        it[Keys.CURRENCY] ?: BuildConfig.DEFAULT_CURRENCY
    }

    val currencySymbol: Flow<String> = context.dataStore.data.map {
        it[Keys.CURRENCY_SYMBOL] ?: BuildConfig.DEFAULT_CURRENCY_SYMBOL
    }

    val darkTheme: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.DARK_THEME] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val budgetAlertThreshold: Flow<Int> = context.dataStore.data.map {
        it[Keys.BUDGET_ALERT_THRESHOLD]?.toIntOrNull() ?: 80
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDING_COMPLETE] ?: false
    }

    /** When true, Glance widget shows voice controls without quote text. */
    val voiceOnlyWidget: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.VOICE_ONLY_WIDGET] ?: false
    }

    suspend fun setCurrency(currency: String, symbol: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENCY] = currency
            prefs[Keys.CURRENCY_SYMBOL] = symbol
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBudgetAlertThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.BUDGET_ALERT_THRESHOLD] = threshold.toString() }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = true }
    }

    suspend fun setVoiceOnlyWidget(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_ONLY_WIDGET] = enabled }
    }

    val voiceEngine: Flow<String> = context.dataStore.data.map {
        it[Keys.VOICE_ENGINE] ?: "sherpa"
    }

    /** "both" records income and spending. "expenses" tracks spending only. */
    val trackMode: Flow<String> = context.dataStore.data.map {
        it[Keys.TRACK_MODE] ?: "both"
    }

    val cashOnHand: Flow<String> = context.dataStore.data.map {
        it[Keys.CASH_ON_HAND] ?: ""
    }

    suspend fun setTrackMode(mode: String) {
        context.dataStore.edit { it[Keys.TRACK_MODE] = if (mode == "expenses") "expenses" else "both" }
    }

    suspend fun setCashOnHand(amount: String) {
        context.dataStore.edit { it[Keys.CASH_ON_HAND] = amount.trim() }
    }

    suspend fun setVoiceEngine(id: String) {
        context.dataStore.edit { it[Keys.VOICE_ENGINE] = id }
    }

    /** Clears all Room tables once per device (see [DatabaseSeeder]). */
    suspend fun runFreshLocalResetIfNeeded(block: suspend () -> Unit) {
        val done = context.dataStore.data.first()[Keys.FRESH_LOCAL_RESET_DONE] == true
        if (done) return
        block()
        context.dataStore.edit { it[Keys.FRESH_LOCAL_RESET_DONE] = true }
    }
}
