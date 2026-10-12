package com.ledgerai.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CLOUD_FALLBACK = booleanPreferencesKey("cloud_fallback")
        val LEARNED_RULES = stringPreferencesKey("learned_voice_rules")
        val LEARNED_AT = stringPreferencesKey("learned_voice_at")
        val TRACK_MODE = stringPreferencesKey("track_mode")
        val CASH_ON_HAND = stringPreferencesKey("cash_on_hand")
        /** One-time local Room wipe (removed demo seed data). */
        val FRESH_LOCAL_RESET_DONE = booleanPreferencesKey("fresh_local_reset_oct_2026_done")
        val CLIP_MENU = booleanPreferencesKey("clip_menu_on")
        val CLIP_FAST = booleanPreferencesKey("clip_fast")
        val CLIP_OFFER = booleanPreferencesKey("clip_offer_copied")
        val CLIP_AI = stringPreferencesKey("clip_ai_mode")
        val CLIP_PIN = booleanPreferencesKey("clip_pin")
        val CLIP_KEEP = stringPreferencesKey("clip_keep")
        val CLIP_COLLECTING = longPreferencesKey("clip_collecting_note")
        val CLIP_STAMP = longPreferencesKey("clip_clipboard_stamp")
        val CLIP_LAST_NOTE = longPreferencesKey("clip_last_note")
        val CLIP_BUBBLE = booleanPreferencesKey("clip_bubble")
        val CLIP_AI_DAY = stringPreferencesKey("clip_ai_day")
        val CLIP_AI_COUNT = stringPreferencesKey("clip_ai_count")
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

    /** "system" (default), "light" or "dark". Applies to the app, not to the widgets. */
    val themeMode: Flow<String> = context.dataStore.data.map {
        when (it[Keys.THEME_MODE]) { "light" -> "light"; "dark" -> "dark"; else -> "system" }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = if (mode == "light" || mode == "dark") mode else "system" }
    }

    /** "en" or "bn". The mic listens to one language at a time. */
    val voiceLanguage: Flow<String> = context.dataStore.data.map {
        if (it[Keys.VOICE_LANGUAGE] == "bn") "bn" else "en"
    }

    suspend fun setVoiceLanguage(code: String) {
        context.dataStore.edit { it[Keys.VOICE_LANGUAGE] = if (code == "bn") "bn" else "en" }
    }

    /** When true, an entry the rules are unsure about may be sent to the cloud. Default on. */
    val cloudFallback: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.CLOUD_FALLBACK] ?: true
    }

    suspend fun setCloudFallback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_FALLBACK] = enabled }
    }

    val learnedRules: Flow<String> = context.dataStore.data.map {
        it[Keys.LEARNED_RULES] ?: ""
    }

    suspend fun learnedRulesNow(): String = context.dataStore.data.first()[Keys.LEARNED_RULES] ?: ""

    suspend fun setLearnedRules(raw: String) {
        context.dataStore.edit { it[Keys.LEARNED_RULES] = raw }
    }

    suspend fun learnedCursor(): Long =
        context.dataStore.data.first()[Keys.LEARNED_AT]?.toLongOrNull() ?: 0L

    suspend fun setLearnedCursor(createdAt: Long) {
        context.dataStore.edit { it[Keys.LEARNED_AT] = createdAt.toString() }
    }

    val clipMenuOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIP_MENU] ?: true }
    val clipFastMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIP_FAST] ?: false }
    val clipOfferCopied: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIP_OFFER] ?: true }
    val clipAiMode: Flow<String> = context.dataStore.data.map { clipMode(it[Keys.CLIP_AI]) }
    val clipPinNotification: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIP_PIN] ?: false }
    val clipRetention: Flow<String> = context.dataStore.data.map { clipKeep(it[Keys.CLIP_KEEP]) }
    val clipCollectingNoteId: Flow<Long?> = context.dataStore.data.map { it[Keys.CLIP_COLLECTING]?.takeIf { id -> id > 0L } }
    val clipClipboardStamp: Flow<Long> = context.dataStore.data.map { it[Keys.CLIP_STAMP] ?: 0L }
    val clipBubble: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLIP_BUBBLE] ?: false }

    suspend fun setClipMenu(on: Boolean) { context.dataStore.edit { it[Keys.CLIP_MENU] = on } }
    suspend fun setClipFast(on: Boolean) { context.dataStore.edit { it[Keys.CLIP_FAST] = on } }
    suspend fun setClipOfferCopied(on: Boolean) { context.dataStore.edit { it[Keys.CLIP_OFFER] = on } }
    suspend fun setClipAiMode(mode: String) { context.dataStore.edit { it[Keys.CLIP_AI] = clipMode(mode) } }
    suspend fun setClipPin(on: Boolean) { context.dataStore.edit { it[Keys.CLIP_PIN] = on } }
    suspend fun setClipRetention(value: String) { context.dataStore.edit { it[Keys.CLIP_KEEP] = clipKeep(value) } }
    suspend fun setClipCollecting(noteId: Long?) {
        context.dataStore.edit { if (noteId == null || noteId <= 0L) it.remove(Keys.CLIP_COLLECTING) else it[Keys.CLIP_COLLECTING] = noteId }
    }
    suspend fun setClipClipboardStamp(stamp: Long) { context.dataStore.edit { it[Keys.CLIP_STAMP] = stamp } }
    suspend fun setClipLastNote(noteId: Long) { context.dataStore.edit { it[Keys.CLIP_LAST_NOTE] = noteId } }
    suspend fun setClipBubble(on: Boolean) { context.dataStore.edit { it[Keys.CLIP_BUBBLE] = on } }

    suspend fun clipAiModeNow(): String = clipMode(context.dataStore.data.first()[Keys.CLIP_AI])
    suspend fun clipFastNow(): Boolean = context.dataStore.data.first()[Keys.CLIP_FAST] ?: false
    suspend fun clipMenuNow(): Boolean = context.dataStore.data.first()[Keys.CLIP_MENU] ?: true
    suspend fun clipCollectingNow(): Long? = context.dataStore.data.first()[Keys.CLIP_COLLECTING]?.takeIf { it > 0L }
    suspend fun clipLastNoteNow(): Long? = context.dataStore.data.first()[Keys.CLIP_LAST_NOTE]?.takeIf { it > 0L }
    suspend fun clipRetentionNow(): String = clipKeep(context.dataStore.data.first()[Keys.CLIP_KEEP])
    suspend fun clipPinNow(): Boolean = context.dataStore.data.first()[Keys.CLIP_PIN] ?: false

    /** Counts one model call. Returns false when today's 100-call cap is already used. */
    suspend fun tryConsumeClipAiCall(): Boolean {
        val today = java.time.LocalDate.now().toString()
        var allowed = false
        context.dataStore.edit { prefs ->
            val count = if (prefs[Keys.CLIP_AI_DAY] == today) prefs[Keys.CLIP_AI_COUNT]?.toIntOrNull() ?: 0 else 0
            if (count >= com.ledgerai.app.domain.clips.ClipFiling.DAILY_CAP) {
                allowed = false
            } else {
                prefs[Keys.CLIP_AI_DAY] = today
                prefs[Keys.CLIP_AI_COUNT] = (count + 1).toString()
                allowed = true
            }
        }
        return allowed
    }

    suspend fun clipAiCallsLeft(): Int {
        val today = java.time.LocalDate.now().toString()
        val prefs = context.dataStore.data.first()
        val count = if (prefs[Keys.CLIP_AI_DAY] == today) prefs[Keys.CLIP_AI_COUNT]?.toIntOrNull() ?: 0 else 0
        return (com.ledgerai.app.domain.clips.ClipFiling.DAILY_CAP - count).coerceAtLeast(0)
    }

    private fun clipMode(raw: String?): String = when (raw) {
        "off" -> "off"
        "auto" -> "auto"
        else -> "suggest"
    }

    private fun clipKeep(raw: String?): String = when (raw) {
        "30" -> "30"
        "forever" -> "forever"
        else -> "365"
    }

    /** Clears all Room tables once per device (see [DatabaseSeeder]). */
    suspend fun runFreshLocalResetIfNeeded(block: suspend () -> Unit) {
        val done = context.dataStore.data.first()[Keys.FRESH_LOCAL_RESET_DONE] == true
        if (done) return
        block()
        context.dataStore.edit { it[Keys.FRESH_LOCAL_RESET_DONE] = true }
    }
}
