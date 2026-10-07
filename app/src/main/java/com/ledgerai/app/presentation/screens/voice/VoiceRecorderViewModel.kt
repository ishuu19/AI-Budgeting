package com.ledgerai.app.presentation.screens.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.remote.WhisperService
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class VoiceRecorderState {
    IDLE,           // Waiting for user to tap
    RECORDING,      // MediaRecorder capturing audio
    TRANSCRIBING,   // Sending audio to Whisper AI
    PARSING,        // Claude parsing transcript → transaction
    RESULT,         // Showing editable parsed result
    SAVED,          // Saved to MongoDB, showing success
    ERROR           // Something went wrong
}

data class VoiceUiState(
    val recorderState: VoiceRecorderState = VoiceRecorderState.IDLE,
    val recordingSeconds: Int = 0,
    val transcript: String = "",
    val parsedTransaction: ParsedTransaction? = null,
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.NONE,
    val typedInput: String = "",
    val amplitudeLevel: Float = 0f    // 0..1 for waveform animation
)

enum class ErrorType { NONE, AUDIO_UNCLEAR, NETWORK, PARSE_FAILED, PERMISSION }

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperService: WhisperService,
    private val transactionRepo: TransactionRepository,
    private val aiRepo: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    // ─── Recording ────────────────────────────────────────────────────────────

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

            _uiState.update { it.copy(
                recorderState = VoiceRecorderState.RECORDING,
                recordingSeconds = 0, errorMessage = null
            ) }

            // Recording timer
            timerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                    // Auto-stop at 60s
                    if (_uiState.value.recordingSeconds >= 60) stopRecording()
                }
            }

            // Amplitude polling for waveform animation
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
            _uiState.update { it.copy(
                recorderState = VoiceRecorderState.ERROR,
                errorType = ErrorType.PERMISSION,
                errorMessage = "Could not start recording. Please grant microphone permission."
            ) }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        timerJob = null
        amplitudeJob = null

        val recorder = mediaRecorder ?: return
        val file = audioFile ?: return

        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            recorder.release()
        }
        mediaRecorder = null

        val durationSeconds = _uiState.value.recordingSeconds
        if (durationSeconds < 1) {
            _uiState.update { it.copy(
                recorderState = VoiceRecorderState.ERROR,
                errorType = ErrorType.AUDIO_UNCLEAR,
                errorMessage = "Recording too short. Please speak for at least 1 second."
            ) }
            return
        }

        transcribeAudio(file)
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

    // ─── Whisper Transcription ─────────────────────────────────────────────────

    private fun transcribeAudio(file: File) {
        _uiState.update { it.copy(
            recorderState = VoiceRecorderState.TRANSCRIBING,
            amplitudeLevel = 0f
        ) }
        viewModelScope.launch {
            try {
                val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val audioPart = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val modelPart = "openai/whisper-large-v3".toRequestBody("text/plain".toMediaTypeOrNull())
                val langPart = "en".toRequestBody("text/plain".toMediaTypeOrNull())
                val formatPart = "json".toRequestBody("text/plain".toMediaTypeOrNull())

                val response = withContext(Dispatchers.IO) {
                    whisperService.transcribe(audioPart, modelPart, langPart, formatPart)
                }

                val transcript = response.text.trim()
                file.delete()

                if (transcript.isBlank() || transcript.length < 3) {
                    _uiState.update { it.copy(
                        recorderState = VoiceRecorderState.ERROR,
                        errorType = ErrorType.AUDIO_UNCLEAR,
                        errorMessage = "Audio was unclear or too quiet. Please speak louder and try again."
                    ) }
                } else {
                    _uiState.update { it.copy(transcript = transcript) }
                    parseTranscript(transcript)
                }

            } catch (e: Exception) {
                file.delete()
                // If Whisper is unavailable via OpenRouter, use device speech recognizer as fallback
                _uiState.update { it.copy(
                    recorderState = VoiceRecorderState.ERROR,
                    errorType = ErrorType.NETWORK,
                    errorMessage = "Transcription failed: ${e.message?.take(80)}. Check your connection and try again."
                ) }
            }
        }
    }

    // ─── AI Parsing ───────────────────────────────────────────────────────────

    private fun parseTranscript(transcript: String) {
        _uiState.update { it.copy(recorderState = VoiceRecorderState.PARSING) }
        viewModelScope.launch {
            aiRepo.parseVoiceTransaction(transcript).fold(
                onSuccess = { parsed ->
                    if (parsed.amount == null || parsed.amount <= 0.0) {
                        // AI couldn't find an amount — show error with transcript pre-filled
                        _uiState.update { it.copy(
                            recorderState = VoiceRecorderState.ERROR,
                            errorType = ErrorType.PARSE_FAILED,
                            errorMessage = "Couldn't detect an amount in: \"$transcript\"\nPlease retry or use the text field."
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            recorderState = VoiceRecorderState.RESULT,
                            parsedTransaction = parsed
                        ) }
                    }
                },
                onFailure = {
                    // Fallback: quick regex parse
                    val fallback = quickParse(transcript)
                    _uiState.update { it.copy(
                        recorderState = VoiceRecorderState.RESULT,
                        parsedTransaction = fallback
                    ) }
                }
            )
        }
    }

    // ─── Text input path ─────────────────────────────────────────────────────

    fun updateTypedInput(text: String) = _uiState.update { it.copy(typedInput = text) }

    fun parseTypedInput() {
        val input = _uiState.value.typedInput.trim()
        if (input.isBlank()) return
        _uiState.update { it.copy(transcript = input) }
        parseTranscript(input)
    }

    // ─── Save to MongoDB ─────────────────────────────────────────────────────

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
                    amount = amount, type = type, category = category,
                    merchant = merchant, note = note, date = date,
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
        _uiState.update { it.copy(
            recorderState = VoiceRecorderState.ERROR,
            errorType = ErrorType.PERMISSION,
            errorMessage = "Microphone permission is required to record voice transactions. Please grant it in Settings."
        ) }
    }

    // ─── Fallback parse ───────────────────────────────────────────────────────

    private fun quickParse(input: String): ParsedTransaction {
        val amountRegex = Regex("""[$£€]?\s*(\d+(?:[.,]\d{1,2})?)""")
        val amount = amountRegex.find(input)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()
        val lower = input.lowercase()
        val category = when {
            lower.containsAny("food", "lunch", "dinner", "breakfast", "coffee",
                "restaurant", "grocery", "groceries", "eat", "meal") -> TransactionCategory.FOOD
            lower.containsAny("uber", "lyft", "gas", "fuel", "taxi", "bus", "train",
                "transport", "metro", "fare") -> TransactionCategory.TRANSPORT
            lower.containsAny("netflix", "spotify", "subscription", "hulu",
                "disney", "apple tv", "prime") -> TransactionCategory.SUBSCRIPTIONS
            lower.containsAny("movie", "game", "concert", "entertainment",
                "cinema", "theatre") -> TransactionCategory.ENTERTAINMENT
            lower.containsAny("amazon", "shopping", "clothes", "shoes",
                "store", "mall", "buy") -> TransactionCategory.SHOPPING
            lower.containsAny("doctor", "pharmacy", "gym", "health",
                "medicine", "hospital") -> TransactionCategory.HEALTH
            lower.containsAny("electric", "water", "internet", "utility",
                "bill", "wifi") -> TransactionCategory.UTILITIES
            lower.containsAny("rent", "mortgage", "housing") -> TransactionCategory.RENT
            lower.containsAny("salary", "paycheck", "income", "paid me",
                "received", "earned") -> TransactionCategory.SALARY
            else -> TransactionCategory.OTHER
        }
        val type = if (category == TransactionCategory.SALARY ||
            lower.containsAny("received", "earned", "income", "got paid", "deposit"))
            TransactionType.INCOME else TransactionType.EXPENSE

        val merchantCandidates = input.split(" ").filter { w ->
            w.length > 3 && w[0].isUpperCase() && !w.matches(Regex("\\d.*"))
        }
        val merchant = merchantCandidates.lastOrNull() ?: ""

        return ParsedTransaction(
            amount = amount, category = category, merchant = merchant,
            date = LocalDate.now(), note = input, type = type
        )
    }

    override fun onCleared() {
        mediaRecorder?.apply { runCatching { stop(); release() } }
        audioFile?.delete()
        super.onCleared()
    }
}

private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
