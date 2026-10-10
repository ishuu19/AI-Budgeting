package com.ledgerai.app.widget

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.R
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Search from the widget. The words stay in this popup and become suggestions. */
@AndroidEntryPoint
class WidgetNoteActivity : ComponentActivity() {

    @Inject lateinit var ai: AiRepository
    @Inject lateinit var capture: VoiceCaptureRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_note_composer)
        val field = findViewById<EditText>(R.id.widget_note_field)
        field.requestFocus()
        field.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
        findViewById<android.view.View>(R.id.widget_note_scrim).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.widget_note_save).setOnClickListener {
            val text = field.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            val host = findViewById<LinearLayout>(R.id.widget_suggestions)
            lifecycleScope.launch {
                capture.recordHeard(text)
                val items = ai.parseVoiceIntents(text).getOrElse { emptyList() }
                if (items.isEmpty()) return@launch
                showWidgetSuggestions(this@WidgetNoteActivity, host, items, text, capture)
            }
        }
    }
}
