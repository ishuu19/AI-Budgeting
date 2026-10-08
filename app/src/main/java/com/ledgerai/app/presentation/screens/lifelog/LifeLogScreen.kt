package com.ledgerai.app.presentation.screens.lifelog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.local.room.CheckinWindowEntity
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.domain.model.CheckinWindowState
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class LifeLogViewModel @Inject constructor(
    private val repo: LifeLogRepository
) : ViewModel() {
    private val _windows = MutableStateFlow<List<CheckinWindowEntity>>(emptyList())
    val windows: StateFlow<List<CheckinWindowEntity>> = _windows.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureWindowsForDay(LocalDate.now())
            repo.observeDay(LocalDate.now()).collect { _windows.value = it }
        }
    }
}

@Composable
fun LifeLogScreen(viewModel: LifeLogViewModel = hiltViewModel()) {
    val windows by viewModel.windows.collectAsState()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val gaps = windows.filter { it.state == CheckinWindowState.GAP || it.state == CheckinWindowState.PENDING }

    LScreen(title = "Log") {
        items(gaps) { w ->
            LRow(
                title = "${fmt.format(w.startAt)} – ${fmt.format(w.endAt)}",
                sub = if (w.state == CheckinWindowState.GAP) "Gap" else "Pending",
                trailingColor = L.Danger
            )
        }
        if (windows.isEmpty()) {
            item { LEmpty(Icons.Default.Timeline, "No gaps") }
        }
    }
}
