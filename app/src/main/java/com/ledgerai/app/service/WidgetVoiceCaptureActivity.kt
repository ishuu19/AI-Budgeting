package com.ledgerai.app.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.R
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.domain.widget.widgetMicFromRecognizer
import com.ledgerai.app.widget.runWidgetAi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Widget mic. Opens the phone's built-in speech mic, then categorizes what was said. */
@AndroidEntryPoint
class WidgetVoiceCaptureActivity : FragmentActivity() {

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var ai: AiRepository
    @Inject lateinit var capture: VoiceCaptureRepository

    private var startedMic = false

    private val systemMic = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = widgetMicFromRecognizer(
            result.resultCode == RESULT_OK,
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
        )
        if (spoken == null) {
            finish()
            return@registerForActivityResult
        }
        showSuggestions(spoken)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.ledgerai.app.data.security.AppLock.holdIfLocked(this)) return
        if (savedInstanceState?.getBoolean(KEY_STARTED) == true) return
        openPhoneMic()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_STARTED, startedMic)
        super.onSaveInstanceState(outState)
    }

    private fun openPhoneMic() {
        lifecycleScope.launch {
            val lang = runCatching { prefs.voiceLanguage.first() }.getOrDefault("en")
            val tag = if (lang == "bn") "bn-BD" else "en-US"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            if (intent.resolveActivity(packageManager) == null) {
                finishWithToast("No microphone on this phone")
                return@launch
            }
            startedMic = true
            systemMic.launch(intent)
        }
    }

    private fun showSuggestions(spoken: String) {
        setContentView(R.layout.widget_listen)
        findViewById<TextView>(R.id.widget_listen_title).text = "Heard"
        findViewById<TextView>(R.id.widget_listen_heard).text = spoken
        findViewById<android.view.View>(R.id.widget_listen_scrim).setOnClickListener { finish() }
        val host = findViewById<LinearLayout>(R.id.widget_suggestions)
        lifecycleScope.launch {
            runWidgetAi(this@WidgetVoiceCaptureActivity, host, spoken, ai, capture)
        }
    }

    private fun finishWithToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val KEY_STARTED = "widget_mic_started"
    }
}
