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
        save.setOnClickListener {
            save.isEnabled = false
            activity.lifecycleScope.launch {
                val toSave = if (intent is ParsedIntent.Unmatched || !canSave(intent)) {
                    ParsedIntent.Note(title = transcript.take(80), body = transcript, rawTranscript = transcript)
                } else {
                    intent
                }
                val saved = capture.save(toSave, transcript)
                if (saved != null) {
                    WidgetRefresh.refreshAll(activity.applicationContext)
                    save.text = "Saved"
                } else {
                    save.isEnabled = true
                    save.text = "Save"
                }
            }
        }
        host.addView(row)
    }
}
