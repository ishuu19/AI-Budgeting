package com.ledgerai.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

/**
 * Lets the user pick this app as the phone's default digital assistant (Settings → Apps → Default apps).
 * The assistant gesture (long-press power or home) then opens [QuickCaptureActivity] over any screen.
 */
class LedgerVoiceInteractionService : VoiceInteractionService()

class LedgerSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LedgerSession(this)
}

private class LedgerSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = QuickCaptureActivity.intent(ctx, QuickCaptureActivity.MODE_VOICE)
        try {
            startAssistantActivity(intent)
        } catch (_: Exception) {
            ctx.startActivity(intent)
        }
        finish()
    }
}

/**
 * Required by the assistant declaration. It is not exported as a general speech recognizer
 * (no intent filter), and it always reports an error, so the app's own recognition is never affected.
 */
class LedgerRecognitionStub : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}

/** Quick-settings tile: pull down the shade, tap, and the capture sheet opens over the current app. */
class CaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = QuickCaptureActivity.intent(this, QuickCaptureActivity.MODE_VOICE)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
