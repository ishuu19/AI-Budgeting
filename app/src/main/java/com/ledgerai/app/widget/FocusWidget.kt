package com.ledgerai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = WidgetStateBuilder.load(context)
        provideContent { FocusContent(payload) }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

@Composable
private fun FocusContent(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        WidgetTile(
            modifier = GlanceModifier.fillMaxSize(),
            raised = true,
            onClick = actionStartActivity(
                WidgetActions.openFocusScreen(context, 0L, payload.focusTitle)
            )
        ) {
            Text(
                text = payload.focusTitle,
                maxLines = 2,
                style = TextStyle(
                    color = palette.onTile,
                    fontSize = WidgetTheme.bodySize,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(8.dp))
            val countdown = payload.focusCountdown ?: "Start"
            Text(
                text = countdown,
                style = TextStyle(
                    color = palette.gold,
                    fontSize = WidgetTheme.heroSize,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Text(
                text = "Tap to open focus",
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(
                        actionStartActivity(
                            WidgetActions.openFocusScreen(context, 0L, payload.focusTitle)
                        )
                    ),
                style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.labelSize)
            )
        }
    }
}
