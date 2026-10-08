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
import androidx.glance.layout.Box
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
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.domain.model.Quote
import com.ledgerai.app.service.SpeakQuoteActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class VoiceTransactionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val quote = loadQuote(entryPoint)
        val voiceOnly = try {
            entryPoint.userPreferences().voiceOnlyWidget.first()
        } catch (_: Exception) {
            false
        }
        provideContent {
            WidgetContent(quote = quote, voiceOnly = voiceOnly)
        }
    }

    private fun widgetEntryPoint(context: Context): QuoteWidgetEntryPoint {
        return EntryPointAccessors.fromApplication(
            context.applicationContext,
            QuoteWidgetEntryPoint::class.java
        )
    }

    private fun loadQuote(entryPoint: QuoteWidgetEntryPoint): Quote {
        return try {
            entryPoint.quoteRepository().readWidgetQuote()
        } catch (_: Exception) {
            Quote(text = "Stay focused on what matters today.", author = "LedgerAI")
        }
    }

    @Composable
    private fun WidgetContent(quote: Quote, voiceOnly: Boolean) {
        val context = LocalContext.current
        val speakIntent = Intent(context, SpeakQuoteActivity::class.java).apply {
            putExtra(SpeakQuoteActivity.EXTRA_QUOTE_TEXT, quote.text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val micIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_VOICE, true)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(16.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "LedgerAI",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (voiceOnly) {
                    Spacer(GlanceModifier.defaultWeight())
                } else {
                    Text(
                        text = quote.text,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionStartActivity(speakIntent)),
                        style = TextStyle(
                            color = ColorProvider(IVORY),
                            fontSize = 14.sp
                        ),
                        maxLines = 2
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .background(ImageProvider(R.drawable.widget_mic_bg))
                        .clickable(actionStartActivity(micIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_mic),
                        contentDescription = "Record",
                        modifier = GlanceModifier.size(26.dp)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_VOICE = "open_voice_record"
        private val IVORY = Color(0xFFFFF9EE)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuoteWidgetEntryPoint {
    fun quoteRepository(): QuoteRepository
    fun userPreferences(): UserPreferences
}

class VoiceTransactionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceTransactionWidget()
}
