package com.ledgerai.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
fun WidgetCanvas(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    Box(
        modifier = modifier
            .background(ImageProvider(palette.canvasBg))
            .padding(WidgetTheme.canvasPadding),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

@Composable
fun WidgetTile(
    modifier: GlanceModifier = GlanceModifier,
    raised: Boolean = false,
    onClick: Action? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    val bg = if (raised) palette.tileRaised else palette.tileBg
    val clickMod = if (onClick != null) modifier.clickable(onClick) else modifier
    Box(
        modifier = clickMod
            .background(ImageProvider(bg))
            .padding(WidgetTheme.tilePadding),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

@Composable
fun WidgetLabel(text: String, palette: WidgetPalette) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            color = palette.onTileMuted,
            fontSize = WidgetTheme.labelSize,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
fun WidgetHero(text: String, color: ColorProvider) {
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = WidgetTheme.heroSize,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun WidgetProgressBar(
    progress: Float,
    palette: WidgetPalette,
    over: Boolean,
    near: Boolean
) {
    val fill = when {
        over -> palette.danger
        near -> palette.amber
        else -> palette.gold
    }
    val track = palette.onTileMuted
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
        val segments = 8
        val filled = (progress * segments).toInt().coerceIn(0, segments)
        repeat(segments) { i ->
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .background(if (i < filled) fill else track),
                content = {}
            )
            if (i < segments - 1) Spacer(GlanceModifier.width(2.dp))
        }
    }
}

@Composable
fun WidgetRoundAction(
    iconRes: Int,
    palette: WidgetPalette,
    onClick: Action,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    backgroundRes: Int = palette.tileRaised
) {
    Box(
        modifier = GlanceModifier
            .size(WidgetTheme.actionSize)
            .background(ImageProvider(backgroundRes))
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize)
        )
    }
}

@Composable
fun WidgetChip(
    text: String,
    palette: WidgetPalette,
    danger: Boolean = false,
    onClick: Action
) {
    val color = if (danger) palette.danger else palette.onTile
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(palette.tileBg))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(onClick)
    ) {
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = WidgetTheme.bodySize)
        )
    }
}

@Composable
fun WidgetActionRow(palette: WidgetPalette, includeTask: Boolean = true) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetRoundAction(
            android.R.drawable.ic_btn_speak_now,
            palette,
            actionStartActivity(WidgetActions.voiceCapture(context))
        )
        Spacer(GlanceModifier.width(6.dp))
        WidgetRoundAction(
            android.R.drawable.ic_input_add,
            palette,
            actionStartActivity(WidgetActions.openSpendToday(context))
        )
        if (includeTask) {
            Spacer(GlanceModifier.width(6.dp))
            WidgetRoundAction(
                android.R.drawable.ic_menu_edit,
                palette,
                actionStartActivity(WidgetActions.openTasks(context))
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        WidgetRoundAction(
            android.R.drawable.ic_media_play,
            palette,
            actionStartActivity(WidgetActions.openFocusScreen(context, 0L, "Focus"))
        )
    }
}
