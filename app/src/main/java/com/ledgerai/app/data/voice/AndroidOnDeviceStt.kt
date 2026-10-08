package com.ledgerai.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Live speech recognition through Android's SpeechRecognizer, preferring offline models.
 * Uses the dedicated on-device recognizer on API 31+ when available.
 * Every method must be called on the main thread; callbacks arrive on the main thread.
 */
class AndroidOnDeviceStt(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onFinal(text: String)
        fun onError(message: String, permission: Boolean)
    }

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var finished = false

    val isActive: Boolean get() = recognizer != null && !finished

    fun start(listener: Listener, languageTag: String = Locale.getDefault().toLanguageTag()) {
        destroy()
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        begin(listener, languageTag, onDevice)
    }

    fun stop() {
        if (!finished) recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.let { r ->
            runCatching { r.cancel() }
            runCatching { r.destroy() }
        }
        recognizer = null
        finished = true
    }

    private fun begin(listener: Listener, languageTag: String, onDevice: Boolean) {
        val r = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            listener.onError("No recognizer", permission = false)
            return
        }
        recognizer = r
        usingOnDevice = onDevice
        finished = false
        r.setRecognitionListener(Callbacks(listener, languageTag))
        r.startListening(buildIntent(languageTag))
    }

    private fun buildIntent(languageTag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private inner class Callbacks(
        private val listener: Listener,
        private val languageTag: String
    ) : RecognitionListener {
        private var lastPartial = ""

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.firstResult()
            if (text.isNotBlank()) {
                lastPartial = text
                listener.onPartial(text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (finished) return
            val text = results.firstResult().ifBlank { lastPartial }
            finished = true
            releaseRecognizer()
            if (text.isBlank()) listener.onError("Didn't catch that", permission = false)
            else listener.onFinal(text)
        }

        override fun onError(error: Int) {
            if (finished) return
            if (usingOnDevice && error in FALLBACK_ERRORS) {
                releaseRecognizer()
                begin(listener, languageTag, onDevice = false)
                return
            }
            finished = true
            releaseRecognizer()
            if (lastPartial.isNotBlank() &&
                (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                listener.onFinal(lastPartial)
                return
            }
            listener.onError(
                message = errorMessage(error),
                permission = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            )
        }
    }

    private fun releaseRecognizer() {
        recognizer?.let { r -> runCatching { r.destroy() } }
        recognizer = null
    }

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic blocked"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "Offline model missing"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        ERROR_LANGUAGE_NOT_SUPPORTED,
        ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
        else -> "Recognizer error"
    }

    private companion object {
        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31).
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        val FALLBACK_ERRORS = setOf(
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER
        )
    }
}
