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
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.schedule.resolveEventDateTime
import com.ledgerai.app.widget.WidgetRefresh
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Widget mic → record → parse → save task → refresh day widget.
 */
@AndroidEntryPoint
class WidgetVoiceCaptureActivity : ComponentActivity() {

    @Inject lateinit var aiRepository: AiRepository
    @Inject lateinit var taskRepository: TaskRepository

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

    private suspend fun handleTranscript(text: String) {
        val parsed = aiRepository.parseVoiceIntent(text).getOrNull()
        val title = when (parsed) {
            is ParsedIntent.Task -> parsed.title.ifBlank { text }
            else -> text.take(120)
        }
        val notes = (parsed as? ParsedIntent.Task)?.notes ?: ""
        val due = (parsed as? ParsedIntent.Task)?.dueAt ?: resolveEventDateTime(
            date = LocalDate.now(),
            time = LocalTime.now().withSecond(0).withNano(0),
            isNew = true,
            explicitNoDate = false
        ) ?: java.time.LocalDateTime.now()
        val id = taskRepository.insert(TaskItem(title = title, notes = notes, dueAt = due))
        taskRepository.seedBeforeEventReminders(id, due)
        WidgetRefresh.refreshAll(applicationContext)
        finishWithToast("Task added")
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
