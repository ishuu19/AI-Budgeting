package com.ledgerai.app.presentation.screens.people

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val repository: PeopleRepository,
    private val userId: String,
) : ViewModel() {

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _people.value = repository.listPeople(userId)
        }
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
                _people.value = repository.listPeople(userId)
                _message.value = null
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
) {
    val viewModel: PeopleViewModel = viewModel(key = userId) {
        PeopleViewModel(repository, userId)
    }
    val people by viewModel.people.collectAsState()
    val message by viewModel.message.collectAsState()
    PeopleScreen(
        people = people,
        onOpenPerson = onOpenPerson,
        onAddPerson = viewModel::addPerson,
        message = message,
        onBack = onBack,
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
) {
    var name by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val visible = people.filter { it.deletedAt == null }

    LScreen(title = "People", onBack = onBack) {
        item {
            Text(
                "Private to you",
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted,
            )
        }
        if (!message.isNullOrBlank()) {
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Default.Person, "No people yet") }
        } else {
            items(visible, key = { it.id }) { person ->
                val detail = listOfNotNull(person.org, person.role).joinToString(" · ")
                LRow(
                    title = person.name,
                    sub = detail.ifBlank { person.notes.ifBlank { null } },
                    icon = Icons.Default.Person,
                    onClick = { onOpenPerson(person) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        item {
            PersonField(value = name, onValueChange = { name = it }, label = "Name")
        }
        item {
            PersonField(value = org, onValueChange = { org = it }, label = "Organization")
        }
        item {
            PersonField(value = role, onValueChange = { role = it }, label = "Role")
        }
        item {
            PersonField(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
        }
        item {
            LButton(
                text = "Add person",
                enabled = name.isNotBlank(),
                onClick = {
                    onAddPerson(
                        name.trim(),
                        org.trim().ifBlank { null },
                        role.trim().ifBlank { null },
                        notes.trim(),
                    ) { saved ->
                        if (saved) {
                            name = ""
                            org = ""
                            role = ""
                            notes = ""
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PersonField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth(),
        colors = personFieldColors(),
    )
}

@Composable
private fun personFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = L.Ink,
    unfocusedTextColor = L.Ink,
    focusedBorderColor = L.Box,
    unfocusedBorderColor = L.Line,
    cursorColor = L.Box,
    focusedLabelColor = L.InkMuted,
    unfocusedLabelColor = L.InkMuted,
)
