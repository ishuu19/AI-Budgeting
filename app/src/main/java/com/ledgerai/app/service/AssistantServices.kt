package com.ledgerai.app.service

import android.app.PendingIntent
import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import com.ledgerai.app.data.notebook.ScreenReader
import com.ledgerai.app.domain.notebook.ScreenBlocks

/**
 * Lets the user pick this app as the phone's default digital assistant (Settings → Apps → Default apps).
 * The assistant gesture (long-press power or home) opens the Notebook panel over any screen.
 */
class LedgerVoiceInteractionService : VoiceInteractionService()

class LedgerSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LedgerSession(this)
}

private class LedgerSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var structureSeen = false
    private var shotSeen = false
    private var blocks = emptyList<com.ledgerai.app.domain.notebook.ScreenBlock>()
    private var label: String? = null
    private var bitmap: Bitmap? = null
    private val fallback = Runnable {
        if (!structureSeen && !shotSeen) open(cantRead = false, shot = null)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handler.postDelayed(fallback, 800)
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        onStructure(structure)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        shotSeen = true
        bitmap = screenshot
        if (structureSeen && blocks.isEmpty()) open(cantRead = screenshot == null, shot = screenshot)
    }

    private fun onStructure(structure: AssistStructure?) {
        structureSeen = true
        blocks = ScreenBlocks.fromNodes(ScreenReader.nodes(structure))
        label = ScreenReader.label(structure)
        if (blocks.isNotEmpty()) open(cantRead = false, shot = null)
        else if (shotSeen) open(cantRead = bitmap == null, shot = bitmap)
    }

    private fun open(cantRead: Boolean, shot: Bitmap?) {
        if (started) return
        started = true
        handler.removeCallbacks(fallback)
        NotebookHandoff.blocks = blocks
        NotebookHandoff.label = label
        NotebookHandoff.bitmap = if (blocks.isEmpty()) shot else null
        NotebookHandoff.cantRead = cantRead && blocks.isEmpty()
        val intent = NotebookPanelActivity.intent(ctx, listen = true, clipboard = false, gesture = true, handoff = true)
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
