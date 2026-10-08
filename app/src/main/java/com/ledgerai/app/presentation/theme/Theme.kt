package com.ledgerai.app.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LedgerLightScheme = lightColorScheme(
    primary = BrandEmerald,
    onPrimary = OnBrandWhite,
    primaryContainer = BrandEmeraldContainer,
    onPrimaryContainer = BrandEmeraldDark,
    secondary = BrandGold,
    onSecondary = BrandEmeraldDark,
    secondaryContainer = BrandGoldContainer,
    onSecondaryContainer = BrandEmeraldDark,
    tertiary = BrandGold,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperIvory,
    onSurfaceVariant = InkMuted,
    error = ExpenseRed,
    onError = OnBrandWhite,
    outline = BrandGold.copy(alpha = 0.45f),
    outlineVariant = BrandGoldLight,
    inverseSurface = BrandEmerald,
    inverseOnSurface = OnBrandWhite,
)

@Composable
fun LedgerAITheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LedgerLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Paper.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
