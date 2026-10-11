package com.ledgerai.app.presentation.screens.capture

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.capture.AskResult
import com.ledgerai.app.data.capture.AskService
import com.ledgerai.app.data.capture.CaptureEngine
import com.ledgerai.app.data.capture.CaptureFiler
import com.ledgerai.app.data.capture.Suggestion
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.voice.AndroidOnDeviceStt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** One line in the chat. Photo and answer lines render live from the database or the AI reply. */
sealed interface ChatMsg {
    val id: Long
    data class User(override val id: Long, val text: String, val images: List<Uri>) : ChatMsg
    data class Bot(override val id: Long, val text: String) : ChatMsg
    data class Photos(override val id: Long, val assetIds: List<String>) : ChatMsg
    data class Answer(override val id: Long, val answer: AskResult) : ChatMsg
}

data class CaptureUi(
    val text: String = "",
    val images: List<Uri> = emptyList(),
    val listening: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val answer: AskResult? = null,
)

sealed interface CaptureEvent {
    /** The message is not a question for the pantry or wardrobe: the voice pipeline should take it. */
    data class HandOff(val text: String) : CaptureEvent

    /** Photos were stored and queued. A floating window can close itself now. */
    data class Saved(val count: Int) : CaptureEvent
}

/**
 * One input for everything. Photos are saved and queued (works offline). Words either ride along as a
 * note on the photos, or are sent to the AI, which answers a meal or outfit question from names only,
 * or hands the text to the normal voice flow (expenses, reminders, notes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val engine: CaptureEngine,
    private val askService: AskService,
    private val media: MediaRepository,
    private val filer: CaptureFiler,
    private val session: UserSession,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val _ui = MutableStateFlow(CaptureUi())
    val ui: StateFlow<CaptureUi> = _ui.asStateFlow()

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val inbox: StateFlow<List<MediaAssetEntity>> = session.userInfo
        .map { it.userId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else media.observeRecent(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val messages: StateFlow<List<ChatMsg>> = _messages.asStateFlow()
    private var nextMsgId = 1L
    private fun post(make: (Long) -> ChatMsg) = _messages.update { it + make(nextMsgId++) }

    private var pendingCamera: Uri? = null
    private var autoSend = false
    private val stt by lazy { AndroidOnDeviceStt(app) }

    fun setText(value: String) = _ui.update { it.copy(text = value) }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(images = (it.images + uris).distinct().take(MAX_IMAGES), answer = null, notice = null) }
    }

    fun removeImage(uri: Uri) = _ui.update { it.copy(images = it.images - uri) }

    fun newCameraUri(): Uri {
        val dir = File(app.cacheDir, "capture").also { it.mkdirs() }
        val file = File.createTempFile("shot_", ".jpg", dir)
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).also { pendingCamera = it }
    }

    fun onCameraResult(saved: Boolean) {
        val uri = pendingCamera
        pendingCamera = null
        if (saved && uri != null) addImages(listOf(uri))
    }

    fun startListening(autoSend: Boolean = false) {
        this.autoSend = autoSend
        viewModelScope.launch {
            val lang = runCatching { prefs.voiceLanguage.first() }.getOrDefault("en")
            _ui.update { it.copy(listening = true, notice = null) }
            stt.start(object : AndroidOnDeviceStt.Listener {
                override fun onPartial(text: String) = _ui.update { it.copy(text = text) }
                override fun onLevel(level: Float) = Unit
                override fun onFinal(text: String) {
                    _ui.update { it.copy(listening = false, text = text.trim().ifEmpty { it.text }) }
                    stt.destroy()
                    // Voice from the big mic means "just do it": send as soon as the words are in.
                    if (autoSend && _ui.value.text.isNotBlank()) submit()
                }

                override fun onError(message: String, permission: Boolean) {
                    _ui.update { it.copy(listening = false, notice = message) }
                    stt.destroy()
                }
            }, if (lang == "bn") "bn-BD" else "en-US")
        }
    }

    fun stopListening() {
        stt.stop()
    }

    fun submit() {
        val state = _ui.value
        if (state.busy) return
        val text = state.text.trim()
        if (state.images.isEmpty() && text.isEmpty()) return
        viewModelScope.launch {
            val userId = session.userInfo.first().userId
            if (userId.isBlank()) {
                _ui.update { it.copy(notice = "Sign in to capture.") }
                return@launch
            }
            post { ChatMsg.User(it, text, state.images) }
            _ui.update { it.copy(busy = true, notice = null, answer = null, text = "", images = emptyList()) }
            if (state.images.isNotEmpty()) {
                val ids = engine.submit(userId, state.images, text)
                if (ids.isEmpty()) post { ChatMsg.Bot(it, "I couldn't read that photo. Try another one.") }
                else {
                    post { ChatMsg.Photos(it, ids) }
                    _events.send(CaptureEvent.Saved(ids.size))
                }
                _ui.update { it.copy(busy = false) }
            } else {
                when (val result = askService.ask(userId, text)) {
                    AskResult.LogIt -> {
                        _ui.update { it.copy(busy = false) }
                        _events.send(CaptureEvent.HandOff(text))
                    }
                    else -> {
                        post { ChatMsg.Answer(it, result) }
                        _ui.update { it.copy(busy = false, answer = result) }
                    }
                }
            }
        }
    }

    fun quickAsk(prompt: String) {
        _ui.update { it.copy(text = prompt) }
        submit()
    }

    fun dismissAnswer() = _ui.update { it.copy(answer = null) }

    // Inbox actions ------------------------------------------------------------------------

    fun answerName(id: String, name: String) = viewModelScope.launch { engine.answerName(id, name) }
    fun fileByHand(id: String, kind: String, name: String) = viewModelScope.launch { engine.fileByHand(id, kind, name) }
    fun accept(id: String, type: String) = viewModelScope.launch { engine.acceptSuggestion(id, type) }
    fun dismissSuggestion(id: String, type: String) = viewModelScope.launch { engine.dismissSuggestion(id, type) }
    fun retry(id: String) = viewModelScope.launch { engine.retry(id) }
    fun discard(id: String) = viewModelScope.launch { media.discard(id) }

    fun suggestionsOf(row: MediaAssetEntity): List<Suggestion> = filer.suggestionsOf(row)
    fun thumb(id: String): File = media.thumbFile(id)
    fun full(id: String): File = media.fullFile(id)

    override fun onCleared() {
        stt.destroy()
        super.onCleared()
    }

    companion object {
        const val MAX_IMAGES = 8
    }
}
