package com.ledgerai.app.presentation.screens.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
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
    private val repo: JobRepository
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
            val apps = text.lines().filter { it.isNotBlank() }.map { line ->
                val (company, title) = parseJobLine(line)
                JobApplication(
                    company = company,
                    title = title,
                    url = Regex("https?://\\S+").find(line)?.value.orEmpty(),
                    appliedOn = today,
                    followUpOn = repo.defaultFollowUp(today)
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

private val StatusFilters: List<JobApplicationStatus?> = listOf(null) + JobApplicationStatus.entries

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

    LaunchedEffect(openId) {
        if (openId != null) {
            sheetId = openId
            onOpened()
        }
    }

    val shown = state.jobs.filter { filter == null || it.status == filter }

    LScreen(
        title = "Jobs",
        fab = { LFab(Icons.Default.Add, onClick = { adding = true }, label = "Add") }
    ) {
        when {
            state.loading -> item { LLoading() }
            state.error -> item { LError("Could not load", onRetry = viewModel::load) }
            state.jobs.isEmpty() -> item { LEmpty(Icons.Default.Work, "No jobs") }
            else -> {
                item(key = "hero") {
                    LHero(
                        label = "This week",
                        value = state.stats.appliedThisWeek.toString(),
                        sub = "${state.stats.responseRatePercent}% replied · ${state.stats.interviews} interviews"
                    )
                }
                item(key = "chips") {
                    LKindChips(StatusFilters, filter, { it?.label() ?: "All" }, { filter = it })
                }
                if (shown.isEmpty()) {
                    item(key = "none") { LEmpty(Icons.Default.Work, "None here") }
                } else {
                    item(key = "list") {
                        LGroup {
                            shown.forEachIndexed { i, job ->
                                if (i > 0) LGroupDivider()
                                LGroupRow(
                                    title = job.title,
                                    sub = "${job.company} · ${job.appliedOn.format(dateFmt)}",
                                    trailing = job.status.label(),
                                    onClick = { sheetId = job.id }
                                )
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
            LField(paste, { paste = it }, "Paste", singleLine = false, minLines = 4)
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
    var notes by rememberSaveable(job.id) { mutableStateOf(job.notes) }
    var contact by rememberSaveable(job.id) { mutableStateOf(job.contact) }
    var statusName by rememberSaveable(job.id) { mutableStateOf(job.status.name) }
    var applied by rememberSaveable(job.id) { mutableStateOf(job.appliedOn.toEpochDay()) }
    var follow by rememberSaveable(job.id) { mutableStateOf(job.followUpOn?.toEpochDay() ?: Long.MIN_VALUE) }
    val followDate = if (follow == Long.MIN_VALUE) null else LocalDate.ofEpochDay(follow)

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
        Text("Status", style = MaterialTheme.typography.titleSmall, color = L.Ink, modifier = Modifier.semantics { heading() })
        ChipsRow {
            JobApplicationStatus.entries.forEach { s ->
                LChip(s.label(), selected = statusName == s.name, onClick = { statusName = s.name })
            }
        }
        LField(company, { company = it }, "Company")
        LField(title, { title = it }, "Role")
        LField(url, { url = it }, "Link")
        if (url.isNotBlank()) {
            LGhostButton("Open link", onClick = {
                val target = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
            })
        }
        LField(contact, { contact = it }, "Contact")
        LField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Text("Dates", style = MaterialTheme.typography.titleSmall, color = L.Ink, modifier = Modifier.semantics { heading() })
        ChipsRow {
            DatePickChip(
                LocalDate.ofEpochDay(applied),
                selected = true,
                onDate = { applied = it.toEpochDay() },
                label = "Applied ${LocalDate.ofEpochDay(applied).format(dateFmt)}"
            )
            DatePickChip(
                followDate ?: LocalDate.now().plusDays(7),
                selected = followDate != null,
                onDate = { follow = it.toEpochDay() },
                label = followDate?.let { "Follow up ${it.format(dateFmt)}" } ?: "Follow up"
            )
            if (followDate != null) LChip("Clear", selected = false, onClick = { follow = Long.MIN_VALUE })
        }
    }
}
