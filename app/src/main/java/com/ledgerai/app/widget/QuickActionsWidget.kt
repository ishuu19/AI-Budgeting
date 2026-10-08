package com.ledgerai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width

class QuickActionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val gaps = runCatching {
            WidgetStateBuilder.load(context).logGaps
        }.getOrDefault(0)
        provideContent { QuickActionsContent(gaps > 0) }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

@Composable
private fun QuickActionsContent(hasLogGap: Boolean) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetRoundAction(
                android.R.drawable.ic_btn_speak_now,
                palette,
                actionStartActivity(WidgetActions.voiceCapture(context))
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetRoundAction(
                android.R.drawable.ic_input_add,
                palette,
                actionStartActivity(WidgetActions.openSpendToday(context))
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetRoundAction(
                android.R.drawable.ic_menu_edit,
                palette,
                actionStartActivity(WidgetActions.openTasks(context))
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetRoundAction(
                android.R.drawable.ic_media_play,
                palette,
                actionStartActivity(WidgetActions.openFocusScreen(context, 0L, "Focus"))
            )
            if (hasLogGap) {
                Spacer(GlanceModifier.width(8.dp))
                WidgetRoundAction(
                    android.R.drawable.ic_menu_my_calendar,
                    palette,
                    actionStartActivity(WidgetActions.openLifeLog(context))
                )
            }
        }
    }
}
