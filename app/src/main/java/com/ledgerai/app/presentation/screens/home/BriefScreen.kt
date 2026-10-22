package com.ledgerai.app.presentation.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.TripStatus
import com.ledgerai.app.domain.dates.Trips
import com.ledgerai.app.presentation.components.LAiBar
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.presentation.screens.library.LibraryLinks
import com.ledgerai.app.presentation.screens.today.TodayItem
import com.ledgerai.app.presentation.screens.today.TodayKind
import com.ledgerai.app.presentation.screens.today.TodayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class ComingUp(val title: String, val whenText: String, val isTrip: Boolean)

@HiltViewModel
class BriefViewModel @Inject constructor(
    birthdays: BirthdayDao,
    trips: TripDao,
    repo: BirthdayRepository,
    session: UserSession,
) : ViewModel() {

    /** Birthdays and trips in the next 14 days, soonest first, at most two. */
    val coming: StateFlow<List<ComingUp>> = combine(birthdays.observeActive(), trips.observeActive()) { b, t ->
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val names = repo.names(session.userInfo.first().userId.ifBlank { null })
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val bd = b.map { x ->
            val at = BirthdayLadder.nextCelebration(x.month, x.day, x.celebrateMinute, now)
            Triple(at.toLocalDate(), "${names[x.personId] ?: "Someone"}'s birthday", false)
        }
        val tr = t.filter { Trips.status(it.startDate, it.endDate, today) != TripStatus.DONE }
            .map { Triple(maxOf(it.startDate, today), if (Trips.status(it.startDate, it.endDate, today) == TripStatus.ONGOING) "${it.name}, now" else it.name, true) }
        (bd + tr)
            .filter { ChronoUnit.DAYS.between(today, it.first) in 0..14 }
            .sortedBy { it.first }
            .take(2)
            .map { ComingUp(it.second, fmt.format(it.first), it.third) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Home: what needs me, in four beats. Next, safe to spend, coming up, then the ask bar. */
@Composable
fun BriefScreen(links: AppLinks, library: LibraryLinks, today: TodayViewModel = hiltViewModel(), brief: BriefViewModel = hiltViewModel()) {
    val state by today.state.collectAsState()
    val coming by brief.coming.collectAsState()
    val now = LocalDateTime.now()
    val day = LocalDate.now()
    val next = nextItem(state.items, now)
    val photos: PhotoSaveViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var photoNote by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = photos.fileWithAi(listOf(uri), "A photo from Home. File it where it belongs.")
            photoNote = if (saved > 0) "Adding from photo…" else "Couldn't read that photo."
        }
    }

    LScreen(title = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())) {
        item {
            Text(
                DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()).format(day),
                style = MaterialTheme.typography.bodyLarge,
                color = L.InkMuted,
                modifier = Modifier.clickable { links.plan(PlanSeg.Calendar) },
            )
        }
        item { LSection("next") }
        item {
            if (next == null) {
                Text("Nothing due.", style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
            } else {
                Column(Modifier.fillMaxWidth().clickable { links.plan(PlanSeg.Calendar) }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        (if (next.allDay) "today" else next.start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) + "   " + next.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = L.Ink,
                    )
                    Text(inText(next, now), style = MaterialTheme.typography.bodySmall, color = L.Accent)
                }
            }
        }
        val guide = state.guide
        if (guide != null) {
            item { LSection("safe to spend today") }
            item {
                Column(Modifier.clickable { links.money(com.ledgerai.app.presentation.navigation.MoneySeg.Overview) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(guide), style = MaterialTheme.typography.displayLarge, color = if (state.spentToday > guide) L.Danger else L.Ink, maxLines = 1)
                    Text("${money(state.spentToday)} spent", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                }
            }
        }
        if (coming.isNotEmpty()) {
            item { LSection("coming up") }
            coming.forEach { c ->
                item(key = c.title) {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (c.isTrip) library.trips() else library.birthdays() }.heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                        Text(c.whenText, style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                LAiBar(
                    "Tell me anything",
                    onSend = { links.speak(it) },
                    onMic = links.listen,
                    onImage = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                photoNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.InkMuted) }
            }
        }
    }
}

/** The one item with the earliest time that is still ahead (or running). Dated tasks and bills follow. */
private fun nextItem(items: List<TodayItem>, now: LocalDateTime): TodayItem? {
    val timed = items.filter { !it.allDay && it.end.isAfter(now) && it.kind != TodayKind.Habit }.minByOrNull { it.start }
    if (timed != null) return timed
    return items.firstOrNull { it.allDay && it.start.toLocalDate() == now.toLocalDate() }
}

private fun inText(item: TodayItem, now: LocalDateTime): String {
    if (item.allDay) return if (item.kind == TodayKind.Bill) "due today" else "today"
    if (!item.start.isAfter(now)) return "now"
    val d = Duration.between(now, item.start)
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return when {
        h >= 24 -> "tomorrow"
        h >= 1 -> "in $h h ${m} min"
        else -> "in ${d.toMinutes()} min"
    }
}
