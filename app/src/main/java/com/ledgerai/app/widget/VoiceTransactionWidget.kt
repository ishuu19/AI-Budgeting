package com.ledgerai.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.MainActivity
import com.ledgerai.app.service.VoiceRecordingService

class VoiceTransactionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF6750A4)))
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic icon area
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(ColorProvider(Color.White.copy(alpha = 0.2f))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎤",
                        style = TextStyle(fontSize = 24.sp)
                    )
                }

                Spacer(GlanceModifier.width(12.dp))

                Column {
                    Text(
                        text = "BudgetAI",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Tap to record transaction",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

class VoiceTransactionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceTransactionWidget()
}
