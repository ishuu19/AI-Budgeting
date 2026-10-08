package com.ledgerai.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.MainActivity
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.domain.model.CalendarEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.format.DateTimeFormatter

class CalendarEventsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            CalendarWidgetEntryPoint::class.java
        ).calendarRepository()
        val events = try {
            repo.listNextDays(3)
        } catch (_: Exception) {
            emptyList()
        }
        provideContent {
            CalendarWidgetContent(events = events)
        }
    }

    @Composable
    private fun CalendarWidgetContent(events: List<CalendarEvent>) {
        val fmt = DateTimeFormatter.ofPattern("EEE MMM d · HH:mm")
        val context = LocalContext.current
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0D3B2E)))
                .padding(12.dp)
                .clickable(actionStartActivity(openApp))
        ) {
            Text(
                "Upcoming",
                style = TextStyle(color = ColorProvider(Color(0xFFD4AF37)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            if (events.isEmpty()) {
                Text(
                    "No events in the next 3 days",
                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.8f)), fontSize = 12.sp)
                )
            } else {
                events.take(4).forEach { event ->
                    Text(
                        event.title,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        event.startAt.format(fmt),
                        style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.7f)), fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarWidgetEntryPoint {
    fun calendarRepository(): CalendarRepository
}

class CalendarEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarEventsWidget()
}
