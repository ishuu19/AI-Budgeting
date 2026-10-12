package com.ledgerai.app.presentation.screens.clips

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.clips.CaptureResult
import com.ledgerai.app.data.clips.ClipActions
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.data.clips.ClipRepository
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.clips.ClipFiling
import com.ledgerai.app.domain.clips.ClipJson
import com.ledgerai.app.domain.clips.ClipSuggestion
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.service.ClipBubbleService
import com.ledgerai.app.service.ClipPins
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddToNotesViewModel @Inject constructor(
    private val clips: ClipRepository,
    private val notes: NoteRepository,
    private val prefs: UserPreferences,
    private val session: UserSession,
    private val executor: Executor,
    private val actions: ClipActions,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val all: StateFlow<List<ClipEntity>> = clips.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notesList: StateFlow<List<NoteItem>> = notes.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val menuOn = prefs.clipMenuOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val fastMode = prefs.clipFastMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val offerCopied = prefs.clipOfferCopied.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val aiMode = prefs.clipAiMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClipFiling.SUGGEST)
    val pinned = prefs.clipPinNotification.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val retention = prefs.clipRetention.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "365")
    val bubble = prefs.clipBubble.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val stamp = prefs.clipClipboardStamp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val collectingId = prefs.clipCollectingNoteId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val subtitle: StateFlow<String> = combine(clips.observeInboxCount(), prefs.clipMenuOn, prefs.clipCollectingNoteId, notes.observeNotes()) { count, menu, collecting, noteRows ->
        val note = collecting?.let { id -> noteRows.firstOrNull { it.id == id }?.title }
        val n = if (count == 1) "1 new" else "$count new"
        when {
            note != null -> "Collecting into $note · $n"
            menu -> if (count == 0) "On" else "On · $n clips"
            else -> "Off"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "On")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _undoId = MutableStateFlow<String?>(null)
    val undoId: StateFlow<String?> = _undoId

    private val _pending = MutableStateFlow<String?>(null)
    val pendingText: StateFlow<String?> = _pending

    private val _confirm = MutableStateFlow<String?>(null)
    val confirmClipId: StateFlow<String?> = _confirm

    private val _overlay = MutableStateFlow(false)
    val askOverlay: StateFlow<Boolean> = _overlay

    init {
        viewModelScope.launch {
            combine(prefs.clipPinNotification, prefs.clipCollectingNoteId, notes.observeNotes()) { pin, id, rows ->
                Triple(pin, id, rows)
            }.collect { (pin, id, rows) ->
                if (!pin) ClipPins.hide(context)
                else ClipPins.show(context, id?.let { wanted -> rows.firstOrNull { it.id == wanted }?.title })
            }
        }
        viewModelScope.launch {
            prefs.clipBubble.collect { on ->
                if (on && Settings.canDrawOverlays(context)) ClipBubbleService.start(context)
                else ClipBubbleService.stop(context)
            }
        }
    }

    fun clearMessage() { _message.value = null }
    fun consumeOverlay() { _overlay.value = false }

    fun onCopied(text: String) {
        viewModelScope.launch {
            if (prefs.clipFastNow()) {
                val result = clips.capture(text, "clipboard", null, null, com.ledgerai.app.data.clips.ClipTarget.Default, false, false, false)
                publish(result, asked = false)
            } else {
                _pending.value = text
            }
        }
    }

    fun dismissPending() { _pending.value = null }

    fun savePending(choiceId: String, askAi: Boolean, saveAnyway: Boolean) {
        val text = _pending.value ?: return
        viewModelScope.launch {
            val result = clips.capture(
                text, "clipboard", null, null, clips.choiceTarget(choiceId), askAi, saveAnyway, false,
            )
            _pending.value = null
            publish(result, asked = askAi)
        }
    }

    fun undo() {
        val id = _undoId.value ?: return
        viewModelScope.launch {
            clips.undoCapture(id)
            _undoId.value = null
            _message.value = "Undone"
        }
    }

    fun rememberStamp(stamp: Long) {
        viewModelScope.launch { prefs.setClipClipboardStamp(stamp) }
    }

    fun swipeToNote(clip: ClipEntity) {
        viewModelScope.launch {
            val target = prefs.clipCollectingNow() ?: prefs.clipLastNoteNow()
            if (target == null) {
                _message.value = "Pick a note first"
                return@launch
            }
            clips.addToNote(clip.id, target)
            _undoId.value = null
            _message.value = "Added to note"
        }
    }

    fun dismiss(clip: ClipEntity) {
        viewModelScope.launch {
            clips.dismiss(clip.id)
            _message.value = "Dismissed"
        }
    }

    fun undoDismiss(id: String) {
        viewModelScope.launch { clips.undoDismiss(id) }
    }

    fun combine(ids: List<String>, noteId: Long?, title: String?) {
        viewModelScope.launch { clips.combine(ids, noteId, title) }
    }

    fun addSelectionTo(ids: List<String>, noteId: Long) {
        viewModelScope.launch { ids.forEach { clips.addToNote(it, noteId) } }
    }

    fun ask(ids: List<String>) {
        viewModelScope.launch { clips.askAi(ids) }
    }

    fun retry(id: String) = ask(listOf(id))

    fun edit(id: String, text: String) {
        viewModelScope.launch { clips.editText(id, text) }
    }

    fun delete(clip: ClipEntity, andFiled: Boolean) {
        viewModelScope.launch {
            if (andFiled) actions.deleteFiled(clip)
            clips.deleteClip(clip.id)
        }
    }

    fun split(clip: ClipEntity) {
        val suggestion = ClipJson.fromStored(clip.aiJson) ?: return
        viewModelScope.launch {
            val n = clips.splitPieces(suggestion.split)
            _message.value = "Split into $n notes"
        }
    }

    fun accept(clip: ClipEntity) {
        val suggestion = ClipJson.fromStored(clip.aiJson) ?: return
        val risk = ClipFiling.risk(suggestion.kind)
        if (risk == Risk.HIGH) {
            _confirm.value = clip.id
            return
        }
        file(clip, suggestion, risk)
    }

    fun confirmHigh() {
        val id = _confirm.value ?: return
        val clip = all.value.firstOrNull { it.id == id } ?: return
        val suggestion = ClipJson.fromStored(clip.aiJson) ?: return
        _confirm.value = null
        file(clip, suggestion, Risk.HIGH)
    }

    fun dismissConfirm() { _confirm.value = null }

    fun startCollecting(noteId: Long?, title: String?) {
        viewModelScope.launch {
            val id = noteId ?: notes.insert(com.ledgerai.app.domain.model.NoteItem(title = title?.trim().orEmpty().ifBlank { "Notes" }, body = ""))
            prefs.setClipCollecting(id)
        }
    }

    fun stopCollecting() {
        viewModelScope.launch { prefs.setClipCollecting(null) }
    }

    fun setMenu(on: Boolean) { viewModelScope.launch { prefs.setClipMenu(on) } }
    fun setFast(on: Boolean) { viewModelScope.launch { prefs.setClipFast(on) } }
    fun setOffer(on: Boolean) { viewModelScope.launch { prefs.setClipOfferCopied(on) } }
    fun setAi(mode: String) { viewModelScope.launch { prefs.setClipAiMode(mode) } }
    fun setPin(on: Boolean) { viewModelScope.launch { prefs.setClipPin(on) } }
    fun setRetention(value: String) { viewModelScope.launch { prefs.setClipRetention(value) } }

    fun setBubble(on: Boolean) {
        if (on && !Settings.canDrawOverlays(context)) {
            _overlay.value = true
            return
        }
        viewModelScope.launch { prefs.setClipBubble(on) }
    }

    suspend fun choices() = clips.choices()

    private fun file(clip: ClipEntity, suggestion: ClipSuggestion, risk: Risk?) {
        viewModelScope.launch {
            val userId = session.userInfo.first().userId
            val type = ClipFiling.actionType(suggestion.kind) ?: return@launch
            val plan = ActionPlan(
                inputRef = "clip:${clip.id}:file",
                actions = listOf(
                    ProposedAction(
                        type,
                        mapOf(
                            "clipId" to clip.id,
                            "userId" to userId,
                            "title" to suggestion.title,
                            "date" to suggestion.date,
                            "time" to suggestion.time,
                            "place" to suggestion.place,
                            "amount" to suggestion.amount,
                            "currency" to suggestion.currency,
                            "company" to suggestion.company,
                            "role" to suggestion.role,
                            "person" to suggestion.person,
                            "item" to (suggestion.item ?: suggestion.title),
                            "tags" to suggestion.tags,
                            "confirmed" to "true",
                        ),
                    )
                ),
            )
            val approved = if (risk == Risk.LOW) emptySet() else setOf(0)
            val result = executor.execute(plan, approved).firstOrNull()
            _message.value = result?.message ?: "Could not file that"
        }
    }

    private fun publish(result: CaptureResult, asked: Boolean) {
        when (result) {
            CaptureResult.Empty -> _message.value = "Nothing selected"
            CaptureResult.Password -> _message.value = "Looks like a password, not saved."
            CaptureResult.NeedsConfirm -> _message.value = "That looks sensitive. Save it from the sheet."
            is CaptureResult.Saved -> {
                _undoId.value = result.id
                _message.value = when {
                    asked -> "Saved · AI will suggest where it goes"
                    result.trimmed -> "Added to ${result.destination} · trimmed"
                    else -> "Added to ${result.destination}"
                }
            }
        }
    }
}
