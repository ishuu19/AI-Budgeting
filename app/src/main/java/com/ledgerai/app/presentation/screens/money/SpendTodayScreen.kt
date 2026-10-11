package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.finance.SpendGuideResult
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.model.SpeculationConfidence
import com.ledgerai.app.domain.model.SpeculationDirection
import com.ledgerai.app.domain.model.SpendSpeculation
import com.ledgerai.app.presentation.components.L
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
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.theme.IncomeGreen
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.presentation.screens.transactions.shortDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val guide: SpendGuideResult? = null,
    val spentToday: Double = 0.0,
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
    private val repo: SpendGuideRepository,
    private val transactions: TransactionRepository
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
                val today = LocalDate.now()
                val guide = repo.computeTodayGuide(today)
                val spent = transactions.getTransactionsForMonth(today.year, today.monthValue).first()
                    .filter { it.date == today && it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }
                _state.update { it.copy(guide = guide, spentToday = spent, isLoading = false) }
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
        if (s.specLabel.isBlank() || amount == null || amount <= 0.0) return
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

    LScreen(
        title = "Safe to spend",
        onBack = onBack,
        action = { LToolButton(Icons.Filled.Edit, "Plan spend", onClick = viewModel::openSpeculate) }
    ) {
        when {
            guide != null -> {
                item(key = "hero") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LHero(
                            label = "Safe to spend",
                            value = money(guide.guideAmount),
                            valueColor = if (guide.status == SpendGuideStatus.OVER) L.Danger else L.OnBox
                        )
                        SpendGauge(state.spentToday, guide.guideAmount, guide.hardLimit, guide.status)
                    }
                }
                if (guide.reasons.isNotEmpty()) {
                    item(key = "reasons-header") { LSection("Why") }
                    item(key = "reasons") {
                        LGroup {
                            guide.reasons.forEachIndexed { index, reason ->
                                if (index > 0) LGroupDivider()
                                LGroupRow(title = reason)
                            }
                        }
                    }
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
                                sub = (if (income) "Income" else "Spending") + " · " + shortDate(spec.expectedDate),
                                trailing = (if (income) "+" else "") + money(spec.amount),
                                trailingColor = if (income) IncomeGreen else L.OnBox
                            )
                        }
                    }
                }
            }
        } else if (guide != null) {
            item(key = "planned-empty") { LEmpty(Icons.AutoMirrored.Filled.TrendingUp, "No planned spend") }
        }
    }

    if (state.showSpeculate) {
        LSheet(
            title = "Plan spend",
            onDismiss = viewModel::closeSpeculate,
            primary = "Save",
            onPrimary = viewModel::saveSpeculation,
            primaryEnabled = state.specLabel.isNotBlank() && (state.specAmount.toDoubleOrNull() ?: 0.0) > 0.0
        ) {
            DecimalField(state.specAmount, viewModel::setSpecAmount, "Amount")
            LField(state.specLabel, viewModel::setSpecLabel, "What for")
            LSegments(
                options = SpeculationDirection.entries,
                selected = state.specDirection,
                label = { if (it == SpeculationDirection.INCOME) "Income" else "Spending" },
                onSelect = viewModel::setSpecDirection
            )
            DatePickChip(
                date = state.specDate,
                selected = true,
                onDate = viewModel::setSpecDate,
                label = shortDate(state.specDate)
            )
            state.specError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger)
            }
        }
    }
}

@Composable
private fun SpendGauge(spent: Double, safe: Double, limit: Double, status: SpendGuideStatus) {
    val max = maxOf(spent, limit, safe, 1.0)
    val fill = when (status) {
        SpendGuideStatus.OVER -> L.Danger
        SpendGuideStatus.NEAR -> L.Highlight
        SpendGuideStatus.UNDER -> L.Gold
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(L.RadiusPill)
                    .background(L.BoxDeep)
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth((spent / max).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(L.RadiusPill)
                    .background(fill)
            )
            GaugeTick(safe / max, L.Gold)
            GaugeTick(limit / max, L.Ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Safe ${money(safe)}", style = MaterialTheme.typography.labelSmall, color = L.OnBoxMuted)
            Text("Limit ${money(limit)}", style = MaterialTheme.typography.labelSmall, color = L.OnBoxMuted)
        }
    }
}

@Composable
private fun GaugeTick(fraction: Double, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 0.98f)).wrapContentWidth(Alignment.End)) {
        Box(Modifier.size(width = 2.dp, height = 12.dp).background(color))
    }
}
