package com.ledgerai.app.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.widget.WidgetRefresh
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Widget mic -> record -> parse -> save calendar event -> refresh day widget.
 */
@AndroidEntryPoint
class WidgetVoiceCaptureActivity : ComponentActivity() {

    @Inject lateinit var aiRepository: AiRepository
    @Inject lateinit var voiceCapture: VoiceCaptureRepository

    private var receiver: BroadcastReceiver? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else finishWithToast("Microphone permission needed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> startRecording()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        registerResultReceiver()
        ContextCompat.startForegroundService(
            this,
            Intent(this, VoiceRecordingService::class.java).apply {
                action = VoiceRecordingService.ACTION_START_RECORDING
                putExtra(VoiceRecordingService.EXTRA_PROMPT, "Say what to add")
            }
        )
        Toast.makeText(this, "Listening…", Toast.LENGTH_SHORT).show()
    }

    private fun registerResultReceiver() {
        val filter = IntentFilter(VoiceRecordingService.ACTION_TRANSCRIPTION_RESULT)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra(VoiceRecordingService.EXTRA_TRANSCRIPTION)?.trim() ?: ""
                lifecycleScope.launch {
                    if (text.isNotEmpty()) {
                        handleTranscript(text)
                    } else {
                        finishWithToast("Didn't catch that — try again")
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    /**
     * Saves every clear item (spend, task, bill, ...) straight away, like the confirm card Save, and keeps a
     * history row for each. Words nothing recognises are kept as a note, never turned into a fake task.
     */
    private suspend fun handleTranscript(text: String) {
        val items = aiRepository.parseVoiceIntents(text).getOrNull().orEmpty()
        var saved = 0
        var firstKind = ""
        for (item in items) {
            val result = if (item is ParsedIntent.Unmatched) {
                voiceCapture.save(ParsedIntent.Note(title = text.take(40), body = text, rawTranscript = text), text)
            } else {
                voiceCapture.save(item, text)
            }
            if (result != null) {
                if (saved == 0) firstKind = result.kind.label
                saved++
            }
        }
        if (saved > 0) WidgetRefresh.refreshAll(applicationContext)
        finishWithToast(
            when {
                saved == 0 -> "Could not save. Open Voice to edit."
                saved == 1 -> "$firstKind saved"
                else -> "$saved items saved"
            }
        )
    }

    private fun finishWithToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        receiver = null
        super.onDestroy()
    }
}
