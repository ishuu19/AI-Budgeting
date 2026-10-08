package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LStat
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.presentation.screens.transactions.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val guide: SpendGuideResult? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showSpeculate: Boolean = false,
    val specLabel: String = "",
    val specAmount: String = "",
    val specDirection: SpeculationDirection = SpeculationDirection.EXPENSE,
    val specDate: LocalDate = LocalDate.now().plusDays(7),
    val specError: String? = null
)

@HiltViewModel
class SpendTodayViewModel @Inject constructor(
    private val repo: SpendGuideRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    val speculations: StateFlow<List<SpendSpeculation>> = repo.observeSpeculations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val guide = repo.computeTodayGuide()
                _state.update { it.copy(guide = guide, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Could not load") }
            }
        }
    }

    fun openSpeculate() = _state.update {
        it.copy(showSpeculate = true, specError = null, specDate = LocalDate.now().plusDays(7))
    }
    fun closeSpeculate() = _state.update { it.copy(showSpeculate = false) }
    fun setSpecLabel(v: String) = _state.update { it.copy(specLabel = v, specError = null) }
    fun setSpecAmount(v: String) = _state.update { it.copy(specAmount = v, specError = null) }
    fun setSpecDirection(v: SpeculationDirection) = _state.update { it.copy(specDirection = v) }
    fun setSpecDate(v: LocalDate) = _state.update { it.copy(specDate = v) }

    fun saveSpeculation() {
        val s = _state.value
        val amount = s.specAmount.toDoubleOrNull()
        if (s.specLabel.isBlank() || amount == null || amount <= 0.0) {
            _state.update { it.copy(specError = "Enter a label and an amount above 0") }
            return
        }
        viewModelScope.launch {
            try {
                repo.addSpeculation(
                    SpendSpeculation(
                        label = s.specLabel.trim(),
                        amount = amount,
                        direction = s.specDirection,
                        expectedDate = s.specDate,
                        confidence = SpeculationConfidence.MEDIUM
                    )
                )
                _state.update { it.copy(showSpeculate = false, specLabel = "", specAmount = "", specError = null) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(specError = "Could not save") }
            }
        }
    }
}

@Composable
fun SpendTodayScreen(onBack: () -> Unit, viewModel: SpendTodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val speculations by viewModel.speculations.collectAsState()
    val guide = state.guide
    val statusColor = when (guide?.status) {
        SpendGuideStatus.OVER -> L.Danger
        SpendGuideStatus.NEAR -> L.Gold
        else -> L.OnBox
    }

    LScreen(
        title = "Safe today",
        onBack = onBack,
        action = { TextButton(onClick = viewModel::openSpeculate) { Text("Plan spend", color = L.Box) } }
    ) {
        when {
            guide != null -> {
                item(key = "hero") {
                    LHero(
                        label = "Safe today",
                        value = money(guide.guideAmount),
                        sub = "Buffer ${money(guide.buffer)}",
                        valueColor = if (guide.status == SpendGuideStatus.OVER) L.Danger else L.OnBox
                    )
                }
                if (guide.reasons.isNotEmpty()) {
                    item(key = "reasons") {
                        LGroup {
                            guide.reasons.forEachIndexed { index, reason ->
                                if (index > 0) LGroupDivider()
                                LGroupRow(title = reason)
                            }
                        }
                    }
                }
                item(key = "limit") {
                    LStat(
                        label = "Hard limit",
                        value = money(guide.hardLimit),
                        modifier = Modifier.fillMaxWidth(),
                        valueColor = statusColor
                    )
                }
            }
            state.isLoading -> item(key = "loading") { LLoading() }
            else -> item(key = "error") { LError(state.error ?: "Could not load", onRetry = viewModel::refresh) }
        }
        if (speculations.isNotEmpty()) {
            item(key = "planned-header") { LSection("Planned") }
            item(key = "planned") {
                LGroup {
                    speculations.sortedBy { it.expectedDate }.forEachIndexed { index, spec ->
                        androidx.compose.runtime.key(spec.id) {
                            if (index > 0) LGroupDivider()
                            val income = spec.direction == SpeculationDirection.INCOME
                            LGroupRow(
                                title = spec.label,
                                sub = (if (income) "Income" else "Expense") + " · " + shortDate(spec.expectedDate),
                                trailing = (if (income) "+" else "-") + money(spec.amount)
                            )
                        }
                    }
                }
            }
        } else if (guide != null) {
            item(key = "planned-empty") { LEmpty(Icons.AutoMirrored.Filled.TrendingUp, "Nothing planned") }
        }
    }

    if (state.showSpeculate) {
        LSheet(
            title = "Plan spend",
            onDismiss = viewModel::closeSpeculate,
            primary = "Save",
            onPrimary = viewModel::saveSpeculation
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip(
                    "Expense",
                    state.specDirection == SpeculationDirection.EXPENSE,
                    onClick = { viewModel.setSpecDirection(SpeculationDirection.EXPENSE) }
                )
                LChip(
                    "Income",
                    state.specDirection == SpeculationDirection.INCOME,
                    onClick = { viewModel.setSpecDirection(SpeculationDirection.INCOME) }
                )
                DatePickChip(
                    date = state.specDate,
                    selected = true,
                    onDate = viewModel::setSpecDate,
                    label = shortDate(state.specDate)
                )
            }
            LField(state.specLabel, viewModel::setSpecLabel, "Label")
            DecimalField(state.specAmount, viewModel::setSpecAmount, "Amount")
            state.specError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
            }
        }
    }
}

private fun Modifier.fillMaxWidth(): Modifier = this.then(Modifier.fillMaxWidth())
