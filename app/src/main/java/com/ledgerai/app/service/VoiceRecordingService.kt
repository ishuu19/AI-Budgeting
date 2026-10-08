package com.ledgerai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.ledgerai.app.R
import java.io.File
import java.util.Locale

/**
 * Foreground service that handles voice recording and speech-to-text transcription.
 * On Android 13+, uses the on-device SpeechRecognizer API.
 * The transcribed text is broadcast back to the calling Activity/ViewModel.
 */
class VoiceRecordingService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "com.ledgerai.app.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.ledgerai.app.STOP_RECORDING"
        const val ACTION_TRANSCRIPTION_RESULT = "com.ledgerai.app.TRANSCRIPTION_RESULT"
        const val EXTRA_TRANSCRIPTION = "transcription"
        /** Optional short prompt for the notification, for example "Say what to add". */
        const val EXTRA_PROMPT = "prompt"
        private const val DEFAULT_PROMPT = "Say what to add"
        const val NOTIFICATION_CHANNEL_ID = "voice_recording"
        const val NOTIFICATION_ID = 1001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var prompt: String = DEFAULT_PROMPT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                prompt = intent?.getStringExtra(EXTRA_PROMPT)?.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT
                startForeground(NOTIFICATION_ID, buildNotification())
                startListening()
            }
            ACTION_STOP_RECORDING -> {
                stopListening()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    // No matches still ends the session: an empty result tells the caller to stop waiting.
                    broadcastResult(matches?.firstOrNull().orEmpty())
                    stopSelf()
                }

                override fun onError(error: Int) {
                    broadcastResult("")
                    stopSelf()
                }

                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun broadcastResult(transcription: String) {
        val broadcastIntent = Intent(ACTION_TRANSCRIPTION_RESULT).apply {
            putExtra(EXTRA_TRANSCRIPTION, transcription)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Listening")
            .setContentText(prompt)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Voice Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
