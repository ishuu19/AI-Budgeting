package com.ledgerai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.R

class HomeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = WidgetStateBuilder.load(context)
        provideContent { HomeContent(payload) }
    }
}

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}

private val Ink = ColorProvider(Color(0xFFF2F6F3))
private val Muted = ColorProvider(Color(0xFFB9CFC4))
private val Mint = ColorProvider(Color(0xFF3FE0A0))
private val Line = ColorProvider(Color(0x73203228))

@Composable
private fun HomeContent(payload: WidgetPayload) {
    val context = LocalContext.current
    val palette = WidgetTheme.palette(context, true)
    val openDay = actionStartActivity(WidgetActions.openCalendar(context))
    WidgetCanvas(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetRoundAction(
                            R.drawable.widget_ic_mic,
                            palette,
                            actionStartActivity(WidgetActions.voiceCapture(context)),
                            iconSize = 26.dp,
                            backgroundRes = R.drawable.widget_btn_mic
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetRoundAction(
                            R.drawable.widget_ic_plus,
                            palette,
                            actionStartActivity(WidgetActions.openAddTransaction(context)),
                            iconSize = 26.dp,
                            backgroundRes = R.drawable.widget_btn_plus
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetRoundAction(
                            R.drawable.widget_ic_search,
                            palette,
                            actionStartActivity(WidgetActions.openNoteComposer(context)),
                            iconSize = 26.dp,
                            backgroundRes = R.drawable.widget_btn_mic
                        )
                    }
                    Spacer(GlanceModifier.height(16.dp))
                    Column(modifier = GlanceModifier.fillMaxWidth().clickable(openDay)) {
                        Text(
                            text = "TODAY",
                            maxLines = 1,
                            style = TextStyle(color = Muted, fontSize = 12.sp)
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = payload.dateHeader,
                            maxLines = 2,
                            style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_spent_bg))
                        .clickable(actionStartActivity(WidgetActions.openSpendToday(context)))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "Today",
                            maxLines = 1,
                            style = TextStyle(color = Muted, fontSize = 11.sp)
                        )
                        Text(
                            text = payload.spentTodayAmount,
                            maxLines = 1,
                            style = TextStyle(color = Mint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            text = "This month",
                            maxLines = 1,
                            style = TextStyle(color = Muted, fontSize = 11.sp)
                        )
                        Text(
                            text = payload.spentMonthAmount,
                            maxLines = 1,
                            style = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(ImageProvider(R.drawable.widget_schedule_bg))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (payload.nextUp.isEmpty()) {
                    Text(
                        text = "Nothing on today",
                        maxLines = 1,
                        modifier = GlanceModifier.padding(vertical = 8.dp).clickable(openDay),
                        style = TextStyle(color = ColorProvider(Color(0xFF143228)), fontSize = 14.sp)
                    )
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(payload.nextUp, itemId = { it.id }) { item ->
                            Column(modifier = GlanceModifier.fillMaxWidth().clickable(openDay)) {
                                if (item.id > 0L) {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Line)
                                    ) {}
                                }
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.time,
                                        maxLines = 1,
                                        modifier = GlanceModifier.width(78.dp),
                                        style = TextStyle(color = ColorProvider(Color(0xFFE4EFE8)), fontSize = 13.sp)
                                    )
                                    Text(
                                        text = item.title,
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight(),
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xFF143228)),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
