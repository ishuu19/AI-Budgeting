package com.ledgerai.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.R

data class WidgetPalette(
    val canvasBg: Int,
    val tileBg: Int,
    val tileRaised: Int,
    val onTile: ColorProvider,
    val onTileMuted: ColorProvider,
    val onCanvas: ColorProvider,
    val gold: ColorProvider,
    val danger: ColorProvider,
    val success: ColorProvider,
    val amber: ColorProvider,
)

object WidgetTheme {
    val canvasPadding = 10.dp
    val tileGap = 8.dp
    val tilePadding = 12.dp
    val tileRadius = 18.dp
    val heroSize = 28.sp
    val labelSize = 11.sp
    val bodySize = 13.sp
    val actionSize = 44.dp

    fun palette(context: Context, forceDark: Boolean? = null): WidgetPalette {
        val night = forceDark ?: isNight(context)
        return if (night) {
            WidgetPalette(
                canvasBg = R.drawable.widget_canvas_dark,
                tileBg = R.drawable.widget_tile_bg,
                tileRaised = R.drawable.widget_tile_raised,
                onTile = ColorProvider(Color(0xFFFFFDF8)),
                onTileMuted = ColorProvider(Color(0xCCD4C48A)),
                onCanvas = ColorProvider(Color(0xFFFFFDF8)),
                gold = ColorProvider(Color(0xFFD4AF37)),
                danger = ColorProvider(Color(0xFFE5645A)),
                success = ColorProvider(Color(0xFF3FBF84)),
                amber = ColorProvider(Color(0xFFE8B84A)),
            )
        } else {
            WidgetPalette(
                canvasBg = R.drawable.widget_canvas_light,
                tileBg = R.drawable.widget_tile_bg,
                tileRaised = R.drawable.widget_tile_raised,
                onTile = ColorProvider(Color(0xFFFFFDF8)),
                onTileMuted = ColorProvider(Color(0xCCD4C48A)),
                onCanvas = ColorProvider(Color(0xFF0C2F24)),
                gold = ColorProvider(Color(0xFFD4AF37)),
                danger = ColorProvider(Color(0xFFC0392B)),
                success = ColorProvider(Color(0xFF2E8B57)),
                amber = ColorProvider(Color(0xFFD4A017)),
            )
        }
    }

    private fun isNight(context: Context): Boolean {
        val ui = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return ui == Configuration.UI_MODE_NIGHT_YES
    }
}
