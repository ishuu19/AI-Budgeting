package com.ledgerai.app.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Warm paper, ink, and one ink-blue accent (docs/life-os/screens.md). Light and dark are two sets of the same tokens,
 * so a screen that only uses tokens (L.* or MaterialTheme) follows the system theme with no extra code.
 * The home-screen widgets keep their own palette in widget/WidgetTheme.kt.
 */
object Palette {
    /** Set once per theme change by [LedgerAITheme]. Snapshot state, so readers recompose. */
    var dark by mutableStateOf(false)

    fun pick(light: Color, dark: Color): Color = if (this.dark) dark else light
}

// Light
internal val LightPage = Color(0xFFFAF8F4)
internal val LightCard = Color(0xFFFAF8F4)
internal val LightCardDeep = Color(0xFFF0ECE3)
internal val LightText = Color(0xFF1C1B1A)
internal val LightTextMuted = Color(0xFF77736B)
internal val LightPrimary = Color(0xFF1C1B1A)
internal val LightAccent = Color(0xFF2F3E8F)
internal val LightLine = Color(0xFFE8E4DB)
internal val LightDanger = Color(0xFFA33B32)
internal val LightSoft = Color(0xFFE9ECF8)
internal val LightAmber = Color(0xFF2F3E8F)
internal val LightIncome = Color(0xFF2E6B4B)

// Dark
internal val DarkPage = Color(0xFF121214)
internal val DarkCard = Color(0xFF121214)
internal val DarkCardDeep = Color(0xFF1C1C20)
internal val DarkText = Color(0xFFECE9E2)
internal val DarkTextMuted = Color(0xFF9A968E)
internal val DarkPrimary = Color(0xFFECE9E2)
internal val DarkAccent = Color(0xFFAAB6F4)
internal val DarkLine = Color(0xFF26252A)
internal val DarkDanger = Color(0xFFF09A92)
internal val DarkSoft = Color(0xFF1E2132)
internal val DarkAmber = Color(0xFFAAB6F4)
internal val DarkIncome = Color(0xFF86C9A4)

/** Text on the accent fill in light theme. Dark theme uses the page color. */
internal val OnHighlightNavy = Color(0xFFFFFFFF)

val AmberHighlight: Color get() = Palette.pick(LightAmber, DarkAmber)

val PageColor: Color get() = Palette.pick(LightPage, DarkPage)
val CardColor: Color get() = Palette.pick(LightCard, DarkCard)
val CardDeepColor: Color get() = Palette.pick(LightCardDeep, DarkCardDeep)
val PrimaryColor: Color get() = Palette.pick(LightPrimary, DarkPrimary)
val OnPrimaryColor: Color get() = Palette.pick(Color.White, DarkPage)
val AccentColor: Color get() = Palette.pick(LightAccent, DarkAccent)
val OnAccentColor: Color get() = Palette.pick(Color.White, DarkPage)
val OnHighlightColor: Color get() = Palette.pick(OnHighlightNavy, DarkPage)
val LineColor: Color get() = Palette.pick(LightLine, DarkLine)
val DangerColor: Color get() = Palette.pick(LightDanger, DarkDanger)
val SoftColor: Color get() = Palette.pick(LightSoft, DarkSoft)
val IncomeColor: Color get() = Palette.pick(LightIncome, DarkIncome)

val Ink: Color get() = Palette.pick(LightText, DarkText)
val InkMuted: Color get() = Palette.pick(LightTextMuted, DarkTextMuted)

val IncomeGreen: Color get() = IncomeColor
val ExpenseRed: Color get() = DangerColor

private val LightCategories = listOf(
    Color(0xFF2F5FE3),
    Color(0xFFF5A524),
    Color(0xFF17A2B8),
    Color(0xFF7A5AF8),
    Color(0xFF2E8B57),
    Color(0xFFE5484D),
    Color(0xFF0B7285),
    Color(0xFFD6409F),
    Color(0xFF5C6B8A),
    Color(0xFF3D4F73),
)

private val DarkCategories = listOf(
    Color(0xFF8DA2F2),
    Color(0xFFFFB84D),
    Color(0xFF5CDBE8),
    Color(0xFFB59CFF),
    Color(0xFF7DCEA0),
    Color(0xFFFF8A80),
    Color(0xFF5ED4E6),
    Color(0xFFFF8AD4),
    Color(0xFFB7C3DC),
    Color(0xFF9AABCC),
)

/** One fixed colour per category index. Light and dark are paired, so a theme switch updates charts. */
val CategoryColors: List<Color> get() = if (Palette.dark) DarkCategories else LightCategories

fun categoryColor(index: Int): Color {
    val colors = CategoryColors
    val i = index.mod(colors.size)
    return colors[if (i < 0) i + colors.size else i]
}
