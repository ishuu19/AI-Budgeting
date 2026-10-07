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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ledgerai.app.MainActivity
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.domain.model.Quote
import com.ledgerai.app.service.SpeakQuoteActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class VoiceTransactionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quote = loadQuote(context)
        provideContent {
            WidgetContent(quote)
        }
    }

    private fun loadQuote(context: Context): Quote {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                QuoteWidgetEntryPoint::class.java
            )
            entryPoint.quoteRepository().readWidgetQuote()
        } catch (_: Exception) {
            Quote(text = "Stay focused on what matters today.", author = "LedgerAI")
        }
    }

    @Composable
    private fun WidgetContent(quote: Quote) {
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
                .background(ColorProvider(Color(0xFF1B4332)))
                .padding(14.dp)
        ) {
            Text(
                text = "LedgerAI",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = quote.text,
                style = TextStyle(
                    color = ColorProvider(Color(0xF2FFFFFF)),
                    fontSize = 13.sp
                ),
                maxLines = 3
            )
            if (quote.author.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = quote.author,
                    style = TextStyle(
                        color = ColorProvider(Color(0xA6FFFFFF)),
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speak",
                    modifier = GlanceModifier
                        .background(ColorProvider(Color(0x33FFFFFF)))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(speakIntent)),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "Mic",
                    modifier = GlanceModifier
                        .background(ColorProvider(Color(0x47FFFFFF)))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(micIntent)),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_VOICE = "open_voice_record"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuoteWidgetEntryPoint {
    fun quoteRepository(): QuoteRepository
}

class VoiceTransactionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceTransactionWidget()
}
