package com.ledgerai.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.MainActivity
import com.ledgerai.app.R
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.ScheduleRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.schedule.expandSlotsForDay
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.service.WidgetVoiceCaptureActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

data class WidgetDayLine(
    val time: String,
    val title: String,
    val sub: String,
    val isTask: Boolean
)

data class WidgetDayPayload(
    val dateLabel: String,
    val schedule: List<WidgetDayLine>,
    val tasks: List<WidgetDayLine>
)

class DayScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = runBlocking { loadDay(context) }
        provideContent { DayScheduleContent(payload) }
    }

    private suspend fun loadDay(context: Context): WidgetDayPayload {
        val ep = EntryPointAccessors.fromApplication(context, DayWidgetEntryPoint::class.java)
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        return try {
            val slots = ep.scheduleRepository().observeAllSlots().first()
            val classes = expandSlotsForDay(slots, today)
            val cal = ep.calendarRepository().observeMonth(today).first()
                .filter { it.startAt.toLocalDate() == today && it.kind != CalendarEventKind.TASK }
            val merged = (classes + cal).distinctBy { "${it.startAt}-${it.title}" }.sortedBy { it.startAt }
            val schedule = merged.take(10).map { e ->
                WidgetDayLine(
                    time = "${e.startAt.format(timeFmt)}",
                    title = e.title,
                    sub = e.location.ifBlank { e.kind.name.lowercase() },
                    isTask = false
                )
            }
            val tasks = ep.taskRepository().observeTasks().first()
                .filter { !it.isCompleted && it.dueAt?.toLocalDate() == today }
                .sortedBy { it.dueAt }
                .take(8)
                .map { t ->
                    WidgetDayLine(
                        time = t.dueAt?.format(timeFmt) ?: "—",
                        title = t.title,
                        sub = "Task",
                        isTask = true
                    )
                }
            WidgetDayPayload(
                dateLabel = today.format(fmt),
                schedule = schedule,
                tasks = tasks
            )
        } catch (_: Exception) {
            WidgetDayPayload(today.format(fmt), emptyList(), emptyList())
        }
    }

    @Composable
    private fun DayScheduleContent(data: WidgetDayPayload) {
        val context = LocalContext.current
        val voiceIntent = Intent(context, WidgetVoiceCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val calendarIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CALENDAR, true)
        }
        val gold = ColorProvider(Color(0xFFD4AF37))
        val ink = ColorProvider(Color(0xFFE8F5E9))
        val muted = ColorProvider(Color(0xFFB8C9BE))
        val box = ColorProvider(Color(0xFF1A5C47))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(14.dp)
                .clickable(actionStartActivity(calendarIntent))
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "Today",
                        style = TextStyle(color = muted, fontSize = 11.sp)
                    )
                    Text(
                        data.dateLabel,
                        style = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Image(
                    provider = ImageProvider(R.drawable.widget_mic),
                    contentDescription = "Add by voice",
                    modifier = GlanceModifier
                        .size(48.dp)
                        .clickable(actionStartActivity(voiceIntent))
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Text("Schedule", style = TextStyle(color = gold, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            if (data.schedule.isEmpty()) {
                Text("No classes or events today", style = TextStyle(color = muted, fontSize = 12.sp))
            } else {
                data.schedule.forEach { line -> ScheduleRow(line, ink, muted, gold, box, false) }
            }
            Spacer(GlanceModifier.height(10.dp))
            Text("Tasks", style = TextStyle(color = gold, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            if (data.tasks.isEmpty()) {
                Text("No tasks due today · tap mic to add", style = TextStyle(color = muted, fontSize = 12.sp))
            } else {
                data.tasks.forEach { line -> ScheduleRow(line, ink, muted, gold, box, true) }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "Open full calendar →",
                style = TextStyle(color = gold, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun ScheduleRow(
        line: WidgetDayLine,
        ink: ColorProvider,
        muted: ColorProvider,
        accent: ColorProvider,
        box: ColorProvider,
        isTask: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(if (isTask) ColorProvider(Color(0xFF0D3B2E)) else box)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                line.time,
                style = TextStyle(
                    color = if (isTask) accent else ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = GlanceModifier.width(44.dp)
            )
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(line.title, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                if (line.sub.isNotBlank()) {
                    Text(line.sub, style = TextStyle(color = muted, fontSize = 10.sp))
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CALENDAR = "open_calendar"
    }
}

class DayScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayScheduleWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DayWidgetEntryPoint {
    fun taskRepository(): TaskRepository
    fun scheduleRepository(): ScheduleRepository
    fun calendarRepository(): CalendarRepository
}
