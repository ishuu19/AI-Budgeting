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
 * Memories, interactions, and commitments the caller already loaded for [person].
 *
 * The memory draft is [MemoryStatus.CONFIRMED] with source type "manual" and [person]'s id.
 * [onAddMemory], [onAddInteraction], and [onAddCommitment] should call the matching
 * repository and invoke `onDone(true)` only after the row is stored. `onDone(false)` keeps
 * the form. Put the reason in [message], [interactionMessage], or [commitmentMessage].
 * Pass null [personId] to [onAddCommitment] to leave the commitment unattached.
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
    var text by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf(MemoryKind.FACT.name) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var interactionDate by rememberSaveable { mutableStateOf("") }
    var interactionWhere by rememberSaveable { mutableStateOf("") }
    var interactionSummary by rememberSaveable { mutableStateOf("") }
    var interactionSource by rememberSaveable { mutableStateOf("") }
    var interactionError by rememberSaveable { mutableStateOf<String?>(null) }
    var commitmentText by rememberSaveable { mutableStateOf("") }
    var commitmentStatus by rememberSaveable { mutableStateOf("") }
    var commitmentDue by rememberSaveable { mutableStateOf("") }
    var commitmentEvent by rememberSaveable { mutableStateOf("") }
    var commitmentError by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = MemoryKind.entries.find { it.name == kindName } ?: MemoryKind.FACT
    val subtitle = listOfNotNull(person.org, person.role).joinToString(" · ").ifBlank { null }
    val visible = memories.filter { it.personId == person.id && it.deletedAt == null }
    val visibleInteractions = interactions.filter { it.personId == person.id && it.deletedAt == null }
    val visibleCommitments = commitments.filter { it.personId == person.id && it.deletedAt == null }
    val banner = localError ?: message
    val interactionBanner = interactionError ?: interactionMessage
    val commitmentBanner = commitmentError ?: commitmentMessage

    LScreen(title = person.name, onBack = onBack) {
        item {
            LHero(
                label = "Person",
                value = person.name,
                sub = subtitle,
            )
        }
        item {
            Text(
                visibilityLabel(person.visibility),
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted,
            )
        }
        if (person.notes.isNotBlank()) {
            item {
                Text(
                    person.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = L.Ink,
                )
            }
        }
        item { LSection("Memories") }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Default.Lightbulb, "No memories yet") }
        } else {
            items(visible, key = { it.id }) { memory ->
                LCard {
                    Text(
                        memory.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = L.OnBox,
                    )
                    Text(
                        memoryLine(memory),
                        style = MaterialTheme.typography.bodySmall,
                        color = L.OnBoxMuted,
                    )
                }
            }
        }
        item { LSection("Add a memory") }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    localError = null
                },
                label = { Text("What you remember") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = L.Ink,
                    unfocusedTextColor = L.Ink,
                    focusedBorderColor = L.Box,
                    unfocusedBorderColor = L.Line,
                    cursorColor = L.Box,
                    focusedLabelColor = L.InkMuted,
                    unfocusedLabelColor = L.InkMuted,
                ),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemoryKind.entries.forEach { option ->
                    FilterChip(
                        selected = option == kind,
                        onClick = { kindName = option.name },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (!banner.isNullOrBlank()) {
            item {
                Text(
                    banner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            LButton(
                text = "Save memory",
                enabled = text.isNotBlank(),
                onClick = {
                    val draft = MemoryDraft(
                        personId = person.id,
                        text = text.trim(),
                        kind = kind,
                        status = MemoryStatus.CONFIRMED,
                        sourceType = MANUAL_SOURCE,
                        sourceId = null,
                    )
                    when (val check = MemoryRules.validate(draft)) {
                        is MemoryCheck.Rejected -> localError = check.reason
                        MemoryCheck.Accepted -> onAddMemory(draft) { saved ->
                            if (saved) {
                                text = ""
                                localError = null
                            }
                        }
                    }
                },
            )
        }
        item { LSection("Interactions") }
        if (visibleInteractions.isEmpty()) {
            item { LEmpty(Icons.Filled.Place, "No interactions yet") }
        } else {
            items(visibleInteractions, key = { "interaction-${it.id}" }) { interaction ->
                LCard {
                    Text(
                        interaction.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = L.OnBox,
                    )
                    Text(
                        interactionLine(interaction),
                        style = MaterialTheme.typography.bodySmall,
                        color = L.OnBoxMuted,
                    )
                }
            }
        }
        item { LSection("Add an interaction") }
        item {
            DetailField(
                value = interactionDate,
                onValueChange = {
                    interactionDate = it
                    interactionError = null
                },
                label = "Date",
            )
        }
        item {
            DetailField(
                value = interactionWhere,
                onValueChange = { interactionWhere = it },
                label = "Where",
            )
        }
        item {
            DetailField(
                value = interactionSummary,
                onValueChange = {
                    interactionSummary = it
                    interactionError = null
                },
                label = "Summary",
            )
        }
        item {
            DetailField(
                value = interactionSource,
                onValueChange = { interactionSource = it },
                label = "Source",
            )
        }
        if (!interactionBanner.isNullOrBlank()) {
            item {
                Text(
                    interactionBanner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            LButton(
                text = "Save interaction",
                enabled = interactionSummary.isNotBlank() && interactionDate.isNotBlank(),
                onClick = {
                    val occurredOn = typedDate(interactionDate)
                    if (occurredOn == null) {
                        interactionError = "Enter the date as yyyy-MM-dd"
                    } else {
                        onAddInteraction(
                            occurredOn,
                            interactionWhere.trim().ifBlank { null },
                            interactionSummary.trim(),
                            interactionSource.trim().ifBlank { null },
                        ) { saved ->
                            if (saved) {
                                interactionDate = ""
                                interactionWhere = ""
                                interactionSummary = ""
                                interactionSource = ""
                                interactionError = null
                            }
                        }
                    }
                },
            )
        }
        item { LSection("Commitments") }
        if (visibleCommitments.isEmpty()) {
            item { LEmpty(Icons.Filled.Event, "No commitments yet") }
        } else {
            items(visibleCommitments, key = { "commitment-${it.id}" }) { commitment ->
                val line = commitmentLine(commitment)
                LCard {
                    Text(
                        commitment.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = L.OnBox,
                    )
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = L.OnBoxMuted,
                        )
                    }
                }
            }
        }
        item { LSection("Add a commitment") }
        item {
            DetailField(
                value = commitmentText,
                onValueChange = {
                    commitmentText = it
                    commitmentError = null
                },
                label = "Commitment",
            )
        }
        item {
            DetailField(
                value = commitmentStatus,
                onValueChange = {
                    commitmentStatus = it
                    commitmentError = null
                },
                label = "Status",
            )
        }
        item {
            DetailField(
                value = commitmentDue,
                onValueChange = {
                    commitmentDue = it
                    commitmentError = null
                },
                label = "Due date",
            )
        }
        item {
            DetailField(
                value = commitmentEvent,
                onValueChange = { commitmentEvent = it },
                label = "Event",
            )
        }
        if (!commitmentBanner.isNullOrBlank()) {
            item {
                Text(
                    commitmentBanner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            LButton(
                text = "Save commitment",
                enabled = commitmentText.isNotBlank() && commitmentStatus.isNotBlank(),
                onClick = {
                    val dueOn = if (commitmentDue.isBlank()) {
                        null
                    } else {
                        typedDate(commitmentDue)
                    }
                    if (commitmentDue.isNotBlank() && dueOn == null) {
                        commitmentError = "Enter the due date as yyyy-MM-dd"
                    } else {
                        onAddCommitment(
                            commitmentEvent.trim().ifBlank { null },
                            commitmentText.trim(),
                            dueOn,
                            commitmentStatus.trim(),
                            person.id,
                        ) { saved ->
                            if (saved) {
                                commitmentText = ""
                                commitmentStatus = ""
                                commitmentDue = ""
                                commitmentEvent = ""
                                commitmentError = null
                            }
                        }
                    }
                },
            )
        }
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

@Composable
private fun DetailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = L.Ink,
            unfocusedTextColor = L.Ink,
            focusedBorderColor = L.Box,
            unfocusedBorderColor = L.Line,
            cursorColor = L.Box,
            focusedLabelColor = L.InkMuted,
            unfocusedLabelColor = L.InkMuted,
        ),
    )
}

private fun typedDate(raw: String): LocalDate? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed) }.getOrNull()
}

private fun interactionLine(interaction: Interaction): String {
    val parts = mutableListOf(interaction.occurredOn.toString())
    val place = interaction.where?.trim().orEmpty()
    if (place.isNotEmpty()) parts.add(place)
    val source = interaction.sourceId?.trim().orEmpty()
    if (source.isNotEmpty()) parts.add(source)
    return parts.joinToString(" · ")
}

private fun commitmentLine(commitment: Commitment): String {
    val parts = mutableListOf<String>()
    val status = commitment.status.trim()
    if (status.isNotEmpty()) parts.add(status)
    commitment.dueOn?.let { parts.add(it.toString()) }
    val event = commitment.eventId?.trim().orEmpty()
    if (event.isNotEmpty()) parts.add(event)
    return parts.joinToString(" · ")
}
