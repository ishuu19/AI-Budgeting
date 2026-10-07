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
        val AI_MODEL = stringPreferencesKey("ai_model")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val BUDGET_ALERT_THRESHOLD = stringPreferencesKey("budget_alert_threshold")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val currency: Flow<String> = context.dataStore.data.map {
        it[Keys.CURRENCY] ?: BuildConfig.DEFAULT_CURRENCY
    }

    val currencySymbol: Flow<String> = context.dataStore.data.map {
        it[Keys.CURRENCY_SYMBOL] ?: BuildConfig.DEFAULT_CURRENCY_SYMBOL
    }

    val aiModel: Flow<String> = context.dataStore.data.map {
        it[Keys.AI_MODEL] ?: BuildConfig.AI_MODEL
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

    suspend fun setCurrency(currency: String, symbol: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENCY] = currency
            prefs[Keys.CURRENCY_SYMBOL] = symbol
        }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[Keys.AI_MODEL] = model }
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
}
