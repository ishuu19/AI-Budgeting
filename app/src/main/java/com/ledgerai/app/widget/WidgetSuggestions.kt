package com.ledgerai.app.widget

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.R
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.presentation.screens.voice.canSave
import com.ledgerai.app.presentation.screens.voice.summarize
import kotlinx.coroutines.launch

/** Shows the AI suggestions inside the widget popup and saves them there. */
internal fun showWidgetSuggestions(
    activity: ComponentActivity,
    host: LinearLayout,
    items: List<ParsedIntent>,
    transcript: String,
    capture: VoiceCaptureRepository,
    onAdded: () -> Unit = {},
) {
    host.removeAllViews()
    val inflater = LayoutInflater.from(activity)
    items.forEach { intent ->
        val row = inflater.inflate(R.layout.widget_suggestion_row, host, false)
        val (title, detail) = summarize(intent)
        row.findViewById<TextView>(R.id.widget_suggestion_title).text = title
        val detailView = row.findViewById<TextView>(R.id.widget_suggestion_detail)
        if (detail.isNullOrBlank()) detailView.visibility = android.view.View.GONE
        else detailView.text = detail
        val save = row.findViewById<TextView>(R.id.widget_suggestion_save)
        var taken = false
        save.setOnClickListener {
            if (taken) return@setOnClickListener
            taken = true
            save.isEnabled = false
            save.isClickable = false
            save.text = "Saved"
            host.addView(TextView(activity).apply {
                text = "Entry added"
                setTextColor(0xFFB9CFC4.toInt())
                textSize = 14f
                setPadding(0, 8, 0, 0)
            })
            onAdded()
            activity.lifecycleScope.launch {
                val note = ParsedIntent.Note(title = transcript.take(80).ifBlank { "Note" }, body = transcript, rawTranscript = transcript)
                val toSave = if (intent is ParsedIntent.Unmatched || !canSave(intent)) note else intent
                val saved = capture.save(toSave, transcript)
                    ?: if (toSave !is ParsedIntent.Note) capture.save(note, transcript) else null
                if (saved != null) WidgetRefresh.refreshAll(activity.applicationContext)
            }
        }
        host.addView(row)
    }
}
