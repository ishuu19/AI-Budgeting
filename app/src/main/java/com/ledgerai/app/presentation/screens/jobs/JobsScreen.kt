package com.ledgerai.app.presentation.screens.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ledgerai.app.presentation.components.LButton
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
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.rememberVoiceInput
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    private val lexicon: com.ledgerai.app.data.ai.JobLexiconProvider,
    private val ai: com.ledgerai.app.data.repository.AiRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    private val _reading = MutableStateFlow(false)
    val reading: StateFlow<Boolean> = _reading.asStateFlow()

    /** Reads the words in job screenshots on the phone. [onText] gets null when nothing was readable. */
    fun readPhotos(uris: List<Uri>, onText: (String?) -> Unit) {
        viewModelScope.launch {
            _reading.value = true
            val pages = uris.mapNotNull { uri ->
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decode(uri) }
                    ?: return@mapNotNull null
                val lines = com.ledgerai.app.data.notebook.ScreenReader.ocr(bitmap)
                bitmap.recycle()
                lines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
            }
            _reading.value = false
            onText(pages.joinToString("\n\n").ifBlank { null })
        }
    }

    private fun decode(uri: Uri): android.graphics.Bitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2_400) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

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
            var parsedList = com.ledgerai.app.data.ai.JobPasteParser.parseMany(text, today, lexicon.lexicon)
            // Rules first. The cloud is asked only when one posting gave neither company nor role.
            val only = parsedList.singleOrNull()
            if (only != null && only.company.isBlank() && only.title.isBlank() && ai.isAiAvailable()) {
                val found = runCatching { ai.parseVoiceIntents("I applied for this job: " + text.take(600)).getOrNull() }
                    .getOrNull()?.filterIsInstance<com.ledgerai.app.data.ai.ParsedIntent.Job>()?.firstOrNull()
                if (found != null) {
                    parsedList = listOf(
                        only.copy(
                            company = found.company.takeUnless { it.equals("Company", true) }.orEmpty(),
                            title = found.title.takeUnless { it.equals("Role", true) }.orEmpty(),
                        )
                    )
                }
            }
            val apps = parsedList.map { p ->
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

private val Funnel = listOf(
    JobApplicationStatus.APPLIED,
    JobApplicationStatus.SCREENING,
    JobApplicationStatus.INTERVIEW,
    JobApplicationStatus.OFFER,
)

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

private fun reached(jobs: List<JobApplication>, stage: JobApplicationStatus): Int {
    if (stage == JobApplicationStatus.APPLIED) return jobs.size
    val need = Funnel.indexOf(stage)
    return jobs.count { Funnel.indexOf(it.status) >= need }
}

private fun dueText(day: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, day)
    return when {
        days < 0 -> if (days == -1L) "1 day overdue" else "${-days} days overdue"
        days == 0L -> "Due today"
        else -> "Due ${day.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
}

/**
 * Jobs segment. Follow-ups are on the main calendar. Here: the job that needs a follow-up,
 * a funnel that filters the list, then the jobs.
 */
@Composable
fun JobsScreen(
    openId: Long? = null,
    onOpened: () -> Unit = {},
    utterance: String = "",
    onUtteranceConsumed: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var paste by rememberSaveable { mutableStateOf("") }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf<JobApplicationStatus?>(null) }
    var expanded by rememberSaveable { mutableStateOf("") }
    var listen by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    LaunchedEffect(openId) {
        if (openId != null) {
            sheetId = openId
            onOpened()
        }
    }
    LaunchedEffect(utterance) {
        if (utterance.isNotBlank()) {
            paste = utterance
            adding = true
            onUtteranceConsumed()
        }
    }

    val shown = state.jobs.filter { job -> filter == null || job.status == filter }

    LScreen(
        title = "Jobs",
        onBack = onBack,
        fab = { LFab(Icons.Default.Add, onClick = { paste = ""; adding = true }, label = "New job") }
    ) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.JOB, onChanged = viewModel::load) }
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = viewModel::load) }
            state.jobs.isEmpty() -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LEmpty(Icons.Default.Work, "No jobs yet")
                    LToolButton(Icons.Filled.Mic, "Speak", { listen = true; paste = ""; adding = true })
                }
            }
            else -> {
                val due = state.jobs
                    .filter { it.status in ActiveStages && it.followUpOn?.isAfter(today) == false }
                    .sortedBy { it.followUpOn?.toEpochDay() ?: 0L }
                val first = due.firstOrNull()
                if (first != null) {
                    item(key = "due") {
                        val nextStage = first.status.next()
                        LHeroCard(
                            label = "Follow up",
                            title = first.company.ifBlank { first.title },
                            sub = listOfNotNull(
                                first.title.takeIf { it.isNotBlank() && first.company.isNotBlank() },
                                first.followUpOn?.let { dueText(it, today) },
                                if (due.size > 1) "+${due.size - 1} more" else null
                            ).joinToString(" · "),
                            onClick = { sheetId = first.id },
                            actions = {
                                if (nextStage != null) {
                                    LButton(
                                        "→ ${nextStage.label()}",
                                        { viewModel.setStatus(first, nextStage) },
                                        Modifier.weight(1f)
                                    )
                                }
                                LGhostButton(
                                    "+1 week",
                                    { viewModel.save(first.copy(followUpOn = today.plusDays(7))) },
                                    Modifier.weight(1f)
                                )
                            }
                        )
                    }
                }
                item(key = "funnel") {
                    JobFunnel(state.jobs, filter) { stage ->
                        filter = if (filter == stage) null else stage
                    }
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
                                LSection(stage.label())
                                LGroup {
                                    visible.forEachIndexed { i, job ->
                                        if (i > 0) LGroupDivider()
                                        val ns = job.status.next()
                                        val overdue = job.status in ActiveStages && job.followUpOn?.isBefore(today) == true
                                        LGroupRow(
                                            title = job.company.ifBlank { job.title },
                                            sub = listOfNotNull(
                                                job.title.takeIf { it.isNotBlank() && job.company.isNotBlank() },
                                                job.location.takeIf { it.isNotBlank() }
                                            ).joinToString(" · ").ifBlank { null },
                                            trailing = job.followUpOn?.let { dueText(it, today) },
                                            trailingColor = if (overdue) L.Danger else L.OnBoxMuted,
                                            onClick = { sheetId = job.id },
                                            end = {
                                                if (ns != null) LTextLink("→ ${ns.label()}", onClick = { viewModel.setStatus(job, ns) })
                                            }
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
        val voice = rememberVoiceInput { paste = it }
        LaunchedEffect(Unit) {
            if (listen) {
                listen = false
                voice.toggle()
            }
        }
        LSheet(
            title = "New job",
            onDismiss = { adding = false; paste = "" },
            primary = "Save",
            onPrimary = {
                viewModel.savePasted(paste)
                adding = false
                paste = ""
            },
            primaryEnabled = paste.isNotBlank()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LField(paste, { paste = it }, "Link or posting", Modifier.weight(1f), singleLine = false, minLines = 3)
                LToolButton(
                    Icons.Filled.Mic,
                    if (voice.listening) "Stop" else "Speak",
                    voice.toggle,
                    active = voice.listening
                )
            }
            val reading by viewModel.reading.collectAsState()
            var unreadable by remember { mutableStateOf(false) }
            val photos = com.ledgerai.app.presentation.components.rememberPhotoActions(max = 4) { uris ->
                unreadable = false
                viewModel.readPhotos(uris) { text ->
                    if (text == null) unreadable = true
                    else paste = listOf(paste, text).filter { it.isNotBlank() }.joinToString("\n\n")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                com.ledgerai.app.presentation.components.PhotoButton(Icons.Filled.PhotoCamera, "Camera", photos.shoot)
                com.ledgerai.app.presentation.components.PhotoButton(Icons.Filled.PhotoLibrary, "Screenshot", photos.pick)
            }
            if (reading) Text("Reading the photo…", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            if (unreadable) Text("Couldn't read any text in that photo.", style = MaterialTheme.typography.bodyMedium, color = L.Danger)
            if (paste.isNotBlank() && !reading) {
                Text("Check the words, then Save. Company, role and dates are picked out from them.", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
            }
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
private fun JobFunnel(
    jobs: List<JobApplication>,
    selected: JobApplicationStatus?,
    onSelect: (JobApplicationStatus) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Funnel.forEachIndexed { index, stage ->
            val on = selected == stage
            val count = jobs.count { it.status == stage }
            val percent = if (index == 0) null else {
                val base = reached(jobs, Funnel[index - 1])
                if (base == 0) 0 else reached(jobs, stage) * 100 / base
            }
            val ink = if (on) L.OnAccent else L.Ink
            val muted = if (on) L.OnAccent else L.InkMuted
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(L.Radius))
                    .background(if (on) L.Accent else L.Box)
                    .border(1.dp, if (on) L.Accent else L.Line, RoundedCornerShape(L.Radius))
                    .clickable { onSelect(stage) }
                    .padding(8.dp)
                    .semantics { contentDescription = "${stage.label()}, $count" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
                Text(
                    stage.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (percent != null) {
                    Text("$percent%", style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
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
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
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
    var editing by rememberSaveable(job.id) { mutableStateOf(false) }
    var more by rememberSaveable(job.id) { mutableStateOf(false) }
    val followDate = if (follow == Long.MIN_VALUE) null else LocalDate.ofEpochDay(follow)
    val today = LocalDate.now()
    val quick = listOf(today.plusDays(3), today.plusDays(7), today.plusDays(14))
    val status = runCatching { JobApplicationStatus.valueOf(statusName) }.getOrDefault(job.status)
    val changed = status != job.status ||
        followDate != job.followUpOn ||
        company.trim() != job.company ||
        title.trim() != job.title ||
        notes.trim() != job.notes ||
        url.trim() != job.url ||
        site.trim() != job.source ||
        location.trim() != job.location ||
        contact.trim() != job.contact ||
        extraDates.trim() != job.extraDates ||
        LocalDate.ofEpochDay(applied) != job.appliedOn

    fun save() {
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
                status = status,
                appliedOn = LocalDate.ofEpochDay(applied),
                followUpOn = followDate
            )
        )
    }

    LItemSheet(
        title = job.company.ifBlank { job.title }.ifBlank { "Job" },
        onDismiss = onDismiss,
        primary = if (editing || changed) "Save" else "Edit job",
        onPrimary = { if (editing || changed) save() else editing = true },
        onDelete = onDelete
    ) {
        StageStepper(status) { statusName = it.name }
        if (status == JobApplicationStatus.REJECTED || status == JobApplicationStatus.WITHDRAWN) {
            Text(status.label(), style = MaterialTheme.typography.bodyMedium, color = L.Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Rejected",
                style = MaterialTheme.typography.labelLarge,
                color = L.Danger,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { statusName = JobApplicationStatus.REJECTED.name }
                    .wrapContentHeight(Alignment.CenterVertically)
            )
            Text(
                "Withdrawn",
                style = MaterialTheme.typography.labelLarge,
                color = L.InkMuted,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { statusName = JobApplicationStatus.WITHDRAWN.name }
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
        LGroupRow(
            title = "Follow up",
            trailing = followDate?.let { dueText(it, today) } ?: "None",
            onClick = { editing = true }
        )
        if (editing) {
            LField(company, { company = it }, "Company")
            LField(title, { title = it }, "Role")
            LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
            Text("Applied", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            ChipsRow {
                DatePickChip(
                    LocalDate.ofEpochDay(applied),
                    selected = true,
                    onDate = { applied = it.toEpochDay() },
                    label = LocalDate.ofEpochDay(applied).format(dateFmt)
                )
            }
            Text("Follow up", style = MaterialTheme.typography.titleSmall, color = L.Ink)
            ChipsRow {
                LChip("In 3 days", selected = followDate == quick[0], onClick = { follow = quick[0].toEpochDay() })
                LChip("In 1 week", selected = followDate == quick[1], onClick = { follow = quick[1].toEpochDay() })
                LChip("In 2 weeks", selected = followDate == quick[2], onClick = { follow = quick[2].toEpochDay() })
                DatePickChip(
                    followDate ?: quick[1],
                    selected = followDate != null && followDate !in quick,
                    onDate = { follow = it.toEpochDay() },
                    label = if (followDate != null && followDate !in quick) dueText(followDate, today) else "Pick date"
                )
                if (followDate != null) LChip("Clear", selected = false, onClick = { follow = Long.MIN_VALUE })
            }
            LGroupRow(
                title = "More",
                onClick = { more = !more },
                end = {
                    Icon(
                        if (more) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                        contentDescription = if (more) "Fewer details" else "More",
                        tint = L.InkMuted
                    )
                }
            )
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
}

@Composable
private fun StageStepper(current: JobApplicationStatus, onPick: (JobApplicationStatus) -> Unit) {
    val index = Funnel.indexOf(current)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Funnel.forEachIndexed { i, stage ->
            val on = index >= i && index >= 0
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable { onPick(stage) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (on) L.Accent else androidx.compose.ui.graphics.Color.Transparent)
                        .border(1.dp, if (on) L.Accent else L.Line, CircleShape)
                )
                Text(
                    stage.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stage == current) L.Ink else L.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
