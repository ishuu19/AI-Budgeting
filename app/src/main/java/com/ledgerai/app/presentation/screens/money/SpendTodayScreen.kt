package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.finance.SpendGuideResult
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
import com.ledgerai.app.domain.model.SpendSpeculation
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TodayUiState(
    val guide: SpendGuideResult? = null,
    val showSpeculate: Boolean = false,
    val specLabel: String = "",
    val specAmount: String = ""
)

@HiltViewModel
class SpendTodayViewModel @Inject constructor(
    private val repo: SpendGuideRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(guide = repo.computeTodayGuide()) }
        }
    }

    fun openSpeculate() = _state.update { it.copy(showSpeculate = true) }
    fun closeSpeculate() = _state.update { it.copy(showSpeculate = false) }
    fun setSpecLabel(v: String) = _state.update { it.copy(specLabel = v) }
    fun setSpecAmount(v: String) = _state.update { it.copy(specAmount = v) }

    fun saveSpeculation() {
        val s = _state.value
        val amount = s.specAmount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            repo.addSpeculation(
                SpendSpeculation(
                    label = s.specLabel.trim(),
                    amount = amount,
                    direction = SpeculationDirection.EXPENSE,
                    expectedDate = LocalDate.now().plusDays(7),
                    confidence = SpeculationConfidence.MEDIUM
                )
            )
            _state.update { it.copy(showSpeculate = false, specLabel = "", specAmount = "") }
            refresh()
        }
    }
}

@Composable
fun SpendTodayScreen(onBack: () -> Unit, viewModel: SpendTodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val guide = state.guide
    val statusColor = when (guide?.status) {
        SpendGuideStatus.OVER -> L.Danger
        SpendGuideStatus.NEAR -> L.Gold
        else -> L.OnBox
    }

    LScreen(title = "Today", onBack = onBack, action = {
        TextButton(onClick = viewModel::openSpeculate) { Text("Speculate", color = L.Box) }
    }) {
        item {
            LHero(
                label = "Safe today",
                value = guide?.let { "%.0f".format(it.guideAmount) } ?: "—",
                sub = guide?.let { "Buffer %.0f".format(it.buffer) }
            )
        }
        guide?.reasons?.forEach { reason ->
            item { LRow(title = reason, trailing = "") }
        }
        item {
            LStat(
                label = "Hard limit",
                value = guide?.let { "%.0f".format(it.hardLimit) } ?: "—",
                modifier = Modifier.fillMaxWidth(),
                valueColor = statusColor
            )
        }
        if (guide == null) {
            item { LEmpty(Icons.Default.TrendingUp, "Loading") }
        }
    }

    if (state.showSpeculate) {
        LSheet(
            title = "Speculate",
            onDismiss = viewModel::closeSpeculate,
            primary = "Save",
            onPrimary = viewModel::saveSpeculation,
            primaryEnabled = state.specLabel.isNotBlank() && state.specAmount.isNotBlank()
        ) {
            OutlinedTextField(
                value = state.specLabel,
                onValueChange = viewModel::setSpecLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label") }
            )
            OutlinedTextField(
                value = state.specAmount,
                onValueChange = viewModel::setSpecAmount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") }
            )
        }
    }
}
