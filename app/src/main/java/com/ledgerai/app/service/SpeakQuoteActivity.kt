package com.ledgerai.app.service

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Transparent activity that speaks quote text via TTS, then finishes.
 * Launched from the Glance widget speak action.
 */
class SpeakQuoteActivity : Activity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var pendingText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingText = intent.getStringExtra(EXTRA_QUOTE_TEXT).orEmpty()
        if (pendingText.isBlank()) {
            finish()
            return
        }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            Log.w(TAG, "TTS init failed")
            finish()
            return
        }
        engine.language = Locale.getDefault()
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
        })
        engine.speak(pendingText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUOTE_TEXT = "quote_text"
        private const val UTTERANCE_ID = "ledgerai_quote"
        private const val TAG = "SpeakQuoteActivity"
    }
}
