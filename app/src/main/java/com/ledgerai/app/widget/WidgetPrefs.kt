package com.ledgerai.app.widget

import android.content.Context

object WidgetPrefs {
    const val NAME = "ledgerai_widget_prefs"

    private const val KEY_PRIVATE = "private_mode"
    private const val KEY_THEME = "theme"
    const val THEME_SYSTEM = "system"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    fun privateMode(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRIVATE, false)

    fun setPrivateMode(context: Context, on: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRIVATE, on)
            .apply()
    }

    fun theme(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }

    fun forceDark(context: Context): Boolean? = when (theme(context)) {
        THEME_DARK -> true
        THEME_LIGHT -> false
        else -> null
    }
}
