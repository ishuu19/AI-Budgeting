package com.ledgerai.app.presentation.screens.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.JobStats
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class JobsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val jobs: List<JobApplication> = emptyList(),
    val stats: JobStats = JobStats(0, 0, 0)
)

/**
 * Splits one pasted line into company and role. Only " - " (with spaces), "|", an en dash or an em dash
 * separate the two, so a hyphen inside a name ("Rolls-Royce") stays in place.
 */
internal fun parseJobLine(line: String): Pair<String, String> {
    val url = Regex("https?://\\S+").find(line)?.value.orEmpty()
    val text = line.replace(url, "").trim()
    val parts = text.split(Regex("\\s*[|\u2013\u2014]\\s*|\\s+-\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    return parts.getOrElse(0) { text.ifBlank { line.trim() } } to parts.getOrElse(1) { "Role" }
}

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repo: JobRepository,
    private val lexicon: com.ledgerai.app.data.ai.JobLexiconProvider
) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = false) }
        job = viewModelScope.launch {
            repo.observeAll()
                .catch { _state.update { s -> s.copy(loading = false, error = true) } }
                .collect { list -> _state.update { it.copy(loading = false, jobs = list, stats = repo.stats(list)) } }
        }
    }

    fun savePasted(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val today = LocalDate.now()
            // Rules read company, role, link, source, status and dates. Several one-line jobs become several rows.
            val apps = com.ledgerai.app.data.ai.JobPasteParser.parseMany(text, today, lexicon.lexicon).map { p ->
                val fallback = parseJobLine(p.notes.lines().firstOrNull { it.isNotBlank() } ?: text)
                val applied = p.appliedOn ?: today
                JobApplication(
                    company = p.company.ifBlank { fallback.first },
                    title = p.title.ifBlank { fallback.second },
                    url = p.url,
                    source = p.source,
                    status = p.status,
                    appliedOn = applied,
                    followUpOn = p.followUpOn ?: repo.defaultFollowUp(applied),
                    notes = if (p.notes.contains('\n')) p.notes else "",
                    location = p.location,
                    extraDates = p.extraDates
                )
            }
            repo.saveBatch(apps)
        }
    }

    fun save(job: JobApplication) {
        viewModelScope.launch { repo.save(job) }
    }

    fun setStatus(job: JobApplication, status: JobApplicationStatus) {
        viewModelScope.launch { repo.save(job.copy(status = status)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}

private fun JobApplicationStatus.label(): String = name.lowercase().replaceFirstChar { it.titlecase() }

/** Month of job follow-ups only. These dates are not written to the main calendar. */
@Composable
private fun JobsMonth(
    jobs: List<JobApplication>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val shown = YearMonth.parse(month)
    val today = LocalDate.now()
    val marks = jobs.mapNotNull { it.followUpOn }.toSet()
    val first = shown.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7
    val cells = buildList {
        repeat(lead) { add(first.minusDays((lead - it).toLong())) }
        for (day in 1..shown.lengthOfMonth()) add(shown.atDay(day))
        val tail = shown.atEndOfMonth().plusDays(1)
        var extra = 0
        while (size % 7 != 0) add(tail.plusDays((extra++).toLong()))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = shown.minusMonths(1).toString() }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = L.Primary)
            }
            Text(
                shown.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = L.Ink
            )
            IconButton(onClick = { month = shown.plusMonths(1).toString() }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = L.Primary)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { letter ->
                Text(
                    letter,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = L.InkMuted,
                    maxLines = 1
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == shown
                    val marked = date in marks
                    val on = date == selected
                    val isToday = date == today
                    val bg = when {
                        on -> L.Gold
                        isToday -> L.Box
                        else -> Color.Transparent
                    }
                    val ink = when {
                        on -> L.BoxDeep
                        isToday -> L.OnBox
                        inMonth -> L.Ink
                        else -> L.InkMuted
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(date) }
                            .semantics {
                                contentDescription = date.format(DateTimeFormatter.ofPattern("MMMM d")) +
                                    if (marked) ", follow up" else ""
                            }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium, color = ink)
                        }
                        Box(
                            Modifier.size(5.dp).clip(CircleShape).background(
                                when {
                                    !marked -> Color.Transparent
                                    on -> L.BoxDeep
                                    isToday -> L.Gold
                                    else -> L.Box
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

private val StatusFilters: List<JobApplicationStatus?> = listOf(null) + JobApplicationStatus.entries

/** Pipeline order for the list: what is closest to an outcome first. */
private val StageOrder = listOf(
    JobApplicationStatus.INTERVIEW,
    JobApplicationStatus.OFFER,
    JobApplicationStatus.SCREENING,
    JobApplicationStatus.APPLIED,
    JobApplicationStatus.REJECTED,
    JobApplicationStatus.WITHDRAWN
)

/** Stages where a follow-up still makes sense. */
private val ActiveStages = setOf(
    JobApplicationStatus.APPLIED,
    JobApplicationStatus.SCREENING,
    JobApplicationStatus.INTERVIEW
)

private const val GROUP_CAP = 5

private fun JobApplicationStatus.next(): JobApplicationStatus? = when (this) {
    JobApplicationStatus.APPLIED -> JobApplicationStatus.SCREENING
    JobApplicationStatus.SCREENING -> JobApplicationStatus.INTERVIEW
    JobApplicationStatus.INTERVIEW -> JobApplicationStatus.OFFER
    else -> null
}

/**
 * Jobs segment. The top card is the job whose follow-up is due; below it a short pipeline summary,
 * stage filters and the jobs grouped by stage with a one-tap "move to next stage" button.
 */
@Composable
fun JobsScreen(
    openId: Long? = null,
    onOpened: () -> Unit = {},
    viewModel: JobsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var paste by rememberSaveable { mutableStateOf("") }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf<JobApplicationStatus?>(null) }
    var dayEpoch by rememberSaveable { mutableStateOf(Long.MIN_VALUE) }
    var showMonth by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf("") }
    val selectedDay = if (dayEpoch == Long.MIN_VALUE) null else LocalDate.ofEpochDay(dayEpoch)
    val today = LocalDate.now()

    LaunchedEffect(openId) {
        if (openId != null) {
            sheetId = openId
            onOpened()
        }
    }

    val shown = state.jobs.filter { job ->
        (filter == null || job.status == filter) &&
            (selectedDay == null || job.followUpOn == selectedDay || (job.followUpOn == null && job.appliedOn == selectedDay))
    }

    LScreen(
        title = "Jobs",
        action = {
            IconButton(onClick = {
                showMonth = !showMonth
                if (!showMonth) dayEpoch = Long.MIN_VALUE
            }) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = if (showMonth) "Hide follow-up calendar" else "Show follow-up calendar",
                    tint = L.Primary
                )
            }
        },
        fab = { LFab(Icons.Default.Add, onClick = { adding = true }, label = "Add") }
    ) {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = viewModel::load) }
            state.jobs.isEmpty() -> item { LEmpty(Icons.Default.Work, "No jobs yet. Say: applied to Google, follow up Friday") }
            else -> {
                val due = state.jobs
                    .filter { it.status in ActiveStages && it.followUpOn?.isAfter(today) == false }
                    .sortedBy { it.followUpOn?.toEpochDay() ?: 0L }
                val first = due.firstOrNull()
                if (first != null) {
                    item(key = "due") {
                        val nextStage = first.status.next()
                        val actions: @Composable RowScope.() -> Unit = {
                            ChipsRow {
                                if (nextStage != null) {
                                    LChip(
                                        "Move to ${nextStage.label()}",
                                        selected = true,
                                        onClick = { viewModel.setStatus(first, nextStage) }
                                    )
                                }
                                LChip(
                                    "Snooze 1 week",
                                    selected = false,
                                    onClick = { viewModel.save(first.copy(followUpOn = today.plusDays(7))) }
                                )
                                DatePickChip(
                                    date = today.plusDays(7),
                                    selected = false,
                                    onDate = { viewModel.save(first.copy(followUpOn = it)) },
                                    label = "Pick date"
                                )
                            }
                        }
                        LHeroCard(
                            label = "Follow up now",
                            title = first.company.ifBlank { first.title },
                            sub = listOfNotNull(
                                first.title.takeIf { it.isNotBlank() && first.company.isNotBlank() },
                                first.followUpOn?.let { "due ${it.format(dateFmt)}" },
                                if (due.size > 1) "+${due.size - 1} more" else null
                            ).joinToString(" · "),
                            onClick = { sheetId = first.id },
                            actions = actions
                        )
                    }
                }
                item(key = "stats") {
                    LSmallPair(
                        left = { m ->
                            LSmallBlock("Applied", state.stats.appliedThisWeek.toString(), m, sub = "this week")
                        },
                        right = { m ->
                            LSmallBlock(
                                "Replied",
                                "${state.stats.responseRatePercent}%",
                                m,
                                sub = "${state.stats.interviews} interviews"
                            )
                        }
                    )
                }
                if (showMonth) {
                    item(key = "month") {
                        JobsMonth(
                            jobs = state.jobs,
                            selected = selectedDay,
                            onSelect = { day ->
                                dayEpoch = if (day == selectedDay) Long.MIN_VALUE else day.toEpochDay()
                            }
                        )
                    }
                }
                item(key = "chips") {
                    LKindChips(StatusFilters, filter, { it?.label() ?: "All" }, { filter = it })
                }
                if (shown.isEmpty()) {
                    item(key = "none") { LEmpty(Icons.Default.Work, "None here") }
                } else {
                    StageOrder.forEach { stage ->
                        val rows = shown.filter { it.status == stage }
                        if (rows.isEmpty()) return@forEach
                        val key = stage.name
                        val all = expanded.split("|").contains(key)
                        val visible = if (all) rows else rows.take(GROUP_CAP)
                        item(key = "g-$key") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LSection("${stage.label()} · ${rows.size}")
                                LGroup {
                                    visible.forEachIndexed { i, job ->
                                        if (i > 0) LGroupDivider()
                                        val ns = job.status.next()
                                        val endSlot: (@Composable () -> Unit)? = if (ns != null) {
                                            {
                                                IconButton(onClick = { viewModel.setStatus(job, ns) }) {
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = "Move ${job.company.ifBlank { job.title }} to ${ns.label()}",
                                                        tint = L.Primary
                                                    )
                                                }
                                            }
                                        } else null
                                        val overdue = job.status in ActiveStages && job.followUpOn?.isBefore(today) == true
                                        LGroupRow(
                                            title = job.company.ifBlank { job.title },
                                            sub = listOfNotNull(
                                                job.title.takeIf { it.isNotBlank() && job.company.isNotBlank() },
                                                job.location.takeIf { it.isNotBlank() },
                                                job.source.takeIf { it.isNotBlank() },
                                                if (job.followUpOn == null) "applied ${job.appliedOn.format(dateFmt)}" else null
                                            ).joinToString(" · "),
                                            trailing = job.followUpOn?.format(dateFmt),
                                            trailingColor = if (overdue) L.Danger else L.Gold,
                                            onClick = { sheetId = job.id },
                                            end = endSlot
                                        )
                                    }
                                    if (rows.size > visible.size) {
                                        LGroupDivider()
                                        LGroupRow(
                                            title = "More (${rows.size - visible.size})",
                                            onClick = { expanded = "$expanded|$key" }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        LSheet(
            title = "Add job",
            onDismiss = { adding = false; paste = "" },
            primary = "Save",
            onPrimary = {
                viewModel.savePasted(paste)
                adding = false
                paste = ""
            },
            primaryEnabled = paste.isNotBlank()
        ) {
            Text(
                "Easiest: say it, like \"Applied to Google, follow up Friday\". Or paste a posting or link here.",
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted
            )
            LField(paste, { paste = it }, "Paste", singleLine = false, minLines = 3)
        }
    }

    sheetId?.let { id ->
        val job = state.jobs.firstOrNull { it.id == id }
        if (job == null) {
            if (!state.loading) LaunchedEffect(id) { sheetId = null }
        } else {
            JobSheet(
                job = job,
                onDismiss = { sheetId = null },
                onSave = { viewModel.save(it); sheetId = null },
                onDelete = { viewModel.delete(job.id); sheetId = null }
            )
        }
    }
}

@Composable
private fun JobSheet(
    job: JobApplication,
    onDismiss: () -> Unit,
    onSave: (JobApplication) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    var company by rememberSaveable(job.id) { mutableStateOf(job.company) }
    var title by rememberSaveable(job.id) { mutableStateOf(job.title) }
    var url by rememberSaveable(job.id) { mutableStateOf(job.url) }
    var site by rememberSaveable(job.id) { mutableStateOf(job.source) }
    var location by rememberSaveable(job.id) { mutableStateOf(job.location) }
    var extraDates by rememberSaveable(job.id) { mutableStateOf(job.extraDates) }
    var notes by rememberSaveable(job.id) { mutableStateOf(job.notes) }
    var contact by rememberSaveable(job.id) { mutableStateOf(job.contact) }
    var statusName by rememberSaveable(job.id) { mutableStateOf(job.status.name) }
    var applied by rememberSaveable(job.id) { mutableStateOf(job.appliedOn.toEpochDay()) }
    var follow by rememberSaveable(job.id) { mutableStateOf(job.followUpOn?.toEpochDay() ?: Long.MIN_VALUE) }
    var more by rememberSaveable(job.id) {
        mutableStateOf(listOf(job.source, job.url, job.location, job.contact, job.extraDates).any { it.isNotBlank() })
    }
    val followDate = if (follow == Long.MIN_VALUE) null else LocalDate.ofEpochDay(follow)
    val today = LocalDate.now()
    val quick = listOf(today.plusDays(3), today.plusDays(7), today.plusDays(14))

    LItemSheet(
        title = job.company.ifBlank { "Job" },
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            onSave(
                job.copy(
                    company = company.trim().ifBlank { job.company },
                    title = title.trim().ifBlank { job.title },
                    url = url.trim(),
                    source = site.trim(),
                    location = location.trim(),
                    extraDates = extraDates.trim(),
                    notes = notes.trim(),
                    contact = contact.trim(),
                    status = JobApplicationStatus.valueOf(statusName),
                    appliedOn = LocalDate.ofEpochDay(applied),
                    followUpOn = followDate
                )
            )
        },
        onDelete = onDelete
    ) {
        Text("Stage", style = MaterialTheme.typography.titleSmall, color = L.Ink, modifier = Modifier.semantics { heading() })
        ChipsRow {
            JobApplicationStatus.entries.forEach { s ->
                LChip(s.label(), selected = statusName == s.name, onClick = { statusName = s.name })
            }
        }
        Text("Follow up", style = MaterialTheme.typography.titleSmall, color = L.Ink, modifier = Modifier.semantics { heading() })
        ChipsRow {
            LChip("In 3 days", selected = followDate == quick[0], onClick = { follow = quick[0].toEpochDay() })
            LChip("In 1 week", selected = followDate == quick[1], onClick = { follow = quick[1].toEpochDay() })
            LChip("In 2 weeks", selected = followDate == quick[2], onClick = { follow = quick[2].toEpochDay() })
            DatePickChip(
                followDate ?: quick[1],
                selected = followDate != null && followDate !in quick,
                onDate = { follow = it.toEpochDay() },
                label = if (followDate != null && followDate !in quick) "On ${followDate.format(dateFmt)}" else "Pick date"
            )
            if (followDate != null) LChip("Clear", selected = false, onClick = { follow = Long.MIN_VALUE })
        }
        LField(company, { company = it }, "Company")
        LField(title, { title = it }, "Role")
        LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Text("Applied", style = MaterialTheme.typography.titleSmall, color = L.Ink, modifier = Modifier.semantics { heading() })
        ChipsRow {
            DatePickChip(
                LocalDate.ofEpochDay(applied),
                selected = true,
                onDate = { applied = it.toEpochDay() },
                label = LocalDate.ofEpochDay(applied).format(dateFmt)
            )
        }
        ChipsRow {
            LChip(if (more) "Fewer details" else "More details", selected = more, onClick = { more = !more })
        }
        if (more) {
            LField(site, { site = it }, "Application site")
            LField(url, { url = it }, "Link")
            if (url.isNotBlank()) {
                LGhostButton("Open link", onClick = {
                    val target = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                })
            }
            LField(location, { location = it }, "Location")
            LField(contact, { contact = it }, "Contact")
            LField(extraDates, { extraDates = it }, "Other dates", singleLine = false, minLines = 2)
        }
    }
}
