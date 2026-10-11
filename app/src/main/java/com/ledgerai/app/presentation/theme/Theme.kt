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
    primary = Color(0xFF17306F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF0C1A3E),
    secondary = Color(0xFF2F5FE3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7ECF8),
    onSecondaryContainer = Color(0xFF0C1A3E),
    tertiary = Color(0xFFF5A524),
    background = Color(0xFFF2F5FB),
    onBackground = Color(0xFF0C1A3E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1A3E),
    surfaceVariant = Color(0xFFE7ECF8),
    onSurfaceVariant = Color(0xFF55638A),
    error = Color(0xFFC62828),
    onError = Color.White,
    outline = Color(0xFFDCE3F2),
    outlineVariant = Color(0xFFDCE3F2),
    inverseSurface = Color(0xFF17306F),
    inverseOnSurface = Color.White,
)

private val LedgerDarkScheme = darkColorScheme(
    primary = Color(0xFFB4C3FF),
    onPrimary = Color(0xFF101113),
    primaryContainer = Color(0xFF34363D),
    onPrimaryContainer = Color(0xFFECECEF),
    secondary = Color(0xFF8DA2F2),
    onSecondary = Color(0xFF101113),
    secondaryContainer = Color(0xFF26272B),
    onSecondaryContainer = Color(0xFFECECEF),
    tertiary = Color(0xFFFFB84D),
    background = Color(0xFF101113),
    onBackground = Color(0xFFECECEF),
    surface = Color(0xFF1B1C1F),
    onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF26272B),
    onSurfaceVariant = Color(0xFFA0A1A8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF101113),
    outline = Color(0xFF2E2F34),
    outlineVariant = Color(0xFF2E2F34),
    inverseSurface = Color(0xFFECECEF),
    inverseOnSurface = Color(0xFF101113),
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
