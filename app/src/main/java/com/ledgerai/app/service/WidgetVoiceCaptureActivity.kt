package com.ledgerai.app.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import com.ledgerai.app.R
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.data.voice.AndroidOnDeviceStt
import com.ledgerai.app.domain.widget.widgetMicTranscript
import com.ledgerai.app.widget.runWidgetAi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Widget mic. Listens on the home screen with the same recognizer as the voice page. */
@AndroidEntryPoint
class WidgetVoiceCaptureActivity : ComponentActivity() {

    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var ai: AiRepository
    @Inject lateinit var capture: VoiceCaptureRepository

    private val stt by lazy { AndroidOnDeviceStt(this) }
    private var heard: TextView? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else finishWithToast("Microphone permission needed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_listen)
        heard = findViewById(R.id.widget_listen_heard)
        findViewById<android.view.View>(R.id.widget_listen_scrim).setOnClickListener {
            stt.destroy()
            finish()
        }
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> startListening()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        lifecycleScope.launch {
            val lang = runCatching { prefs.voiceLanguage.first() }.getOrDefault("en")
            stt.start(listener, if (lang == "bn") "bn-BD" else "en-US")
        }
    }

    private val listener = object : AndroidOnDeviceStt.Listener {
        override fun onPartial(text: String) {
            heard?.text = text
        }

        override fun onLevel(level: Float) = Unit

        override fun onFinal(text: String) {
            val spoken = widgetMicTranscript(text)
            if (spoken == null) {
                heard?.text = "Didn't catch that. Try again."
                return
            }
            heard?.text = spoken
            stt.destroy()
            lifecycleScope.launch {
                val host = findViewById<LinearLayout>(R.id.widget_suggestions)
                if (host == null) {
                    heard?.text = "Didn't catch that. Try again."
                    return@launch
                }
                runWidgetAi(this@WidgetVoiceCaptureActivity, host, spoken, ai, capture)
            }
        }

        override fun onError(message: String, permission: Boolean) {
            heard?.text = message
        }
    }

    private fun finishWithToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        stt.destroy()
        super.onDestroy()
    }
}
