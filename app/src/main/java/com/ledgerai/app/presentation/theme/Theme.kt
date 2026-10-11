package com.ledgerai.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LedgerLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightSoft,
    onPrimaryContainer = LightText,
    secondary = LightAccent,
    onSecondary = Color.White,
    secondaryContainer = LightCardDeep,
    onSecondaryContainer = LightText,
    tertiary = LightAmber,
    onTertiary = OnHighlightNavy,
    background = LightPage,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightCardDeep,
    onSurfaceVariant = LightTextMuted,
    error = LightDanger,
    onError = Color.White,
    outline = LightLine,
    outlineVariant = LightLine,
    inverseSurface = LightPrimary,
    inverseOnSurface = Color.White,
)

private val LedgerDarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkPage,
    primaryContainer = DarkSoft,
    onPrimaryContainer = DarkText,
    secondary = DarkAccent,
    onSecondary = DarkPage,
    secondaryContainer = DarkCardDeep,
    onSecondaryContainer = DarkText,
    tertiary = DarkAmber,
    onTertiary = OnHighlightNavy,
    background = DarkPage,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkCardDeep,
    onSurfaceVariant = DarkTextMuted,
    error = DarkDanger,
    onError = DarkPage,
    outline = DarkLine,
    outlineVariant = DarkLine,
    inverseSurface = DarkText,
    inverseOnSurface = DarkPage,
)

@Composable
fun LedgerAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Written before any child reads it; a system theme change recreates the activity.
    remember(darkTheme) { Palette.dark = darkTheme }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(PageColor.toArgb()))
            window.statusBarColor = PageColor.toArgb()
            window.navigationBarColor = PageColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) LedgerDarkScheme else LedgerLightScheme,
        typography = Typography,
        content = content
    )
}
