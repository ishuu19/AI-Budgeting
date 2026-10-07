package com.ledgerai.app.presentation.screens.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.domain.model.NoteItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepo: NoteRepository
) : ViewModel() {

    val notes: StateFlow<List<NoteItem>> = noteRepo.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            noteRepo.insert(
                NoteItem(
                    title = title.trim().ifBlank { "Untitled" },
                    body = body.trim()
                )
            )
        }
    }

    fun updateNote(note: NoteItem, title: String, body: String) {
        viewModelScope.launch {
            noteRepo.update(note.copy(title = title.trim().ifBlank { "Untitled" }, body = body.trim()))
        }
    }

    fun deleteNote(note: NoteItem) {
        viewModelScope.launch { noteRepo.delete(note) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsState()
    var editor by remember { mutableStateOf<NoteEditorState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes")
                        Text(
                            "Capture ideas. AI actions come later.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = NoteEditorState() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add note")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No notes yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Write a note to keep context offline and ready for AI later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpen = {
                            editor = NoteEditorState(
                                existing = note,
                                title = note.title,
                                body = note.body
                            )
                        },
                        onDelete = { viewModel.deleteNote(note) }
                    )
                }
            }
        }
    }

    editor?.let { state ->
        NoteEditorDialog(
            state = state,
            onDismiss = { editor = null },
            onSave = { title, body ->
                val existing = state.existing
                if (existing == null) viewModel.addNote(title, body)
                else viewModel.updateNote(existing, title, body)
                editor = null
            }
        )
    }
}

private data class NoteEditorState(
    val existing: NoteItem? = null,
    val title: String = "",
    val body: String = ""
)

@Composable
private fun NoteRow(note: NoteItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d · HH:mm") }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (note.body.isNotBlank()) {
                    Text(
                        note.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    note.updatedAt.format(fmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete note")
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    state: NoteEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(state) { mutableStateOf(state.title) }
    var body by remember(state) { mutableStateOf(state.body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.existing == null) "New note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
                enabled = title.isNotBlank() || body.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
