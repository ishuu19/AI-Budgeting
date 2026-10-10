package com.ledgerai.app.presentation.screens.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.speech.SpeechRecognizer
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.IntentSource
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.ai.QuickParse
import com.ledgerai.app.data.ai.RoutedIntents
import com.ledgerai.app.data.ai.VoiceResultKind
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.SavedVoiceItem
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import com.ledgerai.app.data.repository.VoiceHistoryItem
import com.ledgerai.app.data.voice.AndroidOnDeviceStt
import com.ledgerai.app.data.voice.OfflineSttEngine
import com.ledgerai.app.data.voice.OfflineVoiceEngine
import com.ledgerai.app.data.voice.VoskModelManager
import com.ledgerai.app.presentation.navigation.OpenKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class VoiceRecorderState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    PARSING,
    RESULT,
    ERROR
}

enum class ErrorType {
    NONE,
    AUDIO_UNCLEAR,
    NETWORK,
    PARSE_FAILED,
    /** Microphone permission denied; asking again is possible. */
    PERMISSION,
    /** Denied with "don't ask again": only system settings can fix it. */
    PERMISSION_BLOCKED,
    /** Permission is fine but the recorder or recognizer failed. */
    RECORDER_FAILED,
    TOO_SHORT
}

/** One confirm card. [remindersEdited] false lets a new event take the standard reminders. */
data class VoiceCard(
    val key: Int,
    val intent: ParsedIntent,
    val remindersEdited: Boolean = false,
    val source: IntentSource? = null,
)

/** A history row being redone: saving a card updates that row, and optionally removes its old item. */
data class RedoContext(val history: VoiceHistoryItem, val replaceOld: Boolean)

/** Shown after a save. [undo] reverts it; [open] is where the saved item lives, when one id is known. */
class SavedNotice(
    val nonce: Long,
    val message: String,
    val open: Pair<OpenKind, Long>?,
    val undo: suspend () -> Unit
)

data class VoiceUiState(
    val recorderState: VoiceRecorderState = VoiceRecorderState.IDLE,
    val recordingSeconds: Int = 0,
    val transcript: String = "",
    val cards: List<VoiceCard> = emptyList(),
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.NONE,
    val typedInput: String = "",
    val amplitudeLevel: Float = 0f,
    /** Non-null while an offline model is downloading (0f..1f). */
    val modelDownloadProgress: Float? = null,
    /** Selected engine's display label. */
    val engineLabel: String = OfflineVoiceEngine.SHERPA.label,
    /** True when the selected engine streams live results (no audio file). */
    val liveEngine: Boolean = false,
    /** "en" or "bn". */
    val speechLang: String = "en",
    val redo: RedoContext? = null,
    val notice: SavedNotice? = null
) {
    val maxSeconds: Int get() = VoiceRecorderViewModel.MAX_SECONDS
}

fun VoiceResultKind.openKind(): OpenKind? = when (this) {
    VoiceResultKind.Spend, VoiceResultKind.Income -> OpenKind.Transaction
    VoiceResultKind.Task, VoiceResultKind.Reminder, VoiceResultKind.Event, VoiceResultKind.Exam,
    VoiceResultKind.Routine, VoiceResultKind.Alarm -> OpenKind.Event
    VoiceResultKind.Budget -> OpenKind.Budget
    VoiceResultKind.Bill -> OpenKind.Bill
    VoiceResultKind.Debt -> OpenKind.Debt
    VoiceResultKind.Goal -> OpenKind.Goal
    VoiceResultKind.Note -> OpenKind.Note
    VoiceResultKind.Job -> OpenKind.Job
    VoiceResultKind.Edit -> null
    VoiceResultKind.Unsorted -> null
}

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capture: VoiceCaptureRepository,
    private val aiRepo: AiRepository,
    private val voskModelManager: VoskModelManager,
    private val offlineEngine: OfflineSttEngine,
    private val prefs: UserPreferences
) : ViewModel() {

    companion object {
        const val MAX_SECONDS = 60
        private const val MIN_RECORDING_MS = 1000L
    }

    private var engine: OfflineVoiceEngine = OfflineVoiceEngine.SHERPA
    private var speechLang: String = "en"

    private val _uiState = MutableStateFlow(fresh())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    /** Null while loading. */
    val history: StateFlow<List<VoiceHistoryItem>?> = capture.observeHistory()
        .map<List<VoiceHistoryItem>, List<VoiceHistoryItem>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var startedAt = 0L
    private var cardKeys = 0
    private var noticeNonce = 0L

    private var liveStt: AndroidOnDeviceStt? = null
    private var liveSession = 0

    init {
        viewModelScope.launch {
            prefs.voiceEngine.collect { id ->
                val selected = OfflineVoiceEngine.fromId(id)
                engine = selected
                _uiState.update { it.copy(engineLabel = selected.label, liveEngine = selected.isLive) }
            }
        }
        viewModelScope.launch {
            prefs.voiceLanguage.collect { code ->
                speechLang = code
                _uiState.update { it.copy(speechLang = code) }
            }
        }
    }

    fun setSpeechLanguage(code: String) {
        if (_uiState.value.recorderState == VoiceRecorderState.RECORDING) return
        viewModelScope.launch { prefs.setVoiceLanguage(code) }
    }

    private fun fresh() = VoiceUiState(
        engineLabel = engine.label,
        liveEngine = engine.isLive,
        speechLang = speechLang
    )

    // --- recording ---------------------------------------------------------------------------

    private var voiceEdit: VoiceHistoryItem? = null

    /** Records over an existing history row. The transcript replaces that capture. */
    fun recordOver(item: VoiceHistoryItem) {
        voiceEdit = item
        beginRecording()
    }

    fun startRecording() {
        voiceEdit = null
        beginRecording()
    }

    private fun beginRecording() {
        if (!hasMicPermission()) {
            showError(ErrorType.PERMISSION, "Mic blocked")
            return
        }
        if (SpeechRecognizer.isRecognitionAvailable(context)) startLive()
        else showError(ErrorType.RECORDER_FAILED, "Phone speech unavailable")
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startFileRecording() {
        val file = File(context.cacheDir, "voice_capture_${System.currentTimeMillis()}.m4a")
        audioFile = file

        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder

            enterRecordingState()

            amplitudeJob = viewModelScope.launch {
                while (_uiState.value.recorderState == VoiceRecorderState.RECORDING) {
                    delay(100)
                    val max = runCatching { mediaRecorder?.maxAmplitude?.toFloat() ?: 0f }.getOrDefault(0f)
                    val normalized = (max / 32767f).coerceIn(0f, 1f)
                    _uiState.update { it.copy(amplitudeLevel = normalized) }
                }
            }
        } catch (e: Exception) {
            runCatching { recorder.release() }
            audioFile = null
            runCatching { file.delete() }
            // Permission was checked above, so this is the recorder itself (busy mic, no input device).
            showError(ErrorType.RECORDER_FAILED, "Recorder failed")
        }
    }

    private fun startLive() {
        val stt = liveStt ?: AndroidOnDeviceStt(context).also { liveStt = it }
        val session = ++liveSession
        enterRecordingState()
        stt.start(object : AndroidOnDeviceStt.Listener {
            override fun onPartial(text: String) {
                if (session != liveSession) return
                _uiState.update { it.copy(transcript = text) }
            }

            override fun onLevel(level: Float) {
                if (session != liveSession) return
                _uiState.update { it.copy(amplitudeLevel = level) }
            }

            override fun onFinal(text: String) {
                if (session != liveSession) return
                stopTimers()
                _uiState.update { it.copy(transcript = text, amplitudeLevel = 0f) }
                deliverTranscript(text)
            }

            override fun onError(message: String, permission: Boolean) {
                if (session != liveSession) return
                stopTimers()
                when {
                    permission -> showError(ErrorType.PERMISSION, "Mic blocked")
                    message.contains("catch", ignoreCase = true) -> showError(ErrorType.AUDIO_UNCLEAR, message)
                    else -> showError(ErrorType.RECORDER_FAILED, message)
                }
            }
        }, if (speechLang == "bn") "bn-BD" else "en-US")
    }

    private fun enterRecordingState() {
        startedAt = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.RECORDING,
                recordingSeconds = 0,
                transcript = "",
                cards = emptyList(),
                redo = null,
                errorMessage = null,
                errorType = ErrorType.NONE,
                amplitudeLevel = 0f,
                modelDownloadProgress = null
            )
        }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                if (_uiState.value.recordingSeconds >= MAX_SECONDS) {
                    stopRecording()
                    break
                }
            }
        }
    }

    private fun stopTimers() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        timerJob = null
        amplitudeJob = null
    }

    fun stopRecording() {
        if (_uiState.value.recorderState != VoiceRecorderState.RECORDING) return
        stopTimers()

        // A tap that is shorter than a second holds no speech: discard it, do not transcribe.
        if (SystemClock.elapsedRealtime() - startedAt < MIN_RECORDING_MS) {
            discardRecorder()
            showError(ErrorType.TOO_SHORT, "Too short")
            return
        }

        val stt = liveStt
        if (stt != null && stt.isActive) {
            _uiState.update {
                it.copy(recorderState = VoiceRecorderState.TRANSCRIBING, amplitudeLevel = 0f)
            }
            stt.stop()
            return
        }

        val recorder = mediaRecorder ?: return
        val file = audioFile ?: return

        try {
            recorder.stop()
        } catch (e: Exception) {
            // Stop can throw when no audio frame was written.
        }
        runCatching { recorder.release() }
        mediaRecorder = null

        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.TRANSCRIBING,
                amplitudeLevel = 0f,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val selected = OfflineVoiceEngine.fromId(prefs.voiceEngine.first())
            if (!voskModelManager.isModelReady()) {
                _uiState.update { it.copy(modelDownloadProgress = 0f) }
            }
            val result = offlineEngine.transcribe(file, selected) { progress ->
                _uiState.update { state ->
                    state.copy(
                        modelDownloadProgress = progress.coerceIn(0f, 1f)
                            .takeUnless { it >= 1f }
                    )
                }
            }
            runCatching { file.delete() }
            if (audioFile === file) audioFile = null

            result.fold(
                onSuccess = { transcript ->
                    _uiState.update { it.copy(transcript = transcript, modelDownloadProgress = null) }
                    deliverTranscript(transcript)
                },
                onFailure = { err ->
                    val message = err.message.orEmpty()
                    val type = when {
                        message.contains("download", ignoreCase = true) ||
                            message.contains("network", ignoreCase = true) ||
                            message.contains("HTTP", ignoreCase = true) -> ErrorType.NETWORK
                        else -> ErrorType.AUDIO_UNCLEAR
                    }
                    showError(
                        type,
                        if (type == ErrorType.NETWORK) "Download failed" else "Didn't catch that"
                    )
                }
            )
        }
    }

    /** Stops and throws away the current recording. Safe to call in any state. */
    fun cancelRecording() {
        stopTimers()
        discardRecorder()
        _uiState.update { fresh().copy(typedInput = it.typedInput) }
    }

    /** The screen left the foreground or the segment: stop the mic, keep nothing. */
    fun onLeave() {
        if (_uiState.value.recorderState == VoiceRecorderState.RECORDING) cancelRecording()
    }

    private fun discardRecorder() {
        cancelLive()
        mediaRecorder?.apply { runCatching { stop() }; runCatching { release() } }
        mediaRecorder = null
        audioFile?.let { runCatching { it.delete() } }
        audioFile = null
    }

    private fun cancelLive() {
        liveSession++
        liveStt?.destroy()
    }

    private fun deliverTranscript(text: String) {
        val edit = voiceEdit
        voiceEdit = null
        if (edit != null && text.isNotBlank()) {
            redo(
                edit,
                text,
                edit.kind.takeIf { it != VoiceResultKind.Unsorted },
                replaceOld = edit.linkedItemId != null
            )
        } else {
            parseTranscript(text)
        }
    }

    private fun showError(type: ErrorType, message: String) {
        voiceEdit = null
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.ERROR,
                errorType = type,
                errorMessage = message,
                amplitudeLevel = 0f,
                modelDownloadProgress = null
            )
        }
    }

    /** Called with the result of the permission dialog. [permanent] is true when "don't ask again" applies. */
    fun onPermissionDenied(permanent: Boolean) {
        if (permanent) showError(ErrorType.PERMISSION_BLOCKED, "Mic blocked")
        else showError(ErrorType.PERMISSION, "Mic blocked")
    }

    // --- parsing -----------------------------------------------------------------------------

    /** Shared entry for live, file, typed and redone transcripts. [kind] forces the result kind. */
    private fun parseTranscript(transcript: String, kind: VoiceResultKind? = null) {
        val text = transcript.trim()
        if (text.isEmpty()) {
            showError(ErrorType.AUDIO_UNCLEAR, "Didn't catch that")
            return
        }
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.PARSING,
                transcript = text,
                modelDownloadProgress = null,
                cards = emptyList()
            )
        }
        viewModelScope.launch {
            capture.recordHeard(text)
            val parsed = if (kind != null) {
                Result.success(RoutedIntents(listOf(QuickParse.asKind(kind, text)), IntentSource.RULES, cloudCalled = false))
            } else {
                aiRepo.parseVoiceIntentsRouted(text)
            }
            parsed.fold(
                onSuccess = { routed ->
                    val cards = routed.items.map { VoiceCard(cardKeys++, it, source = routed.source.takeIf { _ -> it !is ParsedIntent.Unmatched }) }
                    _uiState.update { it.copy(recorderState = VoiceRecorderState.RESULT, cards = cards) }
                },
                onFailure = { showError(ErrorType.PARSE_FAILED, "Didn't catch that") }
            )
        }
    }

    fun updateTypedInput(text: String) = _uiState.update { it.copy(typedInput = text) }

    /** Words from the widget. Shows the suggestion cards; nothing is saved until the user confirms. */
    fun offerText(text: String) {
        _uiState.update { it.copy(typedInput = "", redo = null) }
        parseTranscript(text)
    }

    fun parseTypedInput() {
        val input = _uiState.value.typedInput.trim()
        if (input.isBlank()) return
        _uiState.update { it.copy(typedInput = "", redo = null) }
        parseTranscript(input)
    }

    /** Re-reads [text] (edited by the user), optionally forcing a result kind. Used by the unmatched card. */
    fun reparse(text: String, kind: VoiceResultKind?) {
        _uiState.update { it.copy(redo = null) }
        parseTranscript(text, kind)
    }

    /**
     * Redo a history row: parse [transcript] again and show confirm cards.
     * Saving a card then updates that row; with [replaceOld] the item it created before is removed.
     */
    fun redo(item: VoiceHistoryItem, transcript: String, kind: VoiceResultKind?, replaceOld: Boolean) {
        parseTranscript(transcript, kind)
        _uiState.update { it.copy(redo = RedoContext(item, replaceOld && item.linkedItemId != null)) }
    }

    // --- cards -------------------------------------------------------------------------------

    fun updateCard(key: Int, intent: ParsedIntent, remindersEdited: Boolean? = null) {
        _uiState.update { state ->
            state.copy(cards = state.cards.map {
                if (it.key == key) it.copy(intent = intent, remindersEdited = remindersEdited ?: it.remindersEdited) else it
            })
        }
    }

    fun discardCard(key: Int) {
        val card = _uiState.value.cards.firstOrNull { it.key == key } ?: return
        val unmatched = card.intent as? ParsedIntent.Unmatched
        if (unmatched != null) {
            viewModelScope.launch { capture.recordUnsorted(unmatched.rawTranscript, _uiState.value.redo?.history) }
        }
        removeCard(key)
    }

    /** Throws away every card and goes back to idle. */
    fun discardAll() {
        _uiState.value.cards.filter { it.intent is ParsedIntent.Unmatched }.forEach { discardCard(it.key) }
        _uiState.update { fresh().copy(typedInput = it.typedInput) }
    }

    private fun removeCard(key: Int) {
        _uiState.update { state ->
            val left = state.cards.filterNot { it.key == key }
            if (left.isEmpty()) fresh().copy(typedInput = state.typedInput, notice = state.notice)
            else state.copy(cards = left)
        }
    }

    /** Saves one card. An unmatched card is kept as a note. */
    fun saveCard(key: Int) {
        val card = _uiState.value.cards.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            val saved = saveOne(card, _uiState.value.transcript)
            if (saved != null) {
                removeCard(key)
                publish(listOf(saved))
            }
        }
    }

    /** Saves every card that can be saved; the rest stay. */
    fun saveAll() {
        val state = _uiState.value
        viewModelScope.launch {
            val saved = mutableListOf<SavedVoiceItem>()
            for (card in state.cards) {
                val result = saveOne(card, state.transcript)
                if (result != null) {
                    saved += result
                    removeCard(card.key)
                }
            }
            if (saved.isNotEmpty()) publish(saved)
        }
    }

    private suspend fun saveOne(card: VoiceCard, transcript: String): SavedVoiceItem? {
        val redo = _uiState.value.redo
        val intent = card.intent.let {
            if (it is ParsedIntent.Unmatched) {
                ParsedIntent.Note(title = noteTitle(it.rawTranscript), body = it.rawTranscript, rawTranscript = it.rawTranscript)
            } else it
        }
        val text = card.intent.rawTranscript.ifBlank { transcript }
        val saved = capture.save(intent, text.ifBlank { transcript }, redo?.history, card.remindersEdited)
        if (saved != null && redo != null) {
            if (redo.replaceOld) redo.history.linkedItemId?.let { capture.deleteLinked(redo.history.kind, it) }
            // The first saved card takes over the row; further cards add their own rows.
            _uiState.update { it.copy(redo = null) }
        }
        return saved
    }

    private fun noteTitle(text: String) = text.trim().split(' ').take(6).joinToString(" ").ifBlank { "Note" }

    private fun publish(saved: List<SavedVoiceItem>) {
        val first = saved.first()
        val message = if (saved.size == 1) {
            "${first.kind.label} saved"
        } else {
            "${saved.size} items saved"
        }
        val open = if (saved.size == 1) first.kind.openKind()?.let { it to first.itemId } else null
        _uiState.update {
            it.copy(
                notice = SavedNotice(
                    nonce = ++noticeNonce,
                    message = message,
                    open = open,
                    undo = { saved.asReversed().forEach { s -> s.undo() } }
                )
            )
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun undoNotice() {
        val notice = _uiState.value.notice ?: return
        _uiState.update { it.copy(notice = null) }
        viewModelScope.launch { notice.undo() }
    }

    // --- history -----------------------------------------------------------------------------

    fun updateHistory(item: VoiceHistoryItem, transcript: String, kind: VoiceResultKind) {
        viewModelScope.launch { capture.updateHistory(item, transcript, kind) }
    }

    fun deleteHistory(item: VoiceHistoryItem, deleteItem: Boolean) {
        viewModelScope.launch { capture.deleteHistory(item, deleteItem) }
    }

    // --- misc --------------------------------------------------------------------------------

    fun reset() = _uiState.update { fresh().copy(typedInput = it.typedInput) }

    /** Back to a clean idle state before recording again. */
    fun retryRecording() {
        stopTimers()
        discardRecorder()
        _uiState.update { fresh().copy(typedInput = it.typedInput) }
    }

    override fun onCleared() {
        stopTimers()
        cancelLive()
        liveStt = null
        mediaRecorder?.apply { runCatching { stop() }; runCatching { release() } }
        audioFile?.let { runCatching { it.delete() } }
        super.onCleared()
    }
}
