package com.ledgerai.app.presentation.screens.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.schedule.FreeSlotOption
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.StudyPlan
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PlanUiState(
    val blocks: List<PlanBlock> = emptyList(),
    val nextBlock: PlanBlock? = null,
    val slotOptions: List<FreeSlotOption> = emptyList(),
    val showAdd: Boolean = false,
    val showStudy: Boolean = false,
    val showSlots: Boolean = false,
    val showHabit: Boolean = false,
    val habitText: String = "",
    val topic: String = "",
    val hours: String = "2",
    val deadline: LocalDate? = null,
    val pendingPlanId: Long? = null
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val habitRepo: HabitRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()
    private val timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm")

    init {
        viewModelScope.launch {
            planRepo.observeBlocks().collect { blocks ->
                val now = java.time.LocalDateTime.now()
                val next = blocks.filter { it.status == PlanBlockStatus.SCHEDULED && !it.endAt.isBefore(now) }
                    .minByOrNull { it.startAt }
                _state.update { it.copy(blocks = blocks, nextBlock = next) }
            }
        }
    }

    fun openAdd() = _state.update { it.copy(showAdd = true) }
    fun closeAdd() = _state.update { it.copy(showAdd = false) }
    fun openStudy() = _state.update { it.copy(showAdd = false, showStudy = true) }
    fun openHabit() = _state.update { it.copy(showAdd = false, showHabit = true, habitText = "") }
    fun setHabitText(v: String) = _state.update { it.copy(habitText = v) }

    fun dismissHabit() = _state.update { it.copy(showHabit = false, habitText = "") }

    fun saveHabit() {
        val parsed = habitRepo.parseQuick(_state.value.habitText) ?: return
        viewModelScope.launch {
            habitRepo.save(parsed)
            dismissHabit()
        }
    }
    fun setTopic(v: String) = _state.update { it.copy(topic = v) }
    fun setHours(v: String) = _state.update { it.copy(hours = v) }

    fun findSlots() {
        val s = _state.value
        val hours = s.hours.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val slots = planRepo.findStudySlots(hours, 50, s.deadline)
            val planId = planRepo.saveStudyPlan(
                StudyPlan(topic = s.topic.trim(), hoursTotal = hours, deadline = s.deadline)
            )
            _state.update {
                it.copy(showStudy = false, showSlots = true, slotOptions = slots, pendingPlanId = planId)
            }
        }
    }

    fun confirmSlots(selected: List<FreeSlotOption>) {
        val s = _state.value
        val planId = s.pendingPlanId ?: return
        viewModelScope.launch {
            planRepo.createBlocksFromSlots(planId, s.topic.trim(), selected)
            _state.update { it.copy(showSlots = false, slotOptions = emptyList(), pendingPlanId = null) }
        }
    }

    fun dismissSlots() = _state.update { it.copy(showSlots = false) }
}

@Composable
fun PlanScreen(
    onOpenFocus: (Long) -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val today = remember { LocalDate.now() }
    val tomorrow = today.plusDays(1)

    LScreen(
        title = "Plan",
        fab = {
            FloatingActionButton(
                onClick = viewModel::openAdd,
                containerColor = L.Box,
                contentColor = L.Gold
            ) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) {
        state.nextBlock?.let { next ->
            item {
                LHero(
                    label = "Next",
                    value = next.title,
                    sub = "${timeFmt.format(next.startAt)} · ${java.time.Duration.between(next.startAt, next.endAt).toMinutes()} min"
                )
            }
        }
        val grouped = state.blocks.groupBy { b ->
            when (b.startAt.toLocalDate()) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                else -> "Later"
            }
        }
        grouped.forEach { (section, rows) ->
            item { LSection(section) }
            items(rows) { block ->
                LRow(
                    title = block.title,
                    sub = timeFmt.format(block.startAt),
                    trailing = block.kind.name.lowercase(),
                    onClick = { onOpenFocus(block.id) }
                )
            }
        }
        if (state.blocks.isEmpty()) {
            item { LEmpty(Icons.Default.Timer, "No sessions") }
        }
    }

    if (state.showAdd) {
        LSheet(
            title = "Add",
            onDismiss = viewModel::closeAdd,
            primary = "Study",
            onPrimary = viewModel::openStudy,
            secondary = "Habit",
            onSecondary = viewModel::openHabit
        ) {}
    }

    if (state.showHabit) {
        LSheet(
            title = "Habit",
            onDismiss = viewModel::dismissHabit,
            primary = "Save",
            onPrimary = viewModel::saveHabit,
            primaryEnabled = state.habitText.isNotBlank()
        ) {
            OutlinedTextField(
                value = state.habitText,
                onValueChange = viewModel::setHabitText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Speak or type") },
                placeholder = { Text("gym 6pm") }
            )
        }
    }

    if (state.showStudy) {
        LSheet(
            title = "Study",
            onDismiss = { viewModel.dismissSlots() },
            primary = "Find time",
            onPrimary = viewModel::findSlots,
            primaryEnabled = state.topic.isNotBlank()
        ) {
            OutlinedTextField(
                value = state.topic,
                onValueChange = viewModel::setTopic,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Topic") }
            )
            OutlinedTextField(
                value = state.hours,
                onValueChange = viewModel::setHours,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hours") }
            )
        }
    }

    if (state.showSlots) {
        LSheet(
            title = "Times",
            onDismiss = viewModel::dismissSlots,
            primary = "Confirm all",
            onPrimary = { viewModel.confirmSlots(state.slotOptions) },
            primaryEnabled = state.slotOptions.isNotEmpty()
        ) {
            if (state.slotOptions.isEmpty()) {
                Text("No free slot found", color = L.InkMuted)
            } else {
                state.slotOptions.forEach { slot ->
                    LRow(
                        title = "${timeFmt.format(slot.start)} – ${timeFmt.format(slot.end)}",
                        sub = slot.reason,
                        onClick = { viewModel.confirmSlots(listOf(slot)) }
                    )
                }
            }
        }
    }
}
