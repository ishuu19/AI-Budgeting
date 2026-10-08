package com.ledgerai.app.presentation.screens.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.AlarmRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.RoutineRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.voice.AndroidOnDeviceStt
import com.ledgerai.app.data.voice.OfflineSttEngine
import com.ledgerai.app.data.voice.OfflineVoiceEngine
import com.ledgerai.app.data.voice.VoskModelManager
import com.ledgerai.app.domain.model.AlarmItem
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.RoutineItem
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.TaskReminder
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

enum class VoiceRecorderState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    PARSING,
    RESULT,
    SAVED,
    ERROR
}

data class VoiceUiState(
    val recorderState: VoiceRecorderState = VoiceRecorderState.IDLE,
    val recordingSeconds: Int = 0,
    val transcript: String = "",
    val parsedIntent: ParsedIntent? = null,
    val savedKind: String = "Item",
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.NONE,
    val typedInput: String = "",
    val amplitudeLevel: Float = 0f,
    /** Non-null while an offline model is downloading (0f..1f). */
    val modelDownloadProgress: Float? = null,
    /** Selected engine's display label. */
    val engineLabel: String = OfflineVoiceEngine.SHERPA.label,
    /** True when the selected engine streams live results (no audio file). */
    val liveEngine: Boolean = false
)

enum class ErrorType { NONE, AUDIO_UNCLEAR, NETWORK, PARSE_FAILED, PERMISSION }

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepo: TransactionRepository,
    private val taskRepo: TaskRepository,
    private val alarmRepo: AlarmRepository,
    private val noteRepo: NoteRepository,
    private val routineRepo: RoutineRepository,
    private val billRepo: BillRepository,
    private val debtRepo: DebtRepository,
    private val goalRepo: GoalRepository,
    private val aiRepo: AiRepository,
    private val voskModelManager: VoskModelManager,
    private val offlineEngine: OfflineSttEngine,
    private val prefs: UserPreferences
) : ViewModel() {

    private var engine: OfflineVoiceEngine = OfflineVoiceEngine.SHERPA

    private val _uiState = MutableStateFlow(fresh())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

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
    }

    private fun fresh() = VoiceUiState(engineLabel = engine.label, liveEngine = engine.isLive)

    fun startRecording() {
        if (engine.isLive) startLive() else startFileRecording()
    }

    private fun startFileRecording() {
        val file = File(context.cacheDir, "voice_transaction_${System.currentTimeMillis()}.m4a")
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
            recorder.release()
            showError(ErrorType.PERMISSION, "Mic blocked")
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
                parseTranscript(text)
            }

            override fun onError(message: String, permission: Boolean) {
                if (session != liveSession) return
                stopTimers()
                showError(if (permission) ErrorType.PERMISSION else ErrorType.AUDIO_UNCLEAR, message)
            }
        })
    }

    private fun enterRecordingState() {
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.RECORDING,
                recordingSeconds = 0,
                transcript = "",
                parsedIntent = null,
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
                if (_uiState.value.recordingSeconds >= 60) stopRecording()
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
        stopTimers()

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
            recorder.release()
        } catch (e: Exception) {
            recorder.release()
        }
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
                    parseTranscript(transcript)
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

    fun cancelRecording() {
        stopTimers()
        cancelLive()
        mediaRecorder?.apply { runCatching { stop(); release() } }
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null
        _uiState.update { fresh() }
    }

    private fun cancelLive() {
        liveSession++
        liveStt?.destroy()
    }

    private fun showError(type: ErrorType, message: String) {
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

    /** Shared entry for live, file, and typed transcripts → multi-intent parse. */
    private fun parseTranscript(transcript: String) {
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.PARSING,
                modelDownloadProgress = null,
                parsedIntent = null
            )
        }
        viewModelScope.launch {
            aiRepo.parseVoiceIntent(transcript).fold(
                onSuccess = { intent ->
                    val invalidTx = intent is ParsedIntent.Transaction &&
                        (intent.amount == null || intent.amount <= 0.0)
                    if (invalidTx) {
                        showError(ErrorType.PARSE_FAILED, "No amount")
                    } else {
                        _uiState.update {
                            it.copy(recorderState = VoiceRecorderState.RESULT, parsedIntent = intent)
                        }
                    }
                },
                onFailure = { showError(ErrorType.PARSE_FAILED, "No match") }
            )
        }
    }

    fun updateTypedInput(text: String) = _uiState.update { it.copy(typedInput = text) }

    fun parseTypedInput() {
        val input = _uiState.value.typedInput.trim()
        if (input.isBlank()) return
        _uiState.update { it.copy(transcript = input, typedInput = "") }
        parseTranscript(input)
    }

    fun confirmTransaction(
        amount: Double,
        type: TransactionType,
        category: TransactionCategory,
        merchant: String,
        note: String,
        date: LocalDate
    ) {
        viewModelScope.launch {
            transactionRepo.insert(
                Transaction(
                    amount = amount,
                    type = type,
                    category = category,
                    merchant = merchant,
                    note = note,
                    date = date,
                    createdAt = LocalDateTime.now()
                )
            )
            markSaved("Transaction")
        }
    }

    fun confirmTask(title: String, notes: String, dueAt: LocalDateTime?) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val whenAt = dueAt ?: java.time.LocalDate.now().atTime(9, 0)
            val id = taskRepo.insert(
                TaskItem(
                    title = trimmed,
                    notes = notes.trim(),
                    dueAt = whenAt
                )
            )
            taskRepo.seedBeforeEventReminders(id, whenAt)
            markSaved("Task")
        }
    }

    fun confirmReminder(title: String, label: String, remindAt: LocalDateTime) {
        val trimmed = title.trim().ifBlank { "Reminder" }
        viewModelScope.launch {
            val eventAt = java.time.LocalDateTime.of(
                java.time.LocalDate.now(),
                remindAt.toLocalTime()
            )
            val id = taskRepo.insert(
                TaskItem(
                    title = trimmed,
                    notes = "",
                    dueAt = eventAt
                )
            )
            taskRepo.seedBeforeEventReminders(id, eventAt)
            markSaved("Reminder")
        }
    }

    fun confirmAlarm(label: String, time: LocalTime, repeatDays: Int = 0) {
        viewModelScope.launch {
            alarmRepo.insert(
                AlarmItem(
                    label = label.trim().ifBlank { "Alarm" },
                    time = time,
                    isEnabled = true,
                    repeatDays = repeatDays.coerceAtLeast(0)
                )
            )
            markSaved("Alarm")
        }
    }

    fun confirmNote(title: String, body: String, tags: List<String> = emptyList()) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            noteRepo.insert(
                NoteItem(
                    title = title.trim().ifBlank { "Untitled" },
                    body = body.trim(),
                    tags = tags.map { it.trim() }.filter { it.isNotEmpty() }
                )
            )
            markSaved("Note")
        }
    }

    fun confirmRoutine(title: String, notes: String, repeatRule: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            routineRepo.insert(
                RoutineItem(
                    title = trimmed,
                    notes = notes.trim(),
                    repeatRule = repeatRule.trim().ifBlank { "DAILY" },
                    isActive = true
                )
            )
            markSaved("Routine")
        }
    }

    fun confirmBill(
        name: String,
        amount: Double,
        frequency: BillFrequency,
        nextDueDate: LocalDate,
        category: TransactionCategory
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            billRepo.insert(
                Bill(
                    name = trimmed,
                    amount = amount,
                    frequency = frequency,
                    nextDueDate = nextDueDate,
                    category = category
                )
            )
            markSaved("Bill")
        }
    }

    fun confirmDebt(
        friendName: String,
        amount: Double,
        direction: DebtDirection,
        dueDate: LocalDate?
    ) {
        val trimmed = friendName.trim()
        if (trimmed.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            debtRepo.insert(
                Debt(
                    friendName = trimmed,
                    amount = amount,
                    direction = direction,
                    dueDate = dueDate
                )
            )
            markSaved("Debt")
        }
    }

    fun confirmGoal(name: String, targetAmount: Double) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || targetAmount <= 0.0) return
        viewModelScope.launch {
            goalRepo.insert(
                Goal(
                    name = trimmed,
                    targetAmount = targetAmount
                )
            )
            markSaved("Goal")
        }
    }

    private fun markSaved(kind: String) {
        _uiState.update {
            it.copy(recorderState = VoiceRecorderState.SAVED, savedKind = kind)
        }
    }

    fun reset() = _uiState.update { fresh() }

    fun retryRecording() {
        cancelLive()
        audioFile?.delete()
        audioFile = null
        _uiState.update { fresh() }
    }

    fun onPermissionDenied() = showError(ErrorType.PERMISSION, "Mic blocked")

    override fun onCleared() {
        stopTimers()
        cancelLive()
        liveStt = null
        mediaRecorder?.apply { runCatching { stop(); release() } }
        audioFile?.delete()
        super.onCleared()
    }
}
