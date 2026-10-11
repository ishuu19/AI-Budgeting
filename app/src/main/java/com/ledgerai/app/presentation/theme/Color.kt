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
internal val LightPage = Color(0xFFF2F5FB)
internal val LightCard = Color(0xFFFFFFFF)
internal val LightCardDeep = Color(0xFFE7ECF8)
internal val LightText = Color(0xFF0C1A3E)
internal val LightTextMuted = Color(0xFF55638A)
internal val LightPrimary = Color(0xFF17306F)
internal val LightAccent = Color(0xFF2F5FE3)
internal val LightLine = Color(0xFFDCE3F2)
internal val LightDanger = Color(0xFFC62828)
internal val LightSoft = Color(0xFFD6E2FF)
internal val LightAmber = Color(0xFFF5A524)
internal val LightIncome = Color(0xFF1F7A4D)

// Dark
internal val DarkPage = Color(0xFF101113)
internal val DarkCard = Color(0xFF1B1C1F)
internal val DarkCardDeep = Color(0xFF26272B)
internal val DarkText = Color(0xFFECECEF)
internal val DarkTextMuted = Color(0xFFA0A1A8)
internal val DarkPrimary = Color(0xFFB4C3FF)
internal val DarkAccent = Color(0xFF8DA2F2)
internal val DarkLine = Color(0xFF2E2F34)
internal val DarkDanger = Color(0xFFFF8A80)
internal val DarkSoft = Color(0xFF34363D)
internal val DarkAmber = Color(0xFFFFB84D)
internal val DarkIncome = Color(0xFF7DCEA0)

/** Navy on amber. Same in both themes so the mic label stays readable. */
internal val OnHighlightNavy = Color(0xFF0B1B45)

val AmberHighlight: Color get() = Palette.pick(LightAmber, DarkAmber)

val PageColor: Color get() = Palette.pick(LightPage, DarkPage)
val CardColor: Color get() = Palette.pick(LightCard, DarkCard)
val CardDeepColor: Color get() = Palette.pick(LightCardDeep, DarkCardDeep)
val PrimaryColor: Color get() = Palette.pick(LightPrimary, DarkPrimary)
val OnPrimaryColor: Color get() = Palette.pick(Color.White, DarkPage)
val AccentColor: Color get() = Palette.pick(LightAccent, DarkAccent)
val OnAccentColor: Color get() = Palette.pick(Color.White, DarkPage)
val OnHighlightColor: Color get() = OnHighlightNavy
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
