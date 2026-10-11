package com.ledgerai.app.presentation.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.NoteLexicon
import com.ledgerai.app.data.ai.NoteRules
import com.ledgerai.app.data.ai.NoteSummaryDto
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.NudgeProposalRepository
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.screens.money.rememberToday
import com.ledgerai.app.presentation.screens.transactions.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val SUGGESTED_TAGS = listOf("finance", "ideas", "goals", "shopping", "work", "personal")

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
    private val aiRepo: AiRepository,
    private val nudgeRepo: NudgeProposalRepository,
    private val lexicon: NoteLexicon,
    private val calendarRepo: CalendarRepository,
) : ViewModel() {

    val notes: StateFlow<List<NoteItem>> = noteRepo.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    fun clearAiMessage() {
        _aiMessage.value = null
    }

    /** True when the cloud can be reached. Summarize, tag and ask need it. */
    fun aiAvailable(): Boolean = aiRepo.isAiAvailable()

    fun addNote(title: String, body: String, tags: List<String>) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            noteRepo.insert(
                NoteItem(
                    title = title.trim().ifBlank { "Untitled" },
                    body = body.trim(),
                    tags = tags.distinct()
                )
            )
        }
    }

    fun updateNote(note: NoteItem, title: String, body: String, tags: List<String>) {
        viewModelScope.launch {
            noteRepo.update(
                note.copy(
                    title = title.trim().ifBlank { "Untitled" },
                    body = body.trim(),
                    tags = tags.distinct()
                )
            )
        }
    }

    fun deleteNote(note: NoteItem) {
        viewModelScope.launch { noteRepo.delete(note) }
    }

    fun summarizeNote(
        title: String,
        body: String,
        onResult: (NoteSummaryDto) -> Unit,
    ) {
        if (title.isBlank() && body.isBlank()) return
        // Rules first: extractive summary and keyword tags work offline. The cloud is only a fallback.
        val ruled = NoteRules.summarize(title, body)
        if (ruled.summary.isNotBlank()) {
            onResult(
                NoteSummaryDto(
                    summary = ruled.bullets.joinToString("\n") { "• $it" },
                    tags = NoteRules.suggestTags(title, body, lexicon.tags),
                    highlights = ruled.bullets,
                    source = "Rules"
                )
            )
            _aiMessage.value = "Summary added"
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.summarizeNote(title, body)
                .onSuccess {
                    onResult(it.copy(source = "AI"))
                    _aiMessage.value = "Summary added"
                }
                .onFailure { _aiMessage.value = it.message ?: "Summarize failed" }
            _aiBusy.value = false
        }
    }

    fun tagNote(
        title: String,
        body: String,
        onTags: (List<String>) -> Unit,
    ) {
        if (title.isBlank() && body.isBlank()) return
        val ruled = NoteRules.suggestTags(title, body, lexicon.tags)
        if (ruled.isNotEmpty()) {
            onTags(ruled)
            _aiMessage.value = if (ruled.size == 1) "1 tag added" else "${ruled.size} tags added"
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.tagNote(title, body)
                .onSuccess {
                    onTags(it)
                    _aiMessage.value = if (it.size == 1) "1 tag added" else "${it.size} tags added"
                }
                .onFailure { _aiMessage.value = it.message ?: "Tag failed" }
            _aiBusy.value = false
        }
    }

    /** Turns the to-do lines of a note into calendar tasks. Rules only; existing tasks with the same title are skipped. */
    fun tasksFromNote(title: String, body: String) {
        val items = NoteRules.actionItems(body).filter { it.kind == "TASK" }
        if (items.isEmpty()) {
            _aiMessage.value = "No tasks found"
            return
        }
        viewModelScope.launch {
            val existing = calendarRepo.observeTasks().first().map { it.title.trim().lowercase() }.toSet()
            var added = 0
            val now = java.time.LocalDateTime.now()
            items.filter { it.title.trim().lowercase() !in existing }.forEach { item ->
                val at = item.due ?: now
                calendarRepo.upsert(
                    CalendarEvent(title = item.title, startAt = at, endAt = at, kind = CalendarEventKind.TASK, hasDate = item.due != null),
                    withDefaultReminders = item.due != null
                )
                added++
            }
            _aiMessage.value = when (added) {
                0 -> "Tasks already added"
                1 -> "1 task added"
                else -> "$added tasks added"
            }
        }
    }

    fun nudgeFromNote(note: NoteItem) {
        viewModelScope.launch {
            _aiBusy.value = true
            val count = nudgeRepo.scanNote(note.id, note.body).size
            _aiMessage.value = if (count > 0) "$count nudge(s) proposed" else "No new nudges found"
            _aiBusy.value = false
        }
    }

    fun askAboutNote(
        title: String,
        body: String,
        question: String,
        onAnswer: (String) -> Unit,
    ) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.askAboutNote(title, body, question.trim())
                .onSuccess {
                    onAnswer(it)
                    _aiMessage.value = null
                }
                .onFailure { _aiMessage.value = it.message ?: "Ask failed" }
            _aiBusy.value = false
        }
    }
}

/** Tags that actually appear on notes, sorted. Suggested tags show only when used. */
internal fun appearingNoteTags(notes: List<NoteItem>): List<String> =
    notes.asSequence().flatMap { it.tags }.distinct().sortedBy { it.lowercase() }.toList()

/** Search title/body/tags, optional tag filter, newest [NoteItem.updatedAt] first. */
internal fun notesMatching(
    notes: List<NoteItem>,
    query: String,
    tag: String?,
): List<NoteItem> {
    val q = query.trim()
    return notes
        .asSequence()
        .filter { note ->
            val tagOk = tag == null || tag in note.tags
            val textOk = q.isEmpty() ||
                note.title.contains(q, ignoreCase = true) ||
                note.body.contains(q, ignoreCase = true) ||
                note.tags.any { it.contains(q, ignoreCase = true) }
            tagOk && textOk
        }
        .sortedByDescending { it.updatedAt }
        .toList()
}

private const val EDITOR_CLOSED = -1L
private const val EDITOR_NEW = 0L
private const val TAG_SEPARATOR = ","

private fun List<String>.joinTags() = joinToString(TAG_SEPARATOR)
private fun String.splitTags() = split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Notes list with the Plan toolbar search, a tag filter and a note editor sheet.
 * [open] asks to open one note (a saved voice result); [onOpened] is called once it is handled.
 */
@Composable
fun NotesScreen(
    onBack: () -> Unit = {},
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    query: String = "",
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val aiMessage by viewModel.aiMessage.collectAsState()
    var editorId by rememberSaveable { mutableLongStateOf(EDITOR_CLOSED) }
    var voiceSeed by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(aiMessage) {
        val msg = aiMessage ?: return@LaunchedEffect
        notice = msg
        viewModel.clearAiMessage()
    }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(2500)
        notice = null
    }

    LaunchedEffect(open?.nonce, notes) {
        val request = open ?: return@LaunchedEffect
        if (notes.any { it.id == request.id }) {
            editorId = request.id
            onOpened()
        } else {
            delay(1500)
            onOpened()
        }
    }

    val filterTags = remember(notes) { appearingNoteTags(notes) }
    LaunchedEffect(filterTags, selectedTag) {
        if (selectedTag != null && selectedTag !in filterTags) selectedTag = null
    }
    val visible = remember(notes, query, selectedTag) {
        notesMatching(notes, query, selectedTag)
    }
    val today = rememberToday()
    val voice = rememberVoiceInput { spoken ->
        if (spoken.isNotBlank()) {
            voiceSeed = spoken
            editorId = EDITOR_NEW
        }
    }

    LScreen(
        title = "Notes",
        onBack = onBack,
        snackbarHost = {
            val text = notice
            if (text != null) LSnackbar(text)
        },
        fab = { LFab(Icons.Filled.Add, onClick = { voiceSeed = ""; editorId = EDITOR_NEW }, label = "New note") }
    ) {
        if (filterTags.isNotEmpty()) {
            item(key = "tags") {
                ChipsRow {
                    LChip("All", selected = selectedTag == null, onClick = { selectedTag = null })
                    filterTags.forEach { tag ->
                        LChip(tag, selected = selectedTag == tag, onClick = { selectedTag = tag })
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LEmpty(
                        Icons.Filled.Description,
                        if (notes.isEmpty()) "No notes yet" else "No matches"
                    )
                    if (notes.isEmpty()) {
                        LToolButton(
                            Icons.Filled.Mic,
                            if (voice.listening) "Stop" else "Speak",
                            voice.toggle,
                            active = voice.listening
                        )
                    }
                }
            }
        } else {
            listOf(
                "Today" to visible.filter { it.updatedAt.toLocalDate() == today },
                "This week" to visible.filter {
                    val day = it.updatedAt.toLocalDate()
                    day.isBefore(today) && !day.isBefore(today.minusDays(6))
                },
                "Earlier" to visible.filter { it.updatedAt.toLocalDate().isBefore(today.minusDays(6)) }
            ).forEach { (label, rows) ->
                if (rows.isEmpty()) return@forEach
                item(key = label) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LSection(label)
                        LGroup {
                            rows.forEachIndexed { index, note ->
                                if (index > 0) LGroupDivider()
                                NoteRow(note, today, onClick = { editorId = note.id })
                            }
                        }
                    }
                }
            }
        }
    }

    val existing = notes.firstOrNull { it.id == editorId }
    if (editorId == EDITOR_NEW || existing != null) {
        NoteEditorSheet(
            existing = existing,
            seed = if (existing == null) voiceSeed else "",
            aiBusy = aiBusy,
            aiAvailable = viewModel.aiAvailable(),
            onClose = { title, body, tags ->
                val hasText = title.isNotBlank() || body.isNotBlank()
                val changed = existing == null ||
                    title.trim() != existing.title || body.trim() != existing.body || tags != existing.tags
                if (hasText && changed) {
                    if (existing == null) viewModel.addNote(title, body, tags)
                    else viewModel.updateNote(existing, title, body, tags)
                    notice = "Note saved"
                }
                voiceSeed = ""
                editorId = EDITOR_CLOSED
            },
            onSave = { title, body, tags ->
                if (existing == null) viewModel.addNote(title, body, tags)
                else viewModel.updateNote(existing, title, body, tags)
                notice = "Note saved"
                voiceSeed = ""
                editorId = EDITOR_CLOSED
            },
            onDelete = {
                existing?.let { viewModel.deleteNote(it) }
                voiceSeed = ""
                editorId = EDITOR_CLOSED
            },
            onSummarize = { title, body, apply -> viewModel.summarizeNote(title, body, apply) },
            onAsk = { title, body, question, onAnswer ->
                viewModel.askAboutNote(title, body, question, onAnswer)
            },
            onTasks = { title, body -> viewModel.tasksFromNote(title, body) },
        )
    }
}

@Composable
private fun NoteRow(note: NoteItem, today: LocalDate, onClick: () -> Unit) {
    val preview = note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    val tags = note.tags.take(3).joinToString(" ") { "#$it" }.takeIf { it.isNotBlank() }
    LGroupRow(
        title = note.title,
        sub = listOfNotNull(preview, tags).joinToString(" · ").ifBlank { null },
        trailing = noteAge(note.updatedAt.toLocalDate(), today),
        onClick = onClick
    )
}

private fun noteAge(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    else -> shortDate(date)
}

@Composable
private fun NoteEditorSheet(
    existing: NoteItem?,
    seed: String,
    aiBusy: Boolean,
    aiAvailable: Boolean,
    onClose: (title: String, body: String, tags: List<String>) -> Unit,
    onSave: (title: String, body: String, tags: List<String>) -> Unit,
    onDelete: () -> Unit,
    onSummarize: (title: String, body: String, apply: (NoteSummaryDto) -> Unit) -> Unit,
    onAsk: (title: String, body: String, question: String, onAnswer: (String) -> Unit) -> Unit,
    onTasks: (title: String, body: String) -> Unit,
) {
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var body by rememberSaveable(existing?.id ?: seed) { mutableStateOf(existing?.body ?: seed) }
    var tagText by rememberSaveable(existing?.id) { mutableStateOf(existing?.tags.orEmpty().joinTags()) }
    var askOpen by rememberSaveable { mutableStateOf(false) }
    var askQuestion by rememberSaveable { mutableStateOf("") }
    var askAnswer by rememberSaveable { mutableStateOf<String?>(null) }
    var summaryPreview by rememberSaveable { mutableStateOf<String?>(null) }
    var summarySource by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingTag by rememberSaveable { mutableStateOf(false) }
    val tags = tagText.splitTags()
    val hasText = title.isNotBlank() || body.isNotBlank()

    fun applySummary(summary: NoteSummaryDto) {
        summaryPreview = summary.summary
        summarySource = summary.source
        val suggested = summary.tags.orEmpty()
        if (suggested.isNotEmpty()) tagText = (tags + suggested).distinct().joinTags()
        if (body.isBlank() && !summary.summary.isNullOrBlank()) body = summary.summary
    }

    LItemSheet(
        title = if (existing == null) "New note" else existing.title.ifBlank { "Note" },
        onDismiss = { onClose(title, body, tags) },
        primary = "Save",
        onPrimary = { onSave(title, body, tags) },
        primaryEnabled = hasText,
        onDelete = if (existing != null) onDelete else null
    ) {
        LField(title, { title = it }, "Title")
        if (hasText) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LToolButton(Icons.Filled.AutoAwesome, "Summarize", {
                    if (!aiBusy) onSummarize(title, body, ::applySummary)
                })
                if (aiAvailable) {
                    LToolButton(Icons.Filled.QuestionAnswer, "Ask", { if (!aiBusy) askOpen = true })
                }
                LToolButton(Icons.Filled.Checklist, "To tasks", { if (!aiBusy) onTasks(title, body) })
            }
        }
        LField(body, { body = it }, "Note", singleLine = false, minLines = 6)

        ChipsRow {
            tags.forEach { tag ->
                LChip(tag, selected = true, onClick = { tagText = (tags - tag).joinTags() })
            }
            LChip("+ tag", selected = pickingTag, onClick = { pickingTag = !pickingTag })
        }
        if (pickingTag) {
            val unused = SUGGESTED_TAGS.filter { it !in tags }
            if (unused.isNotEmpty()) {
                ChipsRow {
                    unused.forEach { tag ->
                        LChip(tag, selected = false, onClick = {
                            tagText = (tags + tag).joinTags()
                            pickingTag = false
                        })
                    }
                }
            }
        }

        if (askOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LField(askQuestion, { askQuestion = it }, "Question", Modifier.weight(1f))
                LToolButton(
                    Icons.Filled.Send,
                    "Ask",
                    {
                        if (!aiBusy && aiAvailable && askQuestion.isNotBlank()) {
                            onAsk(title, body, askQuestion) { answer -> askAnswer = answer }
                        }
                    }
                )
            }
        }

        if (aiBusy) LLoading()

        listOfNotNull(summaryPreview?.let { it to summarySource }, askAnswer?.let { it to "AI" }).forEach { (text, source) ->
            LCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.OnBox,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (source == "AI") {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Inferred", tint = L.Highlight)
                    }
                }
            }
        }
    }
}
