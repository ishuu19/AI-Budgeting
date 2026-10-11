package com.ledgerai.app.presentation.screens.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.people.CommitmentRepository
import com.ledgerai.app.data.people.InteractionRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.domain.memory.Memory
import com.ledgerai.app.domain.memory.MemoryCheck
import com.ledgerai.app.domain.memory.MemoryDraft
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryRules
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.people.Commitment
import com.ledgerai.app.domain.people.Interaction
import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.domain.people.RecordVisibility
import java.time.LocalDate
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LocalPhoto
import com.ledgerai.app.presentation.components.PhotoField
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.screens.money.OptionalDateField
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MANUAL_SOURCE = "manual"

class PersonMemoryViewModel(
    private val people: PeopleRepository,
    private val memories: MemoryRepository,
    private val interactions: InteractionRepository,
    private val commitments: CommitmentRepository,
    private val userId: String,
    private val personId: String,
) : ViewModel() {

    private val _person = MutableStateFlow<Person?>(null)
    val person: StateFlow<Person?> = _person.asStateFlow()

    private val _memoryList = MutableStateFlow<List<Memory>>(emptyList())
    val memoryList: StateFlow<List<Memory>> = _memoryList.asStateFlow()

    private val _interactionList = MutableStateFlow<List<Interaction>>(emptyList())
    val interactionList: StateFlow<List<Interaction>> = _interactionList.asStateFlow()

    private val _commitmentList = MutableStateFlow<List<Commitment>>(emptyList())
    val commitmentList: StateFlow<List<Commitment>> = _commitmentList.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _interactionMessage = MutableStateFlow<String?>(null)
    val interactionMessage: StateFlow<String?> = _interactionMessage.asStateFlow()

    private val _commitmentMessage = MutableStateFlow<String?>(null)
    val commitmentMessage: StateFlow<String?> = _commitmentMessage.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun addMemory(draft: MemoryDraft, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = memories.addMemory(
                userId = userId,
                personId = personId,
                text = draft.text,
                kind = draft.kind,
                status = MemoryStatus.CONFIRMED,
                sourceType = MANUAL_SOURCE,
                sourceId = null,
            )
            result.fold(
                onSuccess = {
                    reload()
                    _message.value = null
                    onDone(true)
                },
                onFailure = { error ->
                    _message.value = error.message ?: "Could not save that memory"
                    onDone(false)
                },
            )
        }
    }

    fun addInteraction(
        occurredOn: LocalDate,
        where: String?,
        summary: String,
        sourceId: String?,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val result = interactions.addInteraction(
                userId = userId,
                personId = personId,
                occurredOn = occurredOn,
                where = where,
                summary = summary,
                sourceId = sourceId,
            )
            result.fold(
                onSuccess = {
                    reload()
                    _interactionMessage.value = null
                    onDone(true)
                },
                onFailure = { error ->
                    _interactionMessage.value = error.message ?: "Could not save that interaction"
                    onDone(false)
                },
            )
        }
    }

    fun addCommitment(
        eventId: String?,
        text: String,
        dueOn: LocalDate?,
        status: String,
        linkedPersonId: String?,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val result = commitments.addCommitment(
                userId = userId,
                personId = linkedPersonId,
                eventId = eventId,
                text = text,
                dueOn = dueOn,
                status = status,
            )
            result.fold(
                onSuccess = {
                    reload()
                    _commitmentMessage.value = null
                    onDone(true)
                },
                onFailure = { error ->
                    _commitmentMessage.value = error.message ?: "Could not save that commitment"
                    onDone(false)
                },
            )
        }
    }

    private suspend fun reload() {
        val active = people.findActive(userId, personId)
        _person.value = active
        if (active == null) {
            _memoryList.value = emptyList()
            _interactionList.value = emptyList()
            _commitmentList.value = emptyList()
        } else {
            _memoryList.value = memories.listMemories(userId, active.id)
            _interactionList.value = interactions.listInteractions(userId, active.id)
            _commitmentList.value = commitments.listCommitments(userId, active.id)
        }
        _ready.value = true
    }
}

/**
 * One active person: memories, interactions, and commitments.
 * [people] resolves [personId]. The other repositories list and add that person's rows.
 *
 * A memory typed here is [MemoryStatus.CONFIRMED], source [MANUAL_SOURCE], and
 * [com.ledgerai.app.domain.people.RecordVisibility.PRIVATE]. This screen does not change visibility.
 * An interaction is stored for this person. A commitment uses the person id passed to
 * [PersonMemoryViewModel.addCommitment], which may be null.
 */
@Composable
fun PersonMemoryScreen(
    userId: String,
    personId: String,
    people: PeopleRepository,
    memories: MemoryRepository,
    interactions: InteractionRepository,
    commitments: CommitmentRepository,
    onBack: () -> Unit,
) {
    val viewModel: PersonMemoryViewModel = viewModel(key = "$userId:$personId") {
        PersonMemoryViewModel(people, memories, interactions, commitments, userId, personId)
    }
    val person by viewModel.person.collectAsState()
    val memoryList by viewModel.memoryList.collectAsState()
    val interactionList by viewModel.interactionList.collectAsState()
    val commitmentList by viewModel.commitmentList.collectAsState()
    val message by viewModel.message.collectAsState()
    val interactionMessage by viewModel.interactionMessage.collectAsState()
    val commitmentMessage by viewModel.commitmentMessage.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val active = person
    when {
        active != null -> PersonMemoryScreen(
            person = active,
            memories = memoryList,
            interactions = interactionList,
            commitments = commitmentList,
            onAddMemory = viewModel::addMemory,
            onAddInteraction = viewModel::addInteraction,
            onAddCommitment = viewModel::addCommitment,
            onBack = onBack,
            message = message,
            interactionMessage = interactionMessage,
            commitmentMessage = commitmentMessage,
        )
        ready -> MissingPerson(onBack)
        else -> LScreen(title = "Person", onBack = onBack) { }
    }
}

/**
 * Memories, moments (with photos) and promises the caller already loaded for [person].
 * Moments are the photo-first way in: add a photo, pick the day, say what happened. Dates come from a
 * date picker and status from chips, so nothing is typed in a special format and no file path is asked for.
 */
@Composable
fun PersonMemoryScreen(
    person: Person,
    memories: List<Memory>,
    interactions: List<Interaction>,
    commitments: List<Commitment>,
    onAddMemory: (MemoryDraft, onDone: (Boolean) -> Unit) -> Unit,
    onAddInteraction: (
        occurredOn: LocalDate,
        where: String?,
        summary: String,
        sourceId: String?,
        onDone: (Boolean) -> Unit,
    ) -> Unit,
    onAddCommitment: (
        eventId: String?,
        text: String,
        dueOn: LocalDate?,
        status: String,
        personId: String?,
        onDone: (Boolean) -> Unit,
    ) -> Unit,
    onBack: () -> Unit,
    message: String? = null,
    interactionMessage: String? = null,
    commitmentMessage: String? = null,
) {
    val photos: PhotoSaveViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val subtitle = listOfNotNull(person.org, person.role).joinToString(" · ").ifBlank { null }
    val visible = memories.filter { it.personId == person.id && it.deletedAt == null }
    val moments = interactions.filter { it.personId == person.id && it.deletedAt == null }
    val promises = commitments.filter { it.personId == person.id && it.deletedAt == null }
    fun photoOf(moment: Interaction): File? =
        moment.sourceId?.let { File(context.filesDir, "media/${it}_t.jpg") }?.takeIf { it.exists() }

    LScreen(title = person.name, onBack = onBack) {
        item { LHero(label = "Person", value = person.name, sub = subtitle) }
        if (person.notes.isNotBlank()) {
            item { Text(person.notes, style = MaterialTheme.typography.bodyLarge, color = L.Ink) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LButton("Add photo or moment", onClick = { sheet = "moment" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LGhostButton("Memory", onClick = { sheet = "memory" }, modifier = Modifier.weight(1f))
                    LGhostButton("Promise", onClick = { sheet = "promise" }, modifier = Modifier.weight(1f))
                }
            }
        }

        item { LSection("Moments") }
        if (moments.isEmpty()) {
            item { LEmpty(Icons.Filled.Place, "No moments yet. Add a photo from when you met.") }
        } else {
            items(moments, key = { "moment-${it.id}" }) { moment ->
                LCard(padding = 12.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        photoOf(moment)?.let { LocalPhoto(it, Modifier.size(84.dp).clip(RoundedCornerShape(L.RadiusSm))) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(moment.summary, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
                            Text(momentLine(moment), style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
                        }
                    }
                }
            }
        }

        item { LSection("Memories") }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Default.Lightbulb, "Nothing remembered yet") }
        } else {
            items(visible, key = { it.id }) { memory ->
                LCard {
                    Text(memory.text, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
                    Text(memoryLine(memory), style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
                }
            }
        }

        item { LSection("Promises") }
        if (promises.isEmpty()) {
            item { LEmpty(Icons.Filled.Place, "No promises. Add one to be reminded.") }
        } else {
            items(promises, key = { "promise-${it.id}" }) { c ->
                LRow(title = c.text, sub = commitmentLine(c))
            }
        }
    }

    when (sheet) {
        "moment" -> MomentSheet(
            error = interactionMessage,
            onDismiss = { sheet = "" },
            onSave = { photo, day, place, what, done ->
                scope.launch {
                    val id = photo?.let { photos.attachToPerson(it, person.id, person.name) }
                    onAddInteraction(day, place, what.ifBlank { "Photo together" }, id) { saved -> done(saved); if (saved) sheet = "" }
                }
            },
        )
        "memory" -> MemorySheet(
            error = localError ?: message,
            onDismiss = { sheet = ""; localError = null },
            onSave = { text, kind ->
                val draft = MemoryDraft(person.id, text.trim(), kind, MemoryStatus.CONFIRMED, MANUAL_SOURCE, null)
                when (val check = MemoryRules.validate(draft)) {
                    is MemoryCheck.Rejected -> localError = check.reason
                    MemoryCheck.Accepted -> onAddMemory(draft) { saved -> if (saved) { sheet = ""; localError = null } }
                }
            },
        )
        "promise" -> PromiseSheet(
            error = commitmentMessage,
            onDismiss = { sheet = "" },
            onSave = { text, due, status ->
                onAddCommitment(null, text.trim(), due, status, person.id) { saved -> if (saved) sheet = "" }
            },
        )
    }
}

@Composable
private fun MomentSheet(
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Uri?, LocalDate, String?, String, done: (Boolean) -> Unit) -> Unit,
) {
    var photo by remember { mutableStateOf<Uri?>(null) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var place by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LSheet(
        title = "Add photo or moment",
        onDismiss = onDismiss,
        primary = if (busy) "Saving…" else "Save",
        onPrimary = {
            busy = true
            onSave(photo, day, place.trim().ifBlank { null }, what.trim()) { busy = false }
        },
        primaryEnabled = !busy && (photo != null || what.isNotBlank()),
    ) {
        PhotoField(photo, { photo = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("When", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            DatePickChip(date = day, selected = true, onDate = { day = it })
        }
        LField(what, { what = it }, "What happened (optional)")
        LField(place, { place = it }, "Where (optional)")
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun MemorySheet(error: String?, onDismiss: () -> Unit, onSave: (String, MemoryKind) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MemoryKind.FACT.name) }
    LSheet(
        title = "Add a memory",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(text, MemoryKind.valueOf(kind)) },
        primaryEnabled = text.isNotBlank(),
    ) {
        LField(text, { text = it }, "What you remember", singleLine = false, minLines = 3)
        ChipsRow {
            MemoryKind.entries.forEach { LChip(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, kind == it.name, onClick = { kind = it.name }) }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun PromiseSheet(error: String?, onDismiss: () -> Unit, onSave: (String, LocalDate?, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var status by rememberSaveable { mutableStateOf("Open") }
    LSheet(
        title = "Add a promise",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(text, due, status) },
        primaryEnabled = text.isNotBlank(),
    ) {
        LField(text, { text = it }, "What did you promise?")
        OptionalDateField("Due", due) { due = it }
        ChipsRow { listOf("Open", "Done").forEach { LChip(it, status == it, onClick = { status = it }) } }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun MissingPerson(onBack: () -> Unit) {
    LScreen(title = "Person", onBack = onBack) {
        item { LEmpty(Icons.Default.Person, "This person is not in your list") }
    }
}

private fun visibilityLabel(visibility: RecordVisibility): String = when (visibility) {
    RecordVisibility.PRIVATE -> "Private"
    RecordVisibility.HOUSEHOLD -> "Household"
}

private fun memoryLine(memory: Memory): String {
    val kind = memory.kind.name.lowercase()
    val status = memory.status.name.lowercase()
    val source = memory.sourceType.ifBlank { "unknown source" }
    return "$kind · $status · $source"
}

private fun momentLine(interaction: Interaction): String {
    val parts = mutableListOf(interaction.occurredOn.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")))
    val place = interaction.where?.trim().orEmpty()
    if (place.isNotEmpty()) parts.add(place)
    return parts.joinToString(" · ")
}

private fun commitmentLine(commitment: Commitment): String {
    val parts = mutableListOf<String>()
    val status = commitment.status.trim()
    if (status.isNotEmpty()) parts.add(status)
    commitment.dueOn?.let { parts.add("due " + it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))) }
    return parts.joinToString(" · ")
}
