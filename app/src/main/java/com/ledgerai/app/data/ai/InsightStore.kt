package com.ledgerai.app.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cached daily AI insight for the dashboard card (SharedPreferences).
 * Severity support types (from validator): info | watch | alert.
 */
@Singleton
class InsightStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(insight: InsightDto, date: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(KEY_TITLE, insight.title.orEmpty())
            .putString(KEY_BODY, insight.body.orEmpty())
            .putString(KEY_SEVERITY, insight.severity ?: "info")
            .putString(KEY_ACTIONS, insight.actions.orEmpty().joinToString("\n"))
            .putString(KEY_DATE, date.toString())
            .putString(KEY_TYPE, AiResponseType.INSIGHT.wireName)
            .apply()
    }

    fun readToday(date: LocalDate = LocalDate.now()): InsightDto? {
        if (prefs.getString(KEY_DATE, null) != date.toString()) return null
        val title = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() } ?: return null
        val body = prefs.getString(KEY_BODY, null)?.takeIf { it.isNotBlank() } ?: return null
        val severity = prefs.getString(KEY_SEVERITY, "info") ?: "info"
        val actions = prefs.getString(KEY_ACTIONS, "")
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return InsightDto(title = title, body = body, severity = severity, actions = actions)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "ledgerai_ai_insight"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_SEVERITY = "severity"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_DATE = "date"
        private const val KEY_TYPE = "type"
    }
}
