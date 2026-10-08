package com.ledgerai.app.presentation.screens.notes

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.ai.NoteSummaryDto
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val SUGGESTED_TAGS = listOf("finance", "ideas", "goals", "shopping", "work", "personal")

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
    private val aiRepo: AiRepository,
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
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.summarizeNote(title, body)
                .onSuccess {
                    onResult(it)
                    _aiMessage.value = "Summary ready."
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
        viewModelScope.launch {
            _aiBusy.value = true
            aiRepo.tagNote(title, body)
                .onSuccess {
                    onTags(it)
                    _aiMessage.value = "Suggested ${it.size} tag(s)."
                }
                .onFailure { _aiMessage.value = it.message ?: "Tag failed" }
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

private data class NoteEditorState(
    val existing: NoteItem? = null,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList()
)

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

@Composable
fun NotesScreen(onBack: () -> Unit = {}, viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsState()
    val aiBusy by viewModel.aiBusy.collectAsState()
    val aiMessage by viewModel.aiMessage.collectAsState()
    var editor by remember { mutableStateOf<NoteEditorState?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(aiMessage) {
        val msg = aiMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearAiMessage()
    }

    val filterTags = remember(notes) { appearingNoteTags(notes) }
    LaunchedEffect(filterTags, selectedTag) {
        if (selectedTag != null && selectedTag !in filterTags) selectedTag = null
    }
    val visible = remember(notes, query, selectedTag) {
        notesMatching(notes, query, selectedTag)
    }

    LScreen(
        title = "Notes",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { editor = NoteEditorState() }) }
    ) {
        item(key = "hero") {
            LHero(label = "Notes", value = visible.size.toString())
        }
        item(key = "search") {
            LField(query, { query = it }, "Search")
        }
        item(key = "tags") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LChip("All", selected = selectedTag == null, onClick = { selectedTag = null })
                filterTags.forEach { tag ->
                    LChip(tag, selected = selectedTag == tag, onClick = { selectedTag = tag })
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                LEmpty(
                    Icons.Filled.Description,
                    if (notes.isEmpty()) "No notes" else "No matches"
                )
            }
        } else {
            items(visible, key = { it.id }) { note ->
                LRow(
                    title = note.title,
                    sub = note.body.ifBlank { null },
                    trailing = note.tags.firstOrNull()?.let { "#$it" },
                    onClick = {
                        editor = NoteEditorState(
                            existing = note,
                            title = note.title,
                            body = note.body,
                            tags = note.tags
                        )
                    }
                )
            }
        }
    }

    editor?.let { state ->
        NoteEditorSheet(
            state = state,
            aiBusy = aiBusy,
            onDismiss = { editor = null },
            onSave = { title, body, tags ->
                val existing = state.existing
                if (existing == null) viewModel.addNote(title, body, tags)
                else viewModel.updateNote(existing, title, body, tags)
                editor = null
            },
            onDelete = {
                state.existing?.let { viewModel.deleteNote(it) }
                editor = null
            },
            onSummarize = { title, body, apply -> viewModel.summarizeNote(title, body, apply) },
            onTag = { title, body, applyTags -> viewModel.tagNote(title, body, applyTags) },
            onAsk = { title, body, question, onAnswer ->
                viewModel.askAboutNote(title, body, question, onAnswer)
            },
        )
    }
}

@Composable
private fun NoteEditorSheet(
    state: NoteEditorState,
    aiBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit,
    onDelete: () -> Unit,
    onSummarize: (title: String, body: String, apply: (NoteSummaryDto) -> Unit) -> Unit,
    onTag: (title: String, body: String, applyTags: (List<String>) -> Unit) -> Unit,
    onAsk: (title: String, body: String, question: String, onAnswer: (String) -> Unit) -> Unit,
) {
    var title by remember(state) { mutableStateOf(state.title) }
    var body by remember(state) { mutableStateOf(state.body) }
    var tags by remember(state) { mutableStateOf(state.tags) }
    var askQuestion by remember { mutableStateOf("") }
    var askAnswer by remember { mutableStateOf<String?>(null) }
    var summaryPreview by remember { mutableStateOf<String?>(null) }
    val hasText = title.isNotBlank() || body.isNotBlank()
    val tagOptions = (tags + SUGGESTED_TAGS).distinct()

    LSheet(
        title = if (state.existing == null) "New" else "Note",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(title, body, tags) },
        primaryEnabled = hasText,
        secondary = if (state.existing != null) "Delete" else null,
        onSecondary = onDelete
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LField(title, { title = it }, "Title")
            LField(body, { body = it }, "Note", singleLine = false, minLines = 6)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagOptions.forEach { tag ->
                    val selected = tag in tags
                    LChip(tag, selected = selected, onClick = {
                        tags = if (selected) tags - tag else tags + tag
                    })
                }
            }

            LButton("Summarize", onClick = {
                if (aiBusy || !hasText) return@LButton
                onSummarize(title, body) { summary ->
                    summaryPreview = summary.summary
                    val suggested = summary.tags.orEmpty()
                    if (suggested.isNotEmpty()) tags = (tags + suggested).distinct()
                    if (body.isBlank() && !summary.summary.isNullOrBlank()) body = summary.summary
                }
            }, enabled = hasText && !aiBusy)

            LButton("Tag", onClick = {
                if (aiBusy || !hasText) return@LButton
                onTag(title, body) { suggested -> tags = (tags + suggested).distinct() }
            }, enabled = hasText && !aiBusy)

            Row(verticalAlignment = Alignment.CenterVertically) {
                LField(askQuestion, { askQuestion = it }, "Ask", modifier = Modifier.weight(1f))
            }
            LButton("Ask", onClick = {
                if (!aiBusy && askQuestion.isNotBlank()) {
                    onAsk(title, body, askQuestion) { answer -> askAnswer = answer }
                }
            }, enabled = !aiBusy && askQuestion.isNotBlank())

            if (aiBusy) {
                LProgress(1f)
            }

            listOfNotNull(summaryPreview, askAnswer).forEach { text ->
                LCard {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.OnBox,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
