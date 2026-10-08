package com.ledgerai.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * White paper · royal green cards · royal gold / ivory accents.
 */

val Paper = Color(0xFFFFFFFF)
val PaperIvory = Color(0xFFFFF9EE)
val Ink = Color(0xFF0C2F24)
val InkMuted = Color(0xFF5C6B66)

val BrandEmerald = Color(0xFF0F5C45)
val BrandEmeraldDark = Color(0xFF0A3D2E)
val BrandEmeraldBright = Color(0xFF147A5A)
val BrandEmeraldContainer = Color(0xFFE8F4EE)
val BrandGold = Color(0xFFD4AF37)
val BrandGoldLight = Color(0xFFF3E6C0)
val BrandGoldContainer = Color(0xFFFFF6DC)

val DarkBackground = Paper
val DarkSurface = BrandEmerald
val DarkSurfaceElevated = BrandEmeraldBright
val DarkSurfaceVariant = Color(0xFF1A6B52)
val DarkBorder = BrandGold.copy(alpha = 0.45f)

val IncomeGreen = Color(0xFF2E8B57)
val IncomeGreenContainer = BrandEmeraldContainer
val ExpenseRed = Color(0xFFC0392B)
val ExpenseRedContainer = Color(0xFFFDECEA)
val WarningAmber = BrandGold

val TextPrimary = Color(0xFFFFFDF8)
val TextSecondary = BrandGoldLight
val TextTertiary = Color(0xFFD4C48A)
val OnBrandWhite = Color(0xFFFFFDF8)

val RoyalDarkGreen = BrandEmeraldDark
val RoyalGreen = BrandEmerald
val RoyalGold = BrandGold
val RoyalGoldLight = BrandGoldLight
val RoyalWhite = Color(0xFFFFFDF8)
val RoyalOnGreen = OnBrandWhite
val RoyalOnWhite = Ink
val NeutralGray = InkMuted

val CategoryColors = listOf(
    Color(0xFF0F5C45),
    Color(0xFFD4AF37),
    Color(0xFF2E8B57),
    Color(0xFF8B7355),
    Color(0xFFC9A227),
    Color(0xFF165C3A),
    Color(0xFFB8860B),
    Color(0xFF3D6B5A),
    Color(0xFFDAA520),
    Color(0xFF6B7F77),
)
