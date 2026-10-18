package com.ledgerai.app.presentation.screens.notebook

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.assistant.ActionStatus
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.chat.ChatMessageEntity
import com.ledgerai.app.data.chat.ChatRepository
import com.ledgerai.app.data.chat.ChatThreadEntity
import com.ledgerai.app.data.clips.CaptureResult
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.data.clips.ClipRepository
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.notebook.NotebookActions
import com.ledgerai.app.domain.notebook.NotebookEntry
import com.ledgerai.app.domain.notebook.NotebookJson
import com.ledgerai.app.domain.notebook.NotebookPrompts
import com.ledgerai.app.worker.NotebookAskWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotebookViewModel @Inject constructor(
    private val clips: ClipRepository,
    private val chat: ChatRepository,
    private val prefs: UserPreferences,
    private val executor: Executor,
    private val media: com.ledgerai.app.data.media.MediaRepository,
    private val session: com.ledgerai.app.data.preferences.UserSession,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Screenshots kept with one entry. */
    fun screens(clipId: String) = media.observeLinked("clip", clipId)

    fun thumb(id: String) = media.thumbFile(id)
    fun full(id: String) = media.fullFile(id)

    private suspend fun attachScreen(clipId: String, screen: android.net.Uri?) {
        if (screen == null) return
        val userId = session.userInfo.first().userId
        if (userId.isNotBlank()) media.addLinked(userId, screen, "clip", clipId)
    }

    val clipsFlow: StateFlow<List<ClipEntity>> = clips.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val threads: StateFlow<List<ChatThreadEntity>> = chat.observeThreads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prompts: StateFlow<List<String>> = prefs.notebookPrompts
        .map { NotebookPrompts.decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookPrompts.BUILTIN)

    val chatKeep: StateFlow<String> = prefs.notebookChatKeep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "forever")

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _undoId = MutableStateFlow<String?>(null)
    val undoId: StateFlow<String?> = _undoId.asStateFlow()

    private val _needsConfirm = MutableStateFlow(false)
    val needsConfirm: StateFlow<Boolean> = _needsConfirm.asStateFlow()

    private val _openThread = MutableStateFlow<String?>(null)
    val openThread: StateFlow<String?> = _openThread.asStateFlow()

    fun messages(threadId: String) = chat.observeMessages(threadId)

    fun clearStatus() {
        _status.value = null
    }

    fun setPrompts(list: List<String>) {
        viewModelScope.launch { prefs.setNotebookPrompts(NotebookPrompts.encode(list)) }
    }

    fun setChatKeep(value: String) {
        viewModelScope.launch { prefs.setNotebookChatKeep(value) }
    }

    fun save(
        body: String,
        sourceKind: String,
        sourceApp: String?,
        windowTitle: String?,
        prompt: String?,
        saveAnyway: Boolean,
        screen: android.net.Uri? = null,
    ) {
        viewModelScope.launch {
            when (val result = clips.saveEntry(body, sourceKind, sourceApp, windowTitle, prompt, saveAnyway = saveAnyway)) {
                is CaptureResult.Saved -> {
                    attachScreen(result.id, screen)
                    saved(result.id)
                }
                CaptureResult.NeedsConfirm -> _needsConfirm.value = true
                CaptureResult.Password -> _status.value = "Looks like a password"
                CaptureResult.Empty -> _status.value = "Nothing to save"
            }
        }
    }

    fun ask(
        body: String,
        selection: String,
        own: String,
        prompt: String?,
        sourceKind: String,
        sourceApp: String?,
        windowTitle: String?,
        saveAnyway: Boolean,
        threadId: String?,
        screen: android.net.Uri? = null,
    ) {
        viewModelScope.launch {
            val result = clips.saveEntry(
                body, sourceKind, sourceApp, windowTitle, prompt,
                threadId = threadId, saveAnyway = saveAnyway,
            )
            when (result) {
                is CaptureResult.Saved -> {
                    attachScreen(result.id, screen)
                    val thread = threadId?.let { chat.thread(it) }
                        ?: chat.openThread(NotebookEntry.fallbackTitle(body), windowTitle ?: sourceApp)
                    clips.attachThread(result.id, thread.id)
                    val history = chat.latest(thread.id, NotebookPrompts.HISTORY)
                        .asReversed()
                        .map { it.text to it.role }
                    val payload = NotebookPrompts.userPayload(prompt, selection, own, history)
                    chat.addMessage(
                        threadId = thread.id,
                        role = "user",
                        text = payload.ifBlank { body },
                        prompt = prompt,
                        clipIds = "[\"${result.id}\"]",
                        state = "queued",
                    )
                    NotebookAskWorker.kick(context)
                    _openThread.value = thread.id
                    _needsConfirm.value = false
                }
                CaptureResult.NeedsConfirm -> _needsConfirm.value = true
                CaptureResult.Password -> _status.value = "Looks like a password"
                CaptureResult.Empty -> _status.value = "Nothing to save"
            }
        }
    }

    /** A follow-up in an open chat. The thread already holds the selection, so no new entry is saved. */
    fun reply(threadId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val history = chat.latest(threadId, NotebookPrompts.HISTORY).asReversed().map { it.text to it.role }
            chat.addMessage(
                threadId = threadId,
                role = "user",
                text = NotebookPrompts.userPayload(null, "", text.trim(), history),
                prompt = null,
                clipIds = null,
                state = "queued",
            )
            NotebookAskWorker.kick(context)
        }
    }

    fun undoLast() {
        if (_undoThread.value != null) undoThread() else undo()
    }

    fun undo() {
        val id = _undoId.value ?: return
        viewModelScope.launch {
            clips.undoCapture(id)
            _undoId.value = null
            _status.value = null
        }
    }

    fun edit(id: String, text: String) {
        viewModelScope.launch { clips.editText(id, text) }
    }

    fun pin(id: String, pinned: Boolean) {
        viewModelScope.launch { clips.setPinned(id, pinned) }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { clips.reorderPins(ids) }
    }

    fun deleteClip(id: String) {
        viewModelScope.launch { clips.deleteClip(id) }
    }

    fun addToNote(clipId: String, noteId: Long) {
        viewModelScope.launch { clips.addToNote(clipId, noteId) }
    }

    fun combine(ids: List<String>) {
        viewModelScope.launch { clips.combine(ids, null, null) }
    }

    suspend fun noteChoices() = clips.choices()

    fun closeThread() {
        _openThread.value = null
    }

    fun showThread(id: String) {
        _openThread.value = id
    }

    fun renameThread(id: String, title: String) {
        viewModelScope.launch { chat.rename(id, title) }
    }

    fun deleteThread(id: String, alsoPinned: Boolean) {
        viewModelScope.launch {
            if (alsoPinned) {
                clipsFlow.value.filter { it.pinned && it.threadId == id }.forEach { clips.deleteClip(it.id) }
            }
            val stamp = chat.deleteThread(id)
            _undoThread.value = id to stamp
            _status.value = "Deleted · Undo"
            if (_openThread.value == id) _openThread.value = null
        }
    }

    private val _undoThread = MutableStateFlow<Pair<String, Long>?>(null)

    fun undoThread() {
        val (id, stamp) = _undoThread.value ?: return
        viewModelScope.launch {
            chat.undoDeleteThread(id, stamp)
            _undoThread.value = null
            _status.value = null
        }
    }

    suspend fun threadHasPin(id: String): Boolean = chat.hasPinned(id)

    fun retry(message: ChatMessageEntity) {
        viewModelScope.launch {
            chat.updateMessage(message.copy(state = "queued", updatedAt = System.currentTimeMillis()))
            NotebookAskWorker.kick(context)
        }
    }

    fun confirmAction(message: ChatMessageEntity, index: Int) {
        if (!NotebookActions.applyNow(userTapped = true)) return
        viewModelScope.launch {
            val actions = NotebookJson.actionsFrom(message.actionsJson)
            if (index !in actions.indices) return@launch
            val plan = ActionPlan(
                inputRef = "notebook:${message.id}",
                actions = actions.map { it.copy(risk = null) },
            )
            val result = executor.execute(plan, approved = setOf(index)).getOrNull(index)
            _status.value = if (result?.status == ActionStatus.APPLIED) "Done" else "Not done"
        }
    }

    fun saveAnswer(message: ChatMessageEntity) {
        viewModelScope.launch {
            val result = clips.saveEntry(
                message.text, "ai", null, null, message.prompt,
                threadId = message.threadId, messageId = message.id,
            )
            if (result is CaptureResult.Saved) saved(result.id)
        }
    }

    fun pinAnswer(message: ChatMessageEntity) {
        viewModelScope.launch {
            val result = clips.saveEntry(
                message.text, "ai", null, null, message.prompt,
                threadId = message.threadId, messageId = message.id,
            )
            if (result is CaptureResult.Saved) clips.setPinned(result.id, true)
        }
    }

    private fun saved(id: String) {
        _needsConfirm.value = false
        _undoId.value = id
        _status.value = NotebookEntry.SAVED
    }
}
