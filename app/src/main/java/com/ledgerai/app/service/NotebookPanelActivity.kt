package com.ledgerai.app.service

import android.Manifest
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import android.speech.tts.TextToSpeech
import android.service.voice.VoiceInteractionService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.data.notebook.ScreenReader
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.voice.AndroidOnDeviceStt
import com.ledgerai.app.domain.clips.ClipRules
import com.ledgerai.app.domain.notebook.NotebookEntry
import com.ledgerai.app.domain.notebook.ScreenBlock
import com.ledgerai.app.presentation.screens.notebook.LedgerAiSheet
import com.ledgerai.app.presentation.screens.notebook.NotebookPanel
import com.ledgerai.app.presentation.screens.notebook.NotebookViewModel
import com.ledgerai.app.presentation.theme.LedgerAITheme
import com.ledgerai.app.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Memory-only screen text. Cleared when the panel reads it. */
object NotebookHandoff {
    var blocks: List<ScreenBlock> = emptyList()
    var label: String? = null
    var bitmap: Bitmap? = null
    var cantRead: Boolean = false

    fun take(): Shot {
        val shot = Shot(blocks, label, bitmap, cantRead)
        blocks = emptyList()
        label = null
        bitmap = null
        cantRead = false
        return shot
    }

    data class Shot(val blocks: List<ScreenBlock>, val label: String?, val bitmap: Bitmap?, val cantRead: Boolean)
}

@AndroidEntryPoint
class NotebookPanelActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    @Inject lateinit var prefs: UserPreferences

    private val stt by lazy { AndroidOnDeviceStt(this) }
    private var tts: TextToSpeech? = null
    private var pendingSpeech: String? = null
    private var heard by mutableStateOf("")
    private var blocks by mutableStateOf<List<ScreenBlock>>(emptyList())
    private var label by mutableStateOf<String?>(null)
    private var cantRead by mutableStateOf(false)
    private var fromOcr by mutableStateOf(false)
    private var showSetup by mutableStateOf(false)

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }

    // "Ledger AI" from the selection menu: the selected text, a message, an optional screen picture.
    private var simple by mutableStateOf(false)
    private var capturing by mutableStateOf(false)
    private var screenFile by mutableStateOf<java.io.File?>(null)
    private var screenThumb by mutableStateOf<Bitmap?>(null)
    private var screenText by mutableStateOf("")

    private val screenPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) return@registerForActivityResult
        capturing = true
        val shot = ScreenGrab.expect()
        ScreenGrabService.start(this, result.resultCode, data)
        lifecycleScope.launch {
            val file = shot.await()
            capturing = false
            if (file == null) {
                Toast.makeText(this@NotebookPanelActivity, "Couldn't capture the screen", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val full = BitmapFactory.decodeFile(file.absolutePath)
            if (full == null) return@launch
            screenFile = file
            screenThumb = Bitmap.createScaledBitmap(full, full.width / 4, full.height / 4, true)
            screenText = ScreenReader.ocr(full).joinToString("\n") { it.text }
            full.recycle()
        }
    }

    private fun addScreen() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenPermission.launch(manager.createScreenCaptureIntent())
    }

    private fun removeScreen() {
        screenFile = null
        screenThumb = null
        screenText = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val handoff = if (intent.getBooleanExtra(EXTRA_HANDOFF, false)) NotebookHandoff.take() else null
        blocks = handoff?.blocks.orEmpty()
        label = handoff?.label
        cantRead = handoff?.cantRead == true && blocks.isEmpty()
        val shot = handoff?.bitmap
        if (blocks.isEmpty() && shot != null) {
            lifecycleScope.launch {
                val read = ScreenReader.ocr(shot)
                shot.recycle()
                if (read.isNotEmpty()) {
                    blocks = read
                    fromOcr = true
                    cantRead = false
                } else {
                    cantRead = true
                }
            }
        }
        simple = intent.action == Intent.ACTION_PROCESS_TEXT
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selected = ClipRules.plain(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: "")
            if (selected.isNotBlank()) {
                blocks = listOf(ScreenBlock(selected, 0, 0))
                label = sourceLabel()
            }
        }
        if (!intent.getBooleanExtra(EXTRA_GESTURE, false) && intent.action != Intent.ACTION_PROCESS_TEXT) {
            val component = ComponentName(this, LedgerVoiceInteractionService::class.java)
            showSetup = !VoiceInteractionService.isActiveService(this, component)
        }
        if (intent.getBooleanExtra(EXTRA_LISTEN, false) || intent.getStringExtra(EXTRA_LISTEN) == "true") ensureMic()
        setContent {
            val vm: NotebookViewModel = hiltViewModel()
            val clips by vm.clipsFlow.collectAsState()
            val prompts by vm.prompts.collectAsState()
            val status by vm.status.collectAsState()
            val needsConfirm by vm.needsConfirm.collectAsState()
            val threadId by vm.openThread.collectAsState()
            val messages by (threadId?.let { vm.messages(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                .collectAsState(emptyList())
            val source = label?.let { NotebookEntry.sourceChip(it, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
            var clipboardChip by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                if (intent.getBooleanExtra(EXTRA_CLIPBOARD, false)) {
                    val text = readClipboard()
                    if (!text.isNullOrBlank()) clipboardChip = text
                }
            }
            val shownBlocks = if (clipboardChip != null && blocks.isEmpty()) {
                listOf(ScreenBlock(clipboardChip!!, 0, 0))
            } else blocks
            LedgerAITheme {
                // Invisible while the screen is read, but kept in place so the typed message survives.
                androidx.compose.foundation.layout.Box(Modifier.alpha(if (capturing) 0f else 1f)) {
                if (simple) {
                    val selectedText = shownBlocks.joinToString("\n") { it.text }
                    val chat = threadId
                    // A saved entry closes the sheet after a moment, unless it was undone.
                    LaunchedEffect(status) {
                        if (status == NotebookEntry.SAVED) {
                            kotlinx.coroutines.delay(2_500)
                            if (vm.status.value == NotebookEntry.SAVED) finish()
                        }
                    }
                    LedgerAiSheet(
                        selected = selectedText,
                        source = label,
                        screenThumb = screenThumb,
                        messages = messages,
                        online = online(),
                        status = status,
                        needsConfirm = needsConfirm,
                        utterance = heard,
                        onUtteranceConsumed = { heard = "" },
                        onAsk = { prompt, text ->
                            val seen = screenText.takeIf { it.isNotBlank() }?.let { "\n\n[Screen text]\n" + it.take(2_000) }.orEmpty()
                            if (chat == null) {
                                // The first question carries the selection (and the screen words); follow-ups reuse the chat.
                                vm.ask(
                                    NotebookEntry.body(listOf(selectedText), text) + seen, selectedText + seen, text, prompt,
                                    NotebookEntry.sourceKind(true, false, fromVoice = false, fromClipboard = false),
                                    label, source, needsConfirm, null, screenFile?.let(Uri::fromFile),
                                )
                            } else {
                                vm.reply(chat, listOfNotNull(prompt, text.takeIf { it.isNotBlank() }).joinToString(": "))
                            }
                        },
                        onSave = { note ->
                            vm.save(
                                NotebookEntry.body(listOf(selectedText), note),
                                NotebookEntry.sourceKind(true, false, fromVoice = false, fromClipboard = false),
                                label, source, null, needsConfirm, screenFile?.let(Uri::fromFile),
                            )
                        },
                        onAddScreen = ::addScreen,
                        onRemoveScreen = ::removeScreen,
                        onMic = { ensureMic() },
                        onClose = { finish() },
                        onUndo = vm::undoLast,
                        onRetry = vm::retry,
                        onCopy = { text ->
                            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                android.content.ClipData.newPlainText("ledger ai", text)
                            )
                        },
                        onSaveAnswer = vm::saveAnswer,
                        onPinAnswer = vm::pinAnswer,
                    )
                } else NotebookPanel(
                    blocks = shownBlocks,
                    source = source,
                    cantRead = cantRead && shownBlocks.isEmpty(),
                    showSetup = showSetup,
                    fromOcr = fromOcr,
                    clips = clips,
                    prompts = prompts,
                    messages = messages,
                    status = status,
                    needsConfirm = needsConfirm,
                    online = online(),
                    utterance = heard,
                    onUtteranceConsumed = { heard = "" },
                    onSave = { body, kind, prompt, window ->
                        val sourceKind = if (clipboardChip != null && blocks.isEmpty()) "clipboard" else kind
                        vm.save(body, sourceKind, label, window, prompt, needsConfirm, screenFile?.let(Uri::fromFile))
                    },
                    onAsk = { body, selection, own, prompt, kind ->
                        val sourceKind = if (clipboardChip != null && blocks.isEmpty()) "clipboard" else kind
                        // The AI gets the words on the screen picture as extra context, never the picture itself.
                        val seen = screenText.takeIf { it.isNotBlank() }?.let { "\n\n[Screen text]\n" + it.take(2_000) }.orEmpty()
                        vm.ask(
                            body + seen, selection + seen, own, prompt, sourceKind, label, source, needsConfirm, threadId,
                            screenFile?.let(Uri::fromFile),
                        )
                    },
                    onMic = { ensureMic() },
                    onLog = {
                        startActivity(QuickCaptureActivity.intent(this@NotebookPanelActivity, QuickCaptureActivity.MODE_VOICE))
                        finish()
                    },
                    onOpenBoard = {
                        startActivity(WidgetActions.openNotebook(this@NotebookPanelActivity))
                        finish()
                    },
                    onUndo = vm::undoLast,
                    onPrompts = vm::setPrompts,
                    onConfirm = vm::confirmAction,
                    onRetry = vm::retry,
                    onPinAnswer = vm::pinAnswer,
                    onSaveAnswer = vm::saveAnswer,
                    onCopy = { text ->
                        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                            android.content.ClipData.newPlainText("notebook", text)
                        )
                    },
                    onRead = { speak(it) },
                    onPinLast = {
                        clips.firstOrNull()?.let { vm.pin(it.id, true) }
                    },
                )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) pendingSpeech?.let { speak(it) }
    }

    override fun onDestroy() {
        stt.destroy()
        tts?.shutdown()
        blocks = emptyList()
        super.onDestroy()
    }

    private fun ensureMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        lifecycleScope.launch {
            val lang = runCatching { prefs.voiceLanguage.first() }.getOrDefault("en")
            stt.start(listener, if (lang == "bn") "bn-BD" else "en-US", silenceMs = 2_000)
        }
    }

    private val listener = object : AndroidOnDeviceStt.Listener {
        override fun onPartial(text: String) = Unit
        override fun onLevel(level: Float) = Unit
        override fun onFinal(text: String) { heard = text }
        override fun onError(message: String, permission: Boolean) = Unit
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            return
        }
        pendingSpeech = null
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "notebook")
    }

    private fun readClipboard(): String? {
        val manager = getSystemService(ClipboardManager::class.java) ?: return null
        return manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun sourceLabel(): String? {
        val pkg = referrer?.takeIf { it.scheme == "android-app" }?.host ?: return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    private fun online(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        return cm?.activeNetwork != null
    }

    companion object {
        const val EXTRA_LISTEN = "notebook_listen"
        const val EXTRA_CLIPBOARD = "notebook_clipboard"
        const val EXTRA_GESTURE = "notebook_gesture"
        const val EXTRA_HANDOFF = "notebook_handoff"

        fun intent(context: Context, listen: Boolean, clipboard: Boolean, gesture: Boolean, handoff: Boolean = false): Intent =
            Intent(context, NotebookPanelActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_LISTEN, listen)
                putExtra(EXTRA_CLIPBOARD, clipboard)
                putExtra(EXTRA_GESTURE, gesture)
                putExtra(EXTRA_HANDOFF, handoff)
            }
    }
}
