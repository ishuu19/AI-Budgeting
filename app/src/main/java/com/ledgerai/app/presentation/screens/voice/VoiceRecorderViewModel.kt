package com.ledgerai.app.presentation.screens.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ParsedTransaction
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
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
    val parsedTransaction: ParsedTransaction? = null,
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.NONE,
    val typedInput: String = "",
    val amplitudeLevel: Float = 0f
)

enum class ErrorType { NONE, AUDIO_UNCLEAR, NETWORK, PARSE_FAILED, PERMISSION }

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepo: TransactionRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    fun startRecording() {
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

            _uiState.update {
                it.copy(
                    recorderState = VoiceRecorderState.RECORDING,
                    recordingSeconds = 0,
                    errorMessage = null
                )
            }

            timerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                    if (_uiState.value.recordingSeconds >= 60) stopRecording()
                }
            }

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
            _uiState.update {
                it.copy(
                    recorderState = VoiceRecorderState.ERROR,
                    errorType = ErrorType.PERMISSION,
                    errorMessage = "Could not start recording. Please grant microphone permission."
                )
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        timerJob = null
        amplitudeJob = null

        val recorder = mediaRecorder ?: return
        audioFile ?: return

        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            recorder.release()
        }
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null

        // Vosk offline transcription arrives in Phase 4. Until then, use the text field.
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.ERROR,
                errorType = ErrorType.NETWORK,
                amplitudeLevel = 0f,
                errorMessage = "Offline transcription (Vosk) is not installed yet. Type the transaction below for now."
            )
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        mediaRecorder?.apply { runCatching { stop(); release() } }
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null
        _uiState.update { VoiceUiState() }
    }

    private fun parseTranscript(transcript: String) {
        _uiState.update { it.copy(recorderState = VoiceRecorderState.PARSING) }
        viewModelScope.launch {
            aiRepo.parseVoiceTransaction(transcript).fold(
                onSuccess = { parsed ->
                    if (parsed.amount == null || parsed.amount <= 0.0) {
                        _uiState.update {
                            it.copy(
                                recorderState = VoiceRecorderState.ERROR,
                                errorType = ErrorType.PARSE_FAILED,
                                errorMessage = "Couldn't detect an amount in: \"$transcript\"\nPlease retry or use the text field."
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                recorderState = VoiceRecorderState.RESULT,
                                parsedTransaction = parsed
                            )
                        }
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            recorderState = VoiceRecorderState.ERROR,
                            errorType = ErrorType.PARSE_FAILED,
                            errorMessage = "Could not parse that input. Try again."
                        )
                    }
                }
            )
        }
    }

    fun updateTypedInput(text: String) = _uiState.update { it.copy(typedInput = text) }

    fun parseTypedInput() {
        val input = _uiState.value.typedInput.trim()
        if (input.isBlank()) return
        _uiState.update { it.copy(transcript = input) }
        parseTranscript(input)
    }

    fun confirmAndSave(
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
            _uiState.update { it.copy(recorderState = VoiceRecorderState.SAVED) }
        }
    }

    fun reset() = _uiState.update { VoiceUiState() }

    fun retryRecording() {
        audioFile?.delete()
        audioFile = null
        _uiState.update { VoiceUiState() }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                recorderState = VoiceRecorderState.ERROR,
                errorType = ErrorType.PERMISSION,
                errorMessage = "Microphone permission is required to record voice transactions. Please grant it in Settings."
            )
        }
    }

    override fun onCleared() {
        mediaRecorder?.apply { runCatching { stop(); release() } }
        audioFile?.delete()
        super.onCleared()
    }
}
