package com.ledgerai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class HomeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = WidgetStateBuilder.load(context)
        provideContent {
            HomeMedium(payload)
        }
    }
}

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}

@Composable
private fun HomeSmall(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    val heroColor = when {
        payload.overGuide -> palette.danger
        payload.nearGuide -> palette.amber
        else -> palette.gold
    }
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetTile(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                onClick = actionStartActivity(WidgetActions.openSpendToday(context))
            ) {
                WidgetLabel(payload.safeTodayLabel, palette)
                WidgetHero(payload.safeTodayAmount, heroColor)
                WidgetProgressBar(payload.guideProgress, palette, payload.overGuide, payload.nearGuide)
                Text(
                    text = payload.spentTodayLabel,
                    style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.bodySize)
                )
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Text(
                text = payload.nextOneLine,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clickable(actionStartActivity(WidgetActions.openCalendar(context))),
                style = TextStyle(color = palette.onCanvas, fontSize = WidgetTheme.bodySize)
            )
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            WidgetActionRow(palette, includeTask = false)
        }
    }
}

@Composable
private fun HomeMedium(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    val heroColor = when {
        payload.overGuide -> palette.danger
        payload.nearGuide -> palette.amber
        else -> palette.gold
    }
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = payload.dateHeader,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(WidgetActions.openCalendar(context))),
                    style = TextStyle(
                        color = palette.onCanvas,
                        fontSize = WidgetTheme.bodySize,
                        fontWeight = FontWeight.Medium
                    )
                )
                WidgetActionRow(palette)
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                WidgetTile(
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionStartActivity(WidgetActions.openSpendToday(context))
                ) {
                    WidgetLabel(payload.safeTodayLabel, palette)
                    WidgetHero(payload.safeTodayAmount, heroColor)
                    WidgetProgressBar(payload.guideProgress, palette, payload.overGuide, payload.nearGuide)
                    Text(
                        text = payload.spentTodayLabel,
                        style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.bodySize)
                    )
                }
                Spacer(GlanceModifier.width(WidgetTheme.tileGap))
                WidgetTile(
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionStartActivity(WidgetActions.openCalendar(context))
                ) {
                    WidgetLabel("Next up", palette)
                    if (payload.nextUp.isEmpty()) {
                        Text(
                            text = "Clear day",
                            style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                        )
                    } else {
                        payload.nextUp.forEach { line ->
                            Text(
                                text = "${line.time}  ${line.title}",
                                style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                            )
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (payload.tasksDue > 0) {
                    WidgetChip(
                        "${payload.tasksDue} tasks",
                        palette,
                        onClick = actionStartActivity(WidgetActions.openTasks(context))
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (payload.billsDue > 0) {
                    WidgetChip(
                        "${payload.billsDue} bills",
                        palette,
                        onClick = actionStartActivity(WidgetActions.openBills(context))
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (payload.logGaps > 0) {
                    WidgetChip(
                        "${payload.logGaps} gap",
                        palette,
                        danger = true,
                        onClick = actionStartActivity(WidgetActions.openLifeLog(context))
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeLarge(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, WidgetPrefs.forceDark(context))
    val heroColor = when {
        payload.overGuide -> palette.danger
        payload.nearGuide -> palette.amber
        else -> palette.gold
    }
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = payload.dateHeader,
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(WidgetActions.openCalendar(context))
                    ),
                    style = TextStyle(color = palette.onCanvas, fontSize = WidgetTheme.bodySize)
                )
                if (payload.quoteLine.isNotBlank()) {
                    Text(
                        text = payload.quoteLine,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        style = TextStyle(color = palette.onCanvas, fontSize = 11.sp)
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                WidgetActionRow(palette)
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetTile(
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionStartActivity(WidgetActions.openSpendToday(context))
                ) {
                    WidgetLabel(payload.safeTodayLabel, palette)
                    WidgetHero(payload.safeTodayAmount, heroColor)
                    WidgetProgressBar(payload.guideProgress, palette, payload.overGuide, payload.nearGuide)
                    Text(
                        text = payload.spentTodayLabel,
                        style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.bodySize)
                    )
                    Text(
                        text = "Month ${payload.monthBudgetPercent}%",
                        style = TextStyle(color = palette.onTileMuted, fontSize = WidgetTheme.labelSize)
                    )
                }
                Spacer(GlanceModifier.width(WidgetTheme.tileGap))
                WidgetTile(
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionStartActivity(WidgetActions.openCalendar(context))
                ) {
                    WidgetLabel("Now / Next", palette)
                    payload.nextUp.forEach { line ->
                        Text(
                            text = "${line.time}  ${line.title}",
                            style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                        )
                    }
                    if (payload.nextUp.isEmpty()) {
                        Text(
                            text = "Clear day",
                            style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                WidgetTile(modifier = GlanceModifier.defaultWeight()) {
                    WidgetLabel("Tasks", palette)
                    payload.tasksToday.forEach { t ->
                        val mark = if (t.done) "☑" else "☐"
                        Text(
                            text = "$mark ${t.title}",
                            modifier = GlanceModifier.clickable(
                                actionRunCallback<WidgetTaskToggleCallback>(
                                    actionParametersOf(
                                        WidgetTaskToggleCallback.TaskIdKey to t.id
                                    )
                                )
                            ),
                            style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                        )
                    }
                }
                Spacer(GlanceModifier.width(WidgetTheme.tileGap))
                WidgetTile(modifier = GlanceModifier.defaultWeight()) {
                    WidgetLabel("Habits", palette)
                    payload.habitsToday.forEach { h ->
                        val dot = if (h.doneToday) "●" else "○"
                        Text(
                            text = "$dot ${h.title}",
                            style = TextStyle(color = palette.onTile, fontSize = WidgetTheme.bodySize)
                        )
                    }
                    if (payload.habitStreak > 0) {
                        Text(
                            text = "Streak ${payload.habitStreak}",
                            style = TextStyle(color = palette.gold, fontSize = WidgetTheme.bodySize)
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(WidgetTheme.tileGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetChip(
                    "Jobs ${payload.jobsThisWeek}",
                    palette,
                    onClick = actionStartActivity(WidgetActions.openJobs(context))
                )
                Spacer(GlanceModifier.width(6.dp))
                if (payload.logGaps > 0) {
                    WidgetChip(
                        "Log ${payload.logGaps} gap",
                        palette,
                        danger = true,
                        onClick = actionStartActivity(WidgetActions.openLifeLog(context))
                    )
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (payload.billsDue > 0) {
                    WidgetChip(
                        "Bill ${payload.billsDue}",
                        palette,
                        onClick = actionStartActivity(WidgetActions.openBills(context))
                    )
                }
            }
        }
    }
}
