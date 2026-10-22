package com.ledgerai.app.presentation.screens.dates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
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
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayEntity
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.dates.TripEntity
import com.ledgerai.app.data.dates.TripRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.LadderSwitches
import com.ledgerai.app.domain.dates.TripStatus
import com.ledgerai.app.domain.dates.Trips
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
import com.ledgerai.app.presentation.components.LSwitchRow
import com.ledgerai.app.presentation.components.LThreadRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import com.ledgerai.app.service.ReminderEngine

// ─── Birthdays ──────────────────────────────────────────────────────────────────────────────

data class BirthdayRow(val entity: BirthdayEntity, val name: String, val next: LocalDateTime, val days: Long, val turning: Int?)

@HiltViewModel
class BirthdaysViewModel @Inject constructor(
    private val dao: BirthdayDao,
    private val repo: BirthdayRepository,
    private val session: UserSession,
    private val engine: ReminderEngine,
) : ViewModel() {

    val rows: StateFlow<List<BirthdayRow>> = dao.observeActive()
        .map { list ->
            val uid = session.userInfo.first().userId.ifBlank { null }
            val names = repo.names(uid)
            val now = LocalDateTime.now()
            list.map { b ->
                val next = BirthdayLadder.nextCelebration(b.month, b.day, b.celebrateMinute, now)
                BirthdayRow(
                    b, names[b.personId] ?: "Someone", next,
                    ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate()),
                    BirthdayLadder.turning(b.birthYear, next),
                )
            }.sortedBy { it.next }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: String?, name: String, month: Int, day: Int, year: Int?, minute: Int, switches: LadderSwitches, note: String,
        onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val uid = session.userInfo.first().userId.ifBlank { null }
                repo.save(id, uid, name, month, day, year, minute, switches, note)
                engine.rebuildAndArm()
                onDone()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Check the details")
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id); engine.arm() }
    }
}

private val CELEBRATE_CHOICES = listOf("Midnight" to 0, "9:00" to 540, "12:00" to 720, "18:00" to 1080, "20:00" to 1200)

@Composable
fun BirthdaysScreen(onBack: () -> Unit, vm: BirthdaysViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    var editing by remember { mutableStateOf<BirthdayRow?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    LScreen(title = "Birthdays", onBack = onBack, action = { LAddAction({ adding = true }, "Add birthday") }) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.BIRTHDAY) }
        if (rows.isEmpty()) {
            item { LEmpty(Icons.Filled.Cake, "No birthdays yet.") }
        }
        var lastMonth = -1
        rows.forEach { row ->
            val m = row.next.monthValue
            if (m != lastMonth) {
                lastMonth = m
                item(key = "m$m-${row.entity.id}") { LSection(Month.of(m).getDisplayName(TextStyle.FULL, Locale.getDefault())) }
            }
            item(key = row.entity.id) {
                Row(
                    Modifier.fillMaxWidth().clickable { editing = row }.heightIn(min = 64.dp).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "%02d".format(row.entity.day),
                        style = MaterialTheme.typography.titleLarge,
                        color = L.Ink,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                        val age = row.turning?.let { "turns $it · " }.orEmpty()
                        val when_ = when (row.days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in ${row.days} days" }
                        Text(age + when_, style = MaterialTheme.typography.bodySmall, color = if (row.days in 0..7) L.Accent else L.InkMuted)
                    }
                }
            }
        }
    }

    if (adding) BirthdaySheet(null, vm, onDismiss = { adding = false })
    editing?.let { BirthdaySheet(it, vm, onDismiss = { editing = null }) }
}

@Composable
private fun BirthdaySheet(row: BirthdayRow?, vm: BirthdaysViewModel, onDismiss: () -> Unit) {
    val e = row?.entity
    var name by rememberSaveable { mutableStateOf(row?.name.orEmpty()) }
    var date by remember { mutableStateOf(LocalDate.of(2024, e?.month ?: LocalDate.now().monthValue, e?.day ?: LocalDate.now().dayOfMonth)) }
    var yearText by rememberSaveable { mutableStateOf(e?.birthYear?.toString().orEmpty()) }
    var minute by rememberSaveable { mutableStateOf(e?.celebrateMinute ?: 0) }
    var note by rememberSaveable { mutableStateOf(e?.note.orEmpty()) }
    var sw by remember {
        mutableStateOf(
            e?.let { LadderSwitches(it.r1mo, it.r2w, it.r1w, it.r3d, it.r1d, it.r5h, it.r30m, it.r10m, it.rAt) } ?: LadderSwitches()
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LSheet(
        title = if (row == null) "New birthday" else row.name,
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            vm.save(e?.id, name, date.monthValue, date.dayOfMonth, yearText.trim().toIntOrNull(), minute, sw, note, onError = { error = it }, onDone = onDismiss)
        },
        secondary = if (row != null) "Delete" else null,
        onSecondary = { confirmDelete = true },
        secondaryDestructive = true,
    ) {
        LField(name, { name = it }, "Name")
        LDateField("Date", date, { date = it }, pattern = "d MMMM")
        LField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, "Birth year (optional)")
        Text("celebration", style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        ChipsRow { CELEBRATE_CHOICES.forEach { (label, m) -> LChip(label, minute == m, { minute = m }) } }
        Text("remind me", style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        LSwitchRow("1 month", sw.month1, { sw = sw.copy(month1 = it) })
        LSwitchRow("2 weeks", sw.week2, { sw = sw.copy(week2 = it) })
        LSwitchRow("1 week", sw.week1, { sw = sw.copy(week1 = it) })
        LSwitchRow("3 days", sw.day3, { sw = sw.copy(day3 = it) })
        LSwitchRow("1 day", sw.day1, { sw = sw.copy(day1 = it) })
        LSwitchRow("5 hours", sw.hour5, { sw = sw.copy(hour5 = it) })
        LSwitchRow("30 minutes", sw.min30, { sw = sw.copy(min30 = it) })
        LSwitchRow("10 minutes", sw.min10, { sw = sw.copy(min10 = it) })
        LSwitchRow("At the time", sw.atTime, { sw = sw.copy(atTime = it) })
        LField(note, { note = it }, "Note (private)", singleLine = false, minLines = 2)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
    if (confirmDelete && e != null) {
        LConfirmDelete(onConfirm = { vm.delete(e.id); confirmDelete = false; onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

// ─── Trips ──────────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class TripsViewModel @Inject constructor(
    dao: TripDao,
    private val repo: TripRepository,
    private val session: UserSession,
    private val engine: ReminderEngine,
) : ViewModel() {

    val trips: StateFlow<List<TripEntity>> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: String?, name: String, place: String, start: LocalDate, end: LocalDate, notes: String, week: Boolean, day: Boolean,
        onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val uid = session.userInfo.first().userId.ifBlank { null }
                repo.save(id, uid, name, place, start, end, notes, week, day)
                engine.rebuildAndArm()
                onDone()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Check the details")
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id); engine.arm() }
    }
}

@Composable
fun TripsScreen(onBack: () -> Unit, vm: TripsViewModel = hiltViewModel()) {
    val trips by vm.trips.collectAsState()
    val today = LocalDate.now()
    var editing by remember { mutableStateOf<TripEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val shortDate = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun status(t: TripEntity) = Trips.status(t.startDate, t.endDate, today)
    val ongoing = trips.filter { status(it) == TripStatus.ONGOING }
    val planned = trips.filter { status(it) == TripStatus.PLANNED }
    val past = trips.filter { status(it) == TripStatus.DONE }.sortedByDescending { it.startDate }

    fun detail(t: TripEntity): String {
        val range = "${shortDate.format(t.startDate)} – ${shortDate.format(t.endDate)}"
        return when (status(t)) {
            TripStatus.ONGOING -> "day ${Trips.dayNumber(t.startDate, t.endDate, today)} of ${Trips.length(t.startDate, t.endDate)} · $range"
            TripStatus.PLANNED -> "in ${Trips.daysUntil(t.startDate, today)} days · $range"
            TripStatus.DONE -> range
        }
    }

    LScreen(title = "Trips", onBack = onBack, action = { LAddAction({ adding = true }, "Add trip") }) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.TRIP) }
        if (trips.isEmpty()) item { LEmpty(Icons.Filled.Cake, "No trips yet.") }
        if (ongoing.isNotEmpty()) item { LSection("now") }
        eachItem(ongoing, key = { it.id }) { t -> LThreadRow(shortDate.format(t.startDate), "${t.name} · ${t.place}", detail = detail(t), now = true, onClick = { editing = t }) }
        if (planned.isNotEmpty()) item { LSection("planned") }
        eachItem(planned, key = { it.id }) { t -> LThreadRow(shortDate.format(t.startDate), "${t.name} · ${t.place}", detail = detail(t), onClick = { editing = t }) }
        if (past.isNotEmpty()) item { LSection("past") }
        eachItem(past, key = { it.id }) { t -> LThreadRow(shortDate.format(t.startDate), "${t.name} · ${t.place}", detail = detail(t), muted = true, onClick = { editing = t }) }
    }

    if (adding) TripSheet(null, vm, onDismiss = { adding = false })
    editing?.let { TripSheet(it, vm, onDismiss = { editing = null }) }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.eachItem(
    list: List<T>,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) = list.forEach { item(key = key(it)) { row(it) } }

@Composable
private fun TripSheet(trip: TripEntity?, vm: TripsViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(trip?.name.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(trip?.place.orEmpty()) }
    var start by remember { mutableStateOf(trip?.startDate ?: LocalDate.now()) }
    var end by remember { mutableStateOf(trip?.endDate ?: LocalDate.now()) }
    var notes by rememberSaveable { mutableStateOf(trip?.notes.orEmpty()) }
    var week by remember { mutableStateOf(trip?.r1w ?: true) }
    var day by remember { mutableStateOf(trip?.r1d ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LSheet(
        title = if (trip == null) "New trip" else trip.name,
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.save(trip?.id, name, place, start, end, notes, week, day, onError = { error = it }, onDone = onDismiss) },
        secondary = if (trip != null) "Delete" else null,
        onSecondary = { confirmDelete = true },
        secondaryDestructive = true,
    ) {
        LField(name, { name = it }, "Name")
        LField(place, { place = it }, "Place")
        LDateField("Start", start, { start = it; if (end.isBefore(it)) end = it })
        LDateField("End", end, { end = it })
        LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        LSwitchRow("Remind 1 week before", week, { week = it })
        LSwitchRow("Remind 1 day before", day, { day = it })
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
    if (confirmDelete && trip != null) {
        LConfirmDelete(onConfirm = { vm.delete(trip.id); confirmDelete = false; onDismiss() }, onDismiss = { confirmDelete = false })
    }
}
