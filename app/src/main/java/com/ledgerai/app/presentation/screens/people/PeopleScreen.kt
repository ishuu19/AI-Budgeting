package com.ledgerai.app.presentation.screens.people

import com.ledgerai.app.presentation.components.LSearchToolbar
import com.ledgerai.app.presentation.components.LToolButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.people.InteractionRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.PhotoField
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.components.rememberPhotoActions
import com.ledgerai.app.presentation.components.rememberVoiceInput
import kotlinx.coroutines.launch
import com.ledgerai.app.presentation.components.LScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class PeopleViewModel(
    private val repository: PeopleRepository,
    private val userId: String,
    private val interactions: InteractionRepository? = null,
) : ViewModel() {

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _lastSeen = MutableStateFlow<Map<String, LocalDate>>(emptyMap())
    val lastSeen: StateFlow<Map<String, LocalDate>> = _lastSeen.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val listed = repository.listPeople(userId)
        _people.value = listed
        val repo = interactions ?: return
        val seen = mutableMapOf<String, LocalDate>()
        listed.filter { it.deletedAt == null }.forEach { person ->
            val latest = repo.listInteractions(userId, person.id).maxOfOrNull { it.occurredOn }
            if (latest != null) seen[person.id] = latest
        }
        _lastSeen.value = seen
    }

    fun addPerson(
        name: String,
        org: String?,
        role: String?,
        notes: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                repository.addPerson(
                    userId = userId,
                    name = name,
                    org = org,
                    role = role,
                    notes = notes,
                )
                _message.value = null
                reload()
                onDone(true)
            } catch (error: IllegalArgumentException) {
                _message.value = error.message ?: "Name is required"
                onDone(false)
            }
        }
    }
}

/**
 * Active people for [userId] from [PeopleRepository.listPeople].
 *
 * [onOpenPerson] should open [com.ledgerai.app.presentation.screens.memory.PersonMemoryScreen]
 * with that person's id. [PeopleRepository.addPerson] stores [com.ledgerai.app.domain.people.RecordVisibility.PRIVATE].
 * This screen does not change visibility.
 */
@Composable
fun PeopleScreen(
    userId: String,
    repository: PeopleRepository,
    onOpenPerson: (Person) -> Unit,
    onBack: (() -> Unit)? = null,
    interactions: InteractionRepository? = null,
) {
    val viewModel: PeopleViewModel = viewModel(key = userId) {
        PeopleViewModel(repository, userId, interactions)
    }
    val people by viewModel.people.collectAsState()
    val lastSeen by viewModel.lastSeen.collectAsState()
    val message by viewModel.message.collectAsState()
    PeopleScreen(
        people = people,
        onOpenPerson = onOpenPerson,
        onAddPerson = viewModel::addPerson,
        message = message,
        onBack = onBack,
        lastSeen = lastSeen,
    )
}

/**
 * People the caller already loaded.
 *
 * [onAddPerson] should call [PeopleRepository.addPerson] for the signed-in user.
 * Pass null for a blank org or role. Call `onDone(true)` only after the person is stored.
 * `onDone(false)` keeps the form; put the reason in [message].
 */
@Composable
fun PeopleScreen(
    people: List<Person>,
    onOpenPerson: (Person) -> Unit,
    onAddPerson: (
        name: String,
        org: String?,
        role: String?,
        notes: String,
        onDone: (Boolean) -> Unit,
    ) -> Unit,
    onBack: (() -> Unit)? = null,
    message: String? = null,
    lastSeen: Map<String, LocalDate> = emptyMap(),
) {
    val photos: PhotoSaveViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var adding by rememberSaveable { mutableStateOf(false) }
    var photoNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val notices = remember { SnackbarHostState() }
    val visible = people.filter { it.deletedAt == null }
    val camera = rememberPhotoActions(max = 6) { uris ->
        scope.launch {
            val n = photos.fileWithAi(uris, "This is a person. Name them and remember what you see.")
            photoNotice = if (n > 0) "Adding $n…" else "Couldn't read that photo."
        }
    }

    LaunchedEffect(photoNotice) {
        val text = photoNotice ?: return@LaunchedEffect
        notices.showSnackbar(text)
        photoNotice = null
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val shown = remember(visible, query) {
        if (query.isBlank()) visible else visible.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        LSearchToolbar(
            query = query,
            onQuery = { query = it },
            searching = searching,
            onSearching = { searching = it },
            hint = "Search people",
        ) {
            LToolButton(Icons.Filled.PhotoCamera, "Camera", camera.shoot)
            LToolButton(Icons.Filled.PhotoLibrary, "Photo", camera.pick)
        }
        LScreen(
            title = "People",
            modifier = Modifier.weight(1f),
            onBack = onBack,
            topPadding = 8.dp,
            snackbarHost = { NoticeHost(notices) },
            fab = {
                com.ledgerai.app.presentation.components.LFab(
                    Icons.Filled.Add,
                    onClick = { adding = true },
                    label = "New person",
                )
            },
        ) {
            if (shown.isEmpty()) {
                item { LEmpty(Icons.Default.Person, if (visible.isEmpty()) "No people yet" else "No matches") }
            } else {
                item {
                    LGroup {
                        shown.forEachIndexed { index, person ->
                            if (index > 0) LGroupDivider()
                            PersonLine(
                                name = person.name,
                                sub = personSub(person, lastSeen[person.id]),
                                onClick = { onOpenPerson(person) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var photo by remember { mutableStateOf<Uri?>(null) }
        var name by rememberSaveable { mutableStateOf("") }
        var notes by rememberSaveable { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val voice = rememberVoiceInput { notes = it }
        LSheet(
            title = "New person",
            onDismiss = { adding = false },
            primary = if (busy) "Saving…" else "Save",
            onPrimary = {
                busy = true
                onAddPerson(name.trim(), null, null, notes.trim()) { saved ->
                    if (saved) {
                        scope.launch {
                            photo?.let { photos.fileWithAi(listOf(it), "This is ${name.trim()}. ${notes.trim()}") }
                            busy = false
                            adding = false
                        }
                    } else {
                        busy = false
                    }
                }
            },
            primaryEnabled = name.isNotBlank() && !busy,
        ) {
            PhotoField(photo, { photo = it })
            LField(name, { name = it }, "Name")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LField(
                    notes,
                    { notes = it },
                    "Note",
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    minLines = 2,
                )
                LToolButton(Icons.Filled.Mic, "Speak", voice.toggle, active = voice.listening)
            }
            if (!message.isNullOrBlank()) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = L.Danger)
            }
        }
    }
}

@Composable
private fun NoticeHost(host: SnackbarHostState) {
    SnackbarHost(host) { data ->
        Snackbar(snackbarData = data, containerColor = L.Box, contentColor = L.OnBox)
    }
}

@Composable
private fun PersonLine(name: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialsAvatar(name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(L.BoxDeep),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = MaterialTheme.typography.labelLarge, color = L.OnBox)
    }
}

private fun personSub(person: Person, seen: LocalDate?): String? {
    seen?.let { return lastSeenLabel(it) }
    val detail = listOfNotNull(person.org, person.role).joinToString(" · ")
    return detail.ifBlank { person.notes.ifBlank { null } }
}

private fun lastSeenLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "Last seen today"
        days == 1L -> "Last seen 1d ago"
        days < 7L -> "Last seen ${days}d ago"
        days < 14L -> "Last seen 1w ago"
        days < 60L -> "Last seen ${days / 7}w ago"
        else -> "Last seen ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return (parts[0].take(1) + parts[1].take(1)).uppercase()
}
