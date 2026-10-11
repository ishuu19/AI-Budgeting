package com.ledgerai.app.presentation.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ledgerai.app.presentation.components.LAiBar
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.rememberVoiceInput
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
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LocalPhoto
import com.ledgerai.app.presentation.components.PhotoField
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.screens.money.OptionalDateField
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
        else -> LScreen(title = person?.name ?: "Person", onBack = onBack) {
            item { LLoading() }
        }
    }
}

/**
 * Memories, moments (with photos) and promises the caller already loaded for [person].
 * One timeline. Say what happened in the bar, or open New moment, New memory, or New promise.
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
    var menu by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf("") }
    var armed by remember { mutableStateOf(false) }

    val subtitle = listOfNotNull(person.org, person.role).joinToString(" · ").ifBlank { null }
    val visible = memories.filter { it.personId == person.id && it.deletedAt == null }
    val moments = interactions.filter { it.personId == person.id && it.deletedAt == null }
    val promises = commitments.filter { it.personId == person.id && it.deletedAt == null }
    val first = person.name.substringBefore(' ').ifBlank { person.name }
    fun photoOf(moment: Interaction): File? =
        moment.sourceId?.let { File(context.filesDir, "media/${it}_t.jpg") }?.takeIf { it.exists() }
    fun tell(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        when (classify(trimmed)) {
            "promise" -> onAddCommitment(null, trimmed, null, "Open", person.id) { }
            "moment" -> onAddInteraction(LocalDate.now(), null, trimmed, null) { }
            else -> {
                val draft = MemoryDraft(person.id, trimmed, MemoryKind.FACT, MemoryStatus.CONFIRMED, MANUAL_SOURCE, null)
                when (val check = MemoryRules.validate(draft)) {
                    is MemoryCheck.Rejected -> localError = check.reason
                    MemoryCheck.Accepted -> onAddMemory(draft) { saved -> if (saved) localError = null }
                }
            }
        }
    }
    val voice = rememberVoiceInput { heard = it }
    LaunchedEffect(voice.listening) {
        if (voice.listening) {
            armed = true
        } else if (armed) {
            val said = heard
            heard = ""
            armed = false
            if (said.isNotBlank()) tell(said)
        }
    }
    val openPromises = promises.filter { isOpen(it.status) }
        .sortedWith(compareBy<Commitment, LocalDate?>(nullsLast()) { it.dueOn })
    val rest = buildList {
        moments.forEach { add(Line.Moment(it) to it.occurredOn) }
        visible.forEach { add(Line.Remembered(it) to memoryDay(it.updatedAt)) }
        promises.filter { !isOpen(it.status) }.forEach { add(Line.Promise(it) to (it.dueOn ?: memoryDay(it.updatedAt))) }
    }.sortedByDescending { it.second }.map { it.first }
    val barError = when {
        sheet.isNotEmpty() -> null
        !localError.isNullOrBlank() -> localError
        !message.isNullOrBlank() -> message
        !interactionMessage.isNullOrBlank() -> interactionMessage
        else -> commitmentMessage
    }

    LScreen(title = person.name, onBack = onBack) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val headerPhoto = moments.sortedByDescending { it.occurredOn }.firstNotNullOfOrNull { photoOf(it) }
                if (headerPhoto != null) {
                    LocalPhoto(headerPhoto, Modifier.size(96.dp).clip(CircleShape))
                } else {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(L.BoxDeep),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initials(person.name), style = MaterialTheme.typography.titleLarge, color = L.OnBox)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
                }
            }
        }
        if (person.notes.isNotBlank()) {
            item {
                Text(
                    person.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = L.Ink,
                    maxLines = 2,
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LAiBar(
                    hint = "Tell me about $first…",
                    onSend = ::tell,
                    onMic = voice.toggle,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    LToolButton(Icons.Filled.Add, "New", onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("New moment") },
                            onClick = { menu = false; sheet = "moment" },
                        )
                        DropdownMenuItem(
                            text = { Text("New memory") },
                            onClick = { menu = false; sheet = "memory" },
                        )
                        DropdownMenuItem(
                            text = { Text("New promise") },
                            onClick = { menu = false; sheet = "promise" },
                        )
                    }
                }
            }
        }
        if (!barError.isNullOrBlank()) {
            item { Text(barError, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
        }
        if (openPromises.isEmpty() && rest.isEmpty()) {
            item { LEmpty(Icons.Filled.PhotoCamera, "No moments yet") }
        } else {
            item {
                LGroup {
                    var index = 0
                    openPromises.forEach { promise ->
                        if (index++ > 0) LGroupDivider()
                        PromiseRow(promise)
                    }
                    rest.forEach { line ->
                        if (index++ > 0) LGroupDivider()
                        when (line) {
                            is Line.Moment -> MomentRow(line.item, photoOf(line.item))
                            is Line.Remembered -> MemoryRow(line.item)
                            is Line.Promise -> PromiseRow(line.item)
                        }
                    }
                }
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
        title = "New moment",
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
        LField(what, { what = it }, "What happened")
        LField(place, { place = it }, "Where")
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun MemorySheet(error: String?, onDismiss: () -> Unit, onSave: (String, MemoryKind) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MemoryKind.FACT.name) }
    LSheet(
        title = "New memory",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(text, MemoryKind.valueOf(kind)) },
        primaryEnabled = text.isNotBlank(),
    ) {
        LField(text, { text = it }, "What you remember", singleLine = false, minLines = 3)
        ChipsRow {
            MemoryKind.entries.forEach { kindOption ->
                LChip(kindLabel(kindOption), kind == kindOption.name, onClick = { kind = kindOption.name })
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun PromiseSheet(error: String?, onDismiss: () -> Unit, onSave: (String, LocalDate?, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    LSheet(
        title = "New promise",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(text, due, "Open") },
        primaryEnabled = text.isNotBlank(),
    ) {
        LField(text, { text = it }, "What did you promise?")
        OptionalDateField("Due", due) { due = it }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
    }
}

@Composable
private fun MissingPerson(onBack: () -> Unit) {
    LScreen(title = "Person", onBack = onBack) {
        item { LEmpty(Icons.Filled.Person, "This person is not in your list") }
    }
}

private fun visibilityLabel(visibility: RecordVisibility): String = when (visibility) {
    RecordVisibility.PRIVATE -> "Private"
    RecordVisibility.HOUSEHOLD -> "Household"
}

private sealed class Line {
    data class Moment(val item: Interaction) : Line()
    data class Remembered(val item: Memory) : Line()
    data class Promise(val item: Commitment) : Line()
}

@Composable
private fun MomentRow(moment: Interaction, photo: File?) {
    val file = photo
    LGroupRow(
        title = moment.summary,
        sub = momentSub(moment),
        icon = Icons.Filled.PhotoCamera,
        end = if (file != null) {
            { LocalPhoto(file, Modifier.size(64.dp).clip(RoundedCornerShape(L.RadiusSm))) }
        } else {
            null
        },
    )
}

@Composable
private fun MemoryRow(memory: Memory) {
    LGroupRow(
        title = memory.text,
        sub = if (memory.updatedAt > 0) relativeDay(memoryDay(memory.updatedAt)) else null,
        icon = Icons.Filled.Lightbulb,
        trailing = if (memory.status == MemoryStatus.INFERRED) "Inferred" else null,
        trailingColor = L.Highlight,
    )
}

@Composable
private fun PromiseRow(promise: Commitment) {
    LGroupRow(
        title = promise.text,
        sub = promise.dueOn?.let { dueLabel(it) },
        icon = Icons.Filled.Handshake,
    )
}

private fun classify(text: String): String {
    val folded = text.lowercase()
    return when {
        listOf("promise", "i'll", "i will", "remind me").any { folded.contains(it) } -> "promise"
        listOf("met ", "saw ", "visited", "moment", "photo").any { folded.contains(it) } -> "moment"
        else -> "memory"
    }
}

private fun isOpen(status: String): Boolean {
    val folded = status.trim().lowercase()
    return folded != "done" && folded != "closed"
}

private fun kindLabel(kind: MemoryKind): String = when (kind) {
    MemoryKind.FACT -> "Fact"
    MemoryKind.PREFERENCE -> "Preference"
    MemoryKind.GOAL -> "Goal"
}

private fun momentSub(moment: Interaction): String {
    val place = moment.where?.trim().orEmpty()
    val day = relativeDay(moment.occurredOn)
    return if (place.isEmpty()) day else "$day · $place"
}

private fun dueLabel(date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return if (days == 0L) "Due today" else "Due ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
}

private fun relativeDay(date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days == 0L -> "Today"
        days == 1L -> "1d ago"
        days in 2..6 -> "${days}d ago"
        days in -6..-1 -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun memoryDay(updatedAt: Long): LocalDate =
    if (updatedAt > 0) {
        java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    } else {
        LocalDate.MIN
    }

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return (parts[0].take(1) + parts[1].take(1)).uppercase()
}
