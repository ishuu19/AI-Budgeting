package com.ledgerai.app.presentation.screens.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.JobStats
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class JobsUiState(
    val jobs: List<JobApplication> = emptyList(),
    val stats: JobStats = JobStats(0, 0, 0),
    val showAdd: Boolean = false,
    val pasteText: String = ""
)

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repo: JobRepository
) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAll().collect { list ->
                _state.update { it.copy(jobs = list, stats = repo.stats(list)) }
            }
        }
    }

    fun openAdd() = _state.update { it.copy(showAdd = true) }
    fun closeAdd() = _state.update { it.copy(showAdd = false, pasteText = "") }
    fun setPaste(v: String) = _state.update { it.copy(pasteText = v) }

    fun saveParsed() {
        val text = _state.value.pasteText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val lines = text.lines().filter { it.isNotBlank() }
            val today = LocalDate.now()
            val apps = lines.map { line ->
                val url = Regex("https?://\\S+").find(line)?.value ?: ""
                val parts = line.replace(url, "").split("—", "-", "|").map { it.trim() }.filter { it.isNotEmpty() }
                val company = parts.getOrElse(0) { line }
                val title = parts.getOrElse(1) { "Role" }
                JobApplication(
                    company = company,
                    title = title,
                    url = url,
                    appliedOn = today,
                    followUpOn = repo.defaultFollowUp(today)
                )
            }
            repo.saveBatch(apps)
            closeAdd()
        }
    }
}

@Composable
fun JobsScreen(viewModel: JobsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }

    LScreen(
        title = "Jobs",
        fab = {
            FloatingActionButton(
                onClick = viewModel::openAdd,
                containerColor = L.Box,
                contentColor = L.Gold
            ) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LStat("Week", "${state.stats.appliedThisWeek}", Modifier.weight(1f))
                LStat("Rate", "${state.stats.responseRatePercent}%", Modifier.weight(1f))
                LStat("Talks", "${state.stats.interviews}", Modifier.weight(1f))
            }
        }
        items(state.jobs) { job ->
            LRow(
                title = job.title,
                sub = job.company,
                trailing = job.status.name.lowercase(),
                onClick = {}
            )
        }
        if (state.jobs.isEmpty()) {
            item { LEmpty(Icons.Default.Work, "No jobs") }
        }
    }

    if (state.showAdd) {
        LSheet(
            title = "Job",
            onDismiss = viewModel::closeAdd,
            primary = "Save",
            onPrimary = viewModel::saveParsed,
            primaryEnabled = state.pasteText.isNotBlank()
        ) {
            OutlinedTextField(
                value = state.pasteText,
                onValueChange = viewModel::setPaste,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text("Paste") },
                minLines = 4
            )
        }
    }
}
