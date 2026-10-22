package com.ledgerai.app.presentation.screens.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LAddAction
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LDateField
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LTextLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val UPCOMING = "UPCOMING"
private const val DONE = "DONE"
private const val CANCELLED = "CANCELLED"

/** An appointment is a calendar row of kind APPOINTMENT with a person and a status. One table, two views. */
@HiltViewModel
class AppointmentsViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val people: PeopleRepository,
    private val birthdays: BirthdayRepository,
    private val session: UserSession,
) : ViewModel() {

    val rows: StateFlow<List<CalendarEvent>> = calendar.observeAll()
        .map { list -> list.filter { it.kind == CalendarEventKind.APPOINTMENT }.sortedBy { it.startAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val names: StateFlow<Map<String, String>> = kotlinx.coroutines.flow.flow {
        emit(birthdays.names(session.userInfo.first().userId.ifBlank { null }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun save(
        old: CalendarEvent?, title: String, day: LocalDate, time: String, place: String, person: String, notes: String,
        status: String, onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 120) return onError("Title must be 1 to 120 characters")
        val t = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull() ?: return onError("Time looks like 14:30")
        viewModelScope.launch {
            val owner = session.userInfo.first().userId.ifBlank { null } ?: com.ledgerai.app.data.dates.LOCAL_OWNER
            val personId = person.trim().takeIf { it.isNotEmpty() }?.let { n ->
                people.listPeople(owner).firstOrNull { it.name.equals(n, true) }?.id ?: people.addPerson(owner, n, null, null, "").id
            }
            val start = LocalDateTime.of(day, t)
            val event = (old ?: CalendarEvent(title = clean, startAt = start, kind = CalendarEventKind.APPOINTMENT)).copy(
                title = clean, startAt = start, endAt = start.plusHours(1), location = place.trim(), notes = notes.trim(),
                personId = personId, status = status,
                // A finished or cancelled appointment stops reminding.
                isEnabled = status == UPCOMING,
            )
            val id = calendar.upsert(event)
            if (old == null) {
                calendar.addReminder(id, "1 day before", 1440)
                calendar.addReminder(id, "1 hour before", 60)
            }
            onDone()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { calendar.deleteById(id) }
    }
}

@Composable
fun AppointmentsScreen(vm: AppointmentsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    val names by vm.names.collectAsState()
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val upcoming = rows.filter { it.status != CANCELLED && it.status != DONE && it.endAt.isAfter(now) }
    val needsAnswer = rows.filter { (it.status == null || it.status == UPCOMING) && !it.endAt.isAfter(now) }
    val past = rows.filter { it.status == DONE || it.status == CANCELLED }.sortedByDescending { it.startAt }
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

    @Composable
    fun Entry(e: CalendarEvent, muted: Boolean = false) {
        Column(Modifier.fillMaxWidth().clickable { editing = e }.heightIn(min = 64.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fmt.format(e.startAt), style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
            Text(e.title, style = MaterialTheme.typography.titleSmall, color = if (muted) L.InkMuted else L.Ink)
            val detail = listOfNotNull(e.location.ifBlank { null }, e.personId?.let { names[it] }, e.status?.takeIf { it != UPCOMING }?.lowercase()).joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
        }
    }

    LScreen(title = "Appointments", action = { LAddAction({ adding = true }, "Add appointment") }) {
        if (rows.isEmpty()) item { LEmpty(Icons.Filled.Event, "No appointments yet.") }
        if (needsAnswer.isNotEmpty()) {
            item { LSection("did it happen?") }
            needsAnswer.forEach { e ->
                item(key = "q${e.id}") {
                    Column {
                        Entry(e)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LTextLink("Done", { vm.save(e, e.title, e.startAt.toLocalDate(), e.startAt.toLocalTime().toString(), e.location, e.personId?.let { names[it] }.orEmpty(), e.notes, DONE, {}, {}) })
                            LTextLink("Cancelled", { vm.save(e, e.title, e.startAt.toLocalDate(), e.startAt.toLocalTime().toString(), e.location, e.personId?.let { names[it] }.orEmpty(), e.notes, CANCELLED, {}, {}) })
                            LTextLink("Edit", { editing = e })
                        }
                    }
                }
            }
        }
        if (upcoming.isNotEmpty()) item { LSection("upcoming") }
        upcoming.forEach { e -> item(key = "u${e.id}") { Entry(e) } }
        if (past.isNotEmpty()) item { LSection("past") }
        past.forEach { e -> item(key = "p${e.id}") { Entry(e, muted = true) } }
    }

    if (adding) AppointmentSheet(null, names, vm) { adding = false }
    editing?.let { AppointmentSheet(it, names, vm) { editing = null } }
}

@Composable
private fun AppointmentSheet(e: CalendarEvent?, names: Map<String, String>, vm: AppointmentsViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(e?.title.orEmpty()) }
    var day by remember { mutableStateOf(e?.startAt?.toLocalDate() ?: LocalDate.now()) }
    var time by rememberSaveable { mutableStateOf(e?.startAt?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "09:00") }
    var place by rememberSaveable { mutableStateOf(e?.location.orEmpty()) }
    var person by rememberSaveable { mutableStateOf(e?.personId?.let { names[it] }.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(e?.notes.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(e?.status ?: UPCOMING) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LSheet(
        title = if (e == null) "New appointment" else "Appointment",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.save(e, title, day, time, place, person, notes, status, onError = { error = it }, onDone = onDismiss) },
        secondary = if (e != null) "Delete" else null,
        onSecondary = { confirm = true },
        secondaryDestructive = true,
    ) {
        LField(title, { title = it }, "What is it?")
        LDateField("Date", day, { day = it })
        LField(time, { time = it }, "Time (24 h, like 14:30)")
        LField(place, { place = it }, "Place")
        LField(person, { person = it }, "With (name)")
        LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        if (e != null) ChipsRow { listOf(UPCOMING to "Upcoming", DONE to "Done", CANCELLED to "Cancelled").forEach { (v, t) -> LChip(t, status == v, { status = v }) } }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
    if (confirm && e != null) LConfirmDelete(onConfirm = { vm.delete(e.id); confirm = false; onDismiss() }, onDismiss = { confirm = false })
}
