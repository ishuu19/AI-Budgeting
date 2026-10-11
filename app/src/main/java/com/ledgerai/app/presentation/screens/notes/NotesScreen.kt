package com.ledgerai.app.presentation.screens.notes

import android.widget.Toast
import kotlinx.coroutines.delay
import com.ledgerai.app.presentation.navigation.OpenItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.money.MutedLine
import com.ledgerai.app.presentation.screens.money.rememberToday
import com.ledgerai.app.presentation.screens.transactions.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
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
        if (title.isBlank() && body.isBlank()) {
            _aiMessage.value = "Add note text before summarizing."
            return
        }
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
            _aiMessage.value = "Summary ready · Rules"
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.summarizeNote(title, body)
                .onSuccess {
                    onResult(it.copy(source = "AI"))
                    _aiMessage.value = "Summary ready · AI"
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
        if (title.isBlank() && body.isBlank()) {
            _aiMessage.value = "Add note text before tagging."
            return
        }
        val ruled = NoteRules.suggestTags(title, body, lexicon.tags)
        if (ruled.isNotEmpty()) {
            onTags(ruled)
            _aiMessage.value = "Suggested ${ruled.size} tag(s) · Rules"
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.tagNote(title, body)
                .onSuccess {
                    onTags(it)
                    _aiMessage.value = "Suggested ${it.size} tag(s) · AI"
                }
                .onFailure { _aiMessage.value = it.message ?: "Tag failed" }
            _aiBusy.value = false
        }
    }

    /** Turns the to-do lines of a note into calendar tasks. Rules only; existing tasks with the same title are skipped. */
    fun tasksFromNote(title: String, body: String) {
        val items = NoteRules.actionItems(body).filter { it.kind == "TASK" }
        if (items.isEmpty()) {
            _aiMessage.value = "No tasks found · Rules"
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
            _aiMessage.value = if (added > 0) "Added $added task(s) · Rules" else "Tasks already added · Rules"
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
        if (question.isBlank()) {
            _aiMessage.value = "Enter a question first."
            return
        }
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
 * Notes list with search, tag filter and a note editor sheet.
 * [open] asks to open one note (a saved voice result); [onOpened] is called once it is handled.
 */
@Composable
fun NotesScreen(
    onBack: () -> Unit = {},
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val aiMessage by viewModel.aiMessage.collectAsState()
    var editorId by rememberSaveable { mutableLongStateOf(EDITOR_CLOSED) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(aiMessage) {
        val msg = aiMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearAiMessage()
    }

    LaunchedEffect(open?.nonce, notes) {
        val request = open ?: return@LaunchedEffect
        if (notes.any { it.id == request.id }) {
            editorId = request.id
            onOpened()
        } else {
            // The list may still be loading; give up when the note is not there.
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

    LScreen(
        title = "Notes",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { editorId = EDITOR_NEW }, label = "Add note") }
    ) {
        if (notes.isNotEmpty()) {
            item(key = "search") {
                LField(query, { query = it }, "Search notes")
            }
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
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                LEmpty(
                    Icons.Filled.Description,
                    if (notes.isEmpty()) "Say: note, idea for the project" else "No matches"
                )
            }
        } else {
            item(key = "count") {
                MutedLine(if (visible.size == 1) "1 note · newest first" else "${visible.size} notes · newest first")
            }
            item(key = "list") {
                LimitedGroup(visible, id = { it.id }, expandKey = "notes", limit = 30) { note ->
                    NoteRow(note, today, onClick = { editorId = note.id })
                }
            }
        }
    }

    val existing = notes.firstOrNull { it.id == editorId }
    if (editorId == EDITOR_NEW || existing != null) {
        NoteEditorSheet(
            existing = existing,
            aiBusy = aiBusy,
            aiAvailable = viewModel.aiAvailable(),
            onClose = { title, body, tags ->
                // Closing the sheet keeps what was typed, so text is never lost.
                val hasText = title.isNotBlank() || body.isNotBlank()
                val changed = existing == null ||
                    title.trim() != existing.title || body.trim() != existing.body || tags != existing.tags
                if (hasText && changed) {
                    if (existing == null) viewModel.addNote(title, body, tags)
                    else viewModel.updateNote(existing, title, body, tags)
                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                }
                editorId = EDITOR_CLOSED
            },
            onSave = { title, body, tags ->
                if (existing == null) viewModel.addNote(title, body, tags)
                else viewModel.updateNote(existing, title, body, tags)
                editorId = EDITOR_CLOSED
            },
            onDelete = {
                existing?.let { viewModel.deleteNote(it) }
                editorId = EDITOR_CLOSED
            },
            onSummarize = { title, body, apply -> viewModel.summarizeNote(title, body, apply) },
            onTag = { title, body, applyTags -> viewModel.tagNote(title, body, applyTags) },
            onAsk = { title, body, question, onAnswer ->
                viewModel.askAboutNote(title, body, question, onAnswer)
            },
            onNudge = { note -> viewModel.nudgeFromNote(note) },
            onTasks = { title, body -> viewModel.tasksFromNote(title, body) },
        )
    }
}

/** One note in the list: title and age, a two line preview, and up to three tags. */
@Composable
private fun NoteRow(note: NoteItem, today: LocalDate, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                note.title,
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(noteAge(note.updatedAt.toLocalDate(), today), style = MaterialTheme.typography.labelSmall, color = L.OnBoxMuted)
        }
        if (note.body.isNotBlank()) {
            Text(note.body, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (note.tags.isNotEmpty()) {
            Text(
                note.tags.take(3).joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = L.Gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun noteAge(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    else -> shortDate(date)
}

@Composable
private fun NoteEditorSheet(
    existing: NoteItem?,
    aiBusy: Boolean,
    aiAvailable: Boolean,
    onClose: (title: String, body: String, tags: List<String>) -> Unit,
    onSave: (title: String, body: String, tags: List<String>) -> Unit,
    onDelete: () -> Unit,
    onSummarize: (title: String, body: String, apply: (NoteSummaryDto) -> Unit) -> Unit,
    onTag: (title: String, body: String, applyTags: (List<String>) -> Unit) -> Unit,
    onAsk: (title: String, body: String, question: String, onAnswer: (String) -> Unit) -> Unit,
    onNudge: (NoteItem) -> Unit,
    onTasks: (title: String, body: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(existing?.body.orEmpty()) }
    var tagText by rememberSaveable { mutableStateOf(existing?.tags.orEmpty().joinTags()) }
    var askOpen by rememberSaveable { mutableStateOf(false) }
    var askQuestion by rememberSaveable { mutableStateOf("") }
    var askAnswer by rememberSaveable { mutableStateOf<String?>(null) }
    var summaryPreview by rememberSaveable { mutableStateOf<String?>(null) }
    var summarySource by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val tags = tagText.splitTags()
    val hasText = title.isNotBlank() || body.isNotBlank()
    val tagOptions = (tags + SUGGESTED_TAGS).distinct()

    LItemSheet(
        title = if (existing == null) "New" else "Note",
        onDismiss = { onClose(title, body, tags) },
        primary = "Save",
        onPrimary = { onSave(title, body, tags) },
        primaryEnabled = hasText,
        onDelete = if (existing != null) onDelete else null
    ) {
        LField(title, { title = it }, "Title")
        LField(body, { body = it }, "Note", singleLine = false, minLines = 6)

        ChipsRow {
            tagOptions.forEach { tag ->
                val selected = tag in tags
                LChip(tag, selected = selected, onClick = {
                    tagText = (if (selected) tags - tag else tags + tag).joinTags()
                })
            }
        }

        // One menu for the four AI actions. They need the cloud, so they say Offline instead of failing.
        Box {
            LGhostButton(
                if (aiAvailable) "Assist" else "Assist · Offline",
                onClick = { menuOpen = true },
                enabled = hasText && !aiBusy
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Summarize") },
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onSummarize(title, body) { summary ->
                            summaryPreview = summary.summary
                            summarySource = summary.source
                            val suggested = summary.tags.orEmpty()
                            if (suggested.isNotEmpty()) tagText = (tags + suggested).distinct().joinTags()
                            if (body.isBlank() && !summary.summary.isNullOrBlank()) body = summary.summary
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Tag") },
                    enabled = true,
                    onClick = {
                        menuOpen = false
                        onTag(title, body) { suggested -> tagText = (tags + suggested).distinct().joinTags() }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ask") },
                    enabled = aiAvailable,
                    onClick = { menuOpen = false; askOpen = true }
                )
                DropdownMenuItem(
                    text = { Text("Tasks") },
                    onClick = { menuOpen = false; onTasks(title, body) }
                )
                DropdownMenuItem(
                    text = { Text("Nudge") },
                    enabled = existing != null,
                    onClick = {
                        menuOpen = false
                        existing?.let { onNudge(it.copy(title = title, body = body, tags = tags)) }
                    }
                )
            }
        }

        if (askOpen) {
            LField(askQuestion, { askQuestion = it }, "Question")
            LButton("Ask", onClick = {
                if (!aiBusy && askQuestion.isNotBlank()) {
                    onAsk(title, body, askQuestion) { answer -> askAnswer = answer }
                }
            }, enabled = aiAvailable && !aiBusy && askQuestion.isNotBlank())
        }

        if (aiBusy) LLoading()

        listOfNotNull(summaryPreview?.let { it to summarySource }, askAnswer?.let { it to "AI" }).forEach { (text, source) ->
            LCard {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = L.OnBox,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (source != null) {
                    Text(source, style = MaterialTheme.typography.labelSmall, color = L.OnBoxMuted)
                }
            }
        }
    }
}
