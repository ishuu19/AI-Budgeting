package com.ledgerai.app.widget

import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.domain.widget.widgetMicTranscript

/**
 * Widget mic and quick note: categorize the words and show suggested entries.
 */
internal suspend fun runWidgetAi(
    activity: ComponentActivity,
    host: LinearLayout,
    text: String,
    ai: AiRepository,
    capture: VoiceCaptureRepository,
) {
    host.removeAllViews()
    val spoken = widgetMicTranscript(text) ?: return
    val status = TextView(activity).apply {
        this.text = "Thinking…"
        setTextColor(0xFFB9CFC4.toInt())
        textSize = 14f
    }
    host.addView(status)
    val items = ai.parseVoiceIntents(spoken).getOrElse { emptyList() }
    if (items.isEmpty()) {
        status.text = "Didn't catch that. Try again."
        return
    }
    host.removeView(status)
    showWidgetSuggestions(activity, host, items, spoken, capture)
}
