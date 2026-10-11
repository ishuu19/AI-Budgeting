package com.ledgerai.app.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Deep blue and white, with one warm amber highlight. Light and dark are two sets of the same tokens,
 * so a screen that only uses tokens (L.* or MaterialTheme) follows the system theme with no extra code.
 * The home-screen widgets keep their own palette in widget/WidgetTheme.kt.
 */
object Palette {
    /** Set once per theme change by [LedgerAITheme]. Snapshot state, so readers recompose. */
    var dark by mutableStateOf(false)

    fun pick(light: Color, dark: Color): Color = if (this.dark) dark else light
}

// Light
private val LightPage = Color(0xFFF2F5FB)
private val LightCard = Color(0xFFFFFFFF)
private val LightCardDeep = Color(0xFFE7ECF8)
private val LightText = Color(0xFF0C1A3E)
private val LightTextMuted = Color(0xFF55638A)
private val LightPrimary = Color(0xFF17306F)
private val LightAccent = Color(0xFF2F5FE3)
private val LightLine = Color(0xFFDCE3F2)
private val LightDanger = Color(0xFFC62828)
private val LightSoft = Color(0xFFD6E2FF)

// Dark
private val DarkPage = Color(0xFF101113)
private val DarkCard = Color(0xFF1B1C1F)
private val DarkCardDeep = Color(0xFF26272B)
private val DarkText = Color(0xFFECECEF)
private val DarkTextMuted = Color(0xFFA0A1A8)
private val DarkPrimary = Color(0xFFB4C3FF)
private val DarkAccent = Color(0xFF8DA2F2)
private val DarkLine = Color(0xFF2E2F34)
private val DarkDanger = Color(0xFFFF8A80)
private val DarkSoft = Color(0xFF34363D)

val AmberHighlight: Color get() = Palette.pick(Color(0xFFF5A524), Color(0xFFFFB84D))

val PageColor: Color get() = Palette.pick(LightPage, DarkPage)
val CardColor: Color get() = Palette.pick(LightCard, DarkCard)
val CardDeepColor: Color get() = Palette.pick(LightCardDeep, DarkCardDeep)
val PrimaryColor: Color get() = Palette.pick(LightPrimary, DarkPrimary)
val OnPrimaryColor: Color get() = Palette.pick(Color.White, DarkPage)
val AccentColor: Color get() = Palette.pick(LightAccent, DarkAccent)
val LineColor: Color get() = Palette.pick(LightLine, DarkLine)
val DangerColor: Color get() = Palette.pick(LightDanger, DarkDanger)
val SoftColor: Color get() = Palette.pick(LightSoft, DarkSoft)

val Ink: Color get() = Palette.pick(LightText, DarkText)
val InkMuted: Color get() = Palette.pick(LightTextMuted, DarkTextMuted)

val IncomeGreen = Color(0xFF2E8B57)
val ExpenseRed: Color get() = DangerColor

val CategoryColors = listOf(
    Color(0xFF2F5FE3),
    Color(0xFFF5A524),
    Color(0xFF17A2B8),
    Color(0xFF7A5AF8),
    Color(0xFF2E8B57),
    Color(0xFFE5484D),
    Color(0xFF0B7285),
    Color(0xFFD6409F),
    Color(0xFF8FA3C8),
    Color(0xFF5B6B8F),
)
