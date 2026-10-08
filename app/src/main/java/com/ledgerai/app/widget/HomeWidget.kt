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
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class HomeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = WidgetStateBuilder.load(context)
        provideContent { HomeContent(payload) }
    }
}

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}

@Composable
private fun HomeContent(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    val heroColor = when {
        payload.overGuide -> palette.danger
        payload.nearGuide -> palette.amber
        else -> palette.gold
    }
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = payload.dateHeader,
                maxLines = 1,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(WidgetActions.openCalendar(context))),
                style = TextStyle(color = palette.onCanvas, fontSize = WidgetTheme.labelSize)
            )
            Spacer(GlanceModifier.height(8.dp))
            WidgetTile(
                modifier = GlanceModifier.fillMaxWidth(),
                onClick = actionStartActivity(WidgetActions.openSpendToday(context))
            ) {
                WidgetLabel(payload.safeTodayLabel, palette)
                WidgetHero(payload.safeTodayAmount, heroColor)
                Text(
                    text = payload.spentTodayLabel,
                    maxLines = 1,
                    style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.labelSize)
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = payload.nextOneLine,
                maxLines = 1,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .clickable(actionStartActivity(WidgetActions.openCalendar(context))),
                style = TextStyle(color = palette.onCanvas, fontSize = WidgetTheme.bodySize)
            )
            if (payload.tasksDue > 0 || payload.billsDue > 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        if (payload.tasksDue > 0) "${payload.tasksDue} tasks" else null,
                        if (payload.billsDue > 0) "${payload.billsDue} bills" else null
                    ).joinToString("  ·  "),
                    maxLines = 1,
                    style = TextStyle(color = palette.onCanvas, fontSize = WidgetTheme.labelSize)
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            WidgetActionRow(palette, includeTask = false)
        }
    }
}
