package com.ledgerai.app.presentation.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.presentation.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class GoalsViewModel @Inject constructor(private val goalRepo: GoalRepository) : ViewModel() {

    val goals: StateFlow<List<Goal>> = goalRepo.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addGoal(name: String, target: Double, emoji: String, targetDate: LocalDate?, location: String = "") {
        viewModelScope.launch {
            goalRepo.insert(
                Goal(name = name, targetAmount = target, emoji = emoji, targetDate = targetDate, location = location)
            )
        }
    }

    fun addSavings(goal: Goal, amount: Double) {
        viewModelScope.launch {
            val saved = (goal.savedAmount + amount).coerceAtLeast(0.0)
            goalRepo.update(
                goal.copy(
                    savedAmount = saved,
                    isCompleted = saved >= goal.targetAmount
                )
            )
        }
    }

    fun updateGoal(goal: Goal, name: String, target: Double, emoji: String, targetDate: LocalDate?, location: String) {
        viewModelScope.launch {
            goalRepo.update(
                goal.copy(
                    name = name,
                    targetAmount = target,
                    emoji = emoji,
                    targetDate = targetDate,
                    location = location,
                    isCompleted = goal.savedAmount >= target
                )
            )
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { goalRepo.delete(goal) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val DeadlineFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

private enum class GoalFilter(val label: String) { ACTIVE("Active"), DONE("Done") }

private val activeGoalOrder = compareBy<Goal>(
    { it.targetDate ?: LocalDate.MAX },
    { it.progressPercent }
)

private fun monthsUntil(today: LocalDate, target: LocalDate): Int {
    val period = Period.between(today, target)
    val months = period.years * 12 + period.months + if (period.days > 0) 1 else 0
    return months.coerceAtLeast(1)
}

private fun monthlyPace(goal: Goal, today: LocalDate): Double? {
    val date = goal.targetDate ?: return null
    if (!date.isAfter(today) || goal.remaining <= 0.0) return null
    return goal.remaining / monthsUntil(today, date)
}

private fun goalSubline(goal: Goal, today: LocalDate): String? {
    val date = goal.targetDate
    val pace = monthlyPace(goal, today)
    return when {
        pace != null -> "${money(pace)}/mo"
        date != null && date.isBefore(today) && !goal.isCompleted -> "Late"
        date != null -> date.format(DeadlineFormat)
        else -> null
    }
}

@Composable
fun GoalsScreen(
    onBack: () -> Unit = {},
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val goals by viewModel.goals.collectAsState()
    var filter by remember { mutableStateOf(GoalFilter.ACTIVE) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var contributing by remember { mutableStateOf<Goal?>(null) }
    val today = LocalDate.now()

    val visible = remember(goals, filter) {
        when (filter) {
            GoalFilter.ACTIVE -> goals.filter { !it.isCompleted }.sortedWith(activeGoalOrder)
            GoalFilter.DONE -> goals.filter { it.isCompleted }
        }
    }
    val totalSaved = remember(goals) { goals.sumOf { it.savedAmount } }
    val totalTarget = remember(goals) { goals.sumOf { it.targetAmount } }
    val totalLeft = (totalTarget - totalSaved).coerceAtLeast(0.0)

    LScreen(
        title = "Goals",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { adding = true }) }
    ) {
        if (goals.isNotEmpty()) {
            item(key = "hero") {
                LHero(
                    label = "Left",
                    value = money(totalLeft),
                    sub = "${money(totalSaved)} of ${money(totalTarget)}"
                )
            }
        }

        item(key = "chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalFilter.entries.forEach { f ->
                    LChip(f.label, selected = filter == f, onClick = { filter = f })
                }
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") { LEmpty(Icons.Filled.Flag, "No goals") }
        } else {
            items(visible, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    today = today,
                    onEdit = { editing = goal },
                    onContribute = { contributing = goal }
                )
            }
        }
    }

    if (adding) {
        GoalSheet(
            existing = null,
            onDismiss = { adding = false },
            onSave = { name, target, date, place ->
                viewModel.addGoal(name, target, "🎯", date, place)
                adding = false
            }
        )
    }

    editing?.let { goal ->
        GoalSheet(
            existing = goal,
            onDismiss = { editing = null },
            onSave = { name, target, date, place ->
                viewModel.updateGoal(goal, name, target, goal.emoji, date, place)
                editing = null
            },
            onDelete = {
                viewModel.deleteGoal(goal)
                editing = null
            }
        )
    }

    contributing?.let { goal ->
        ContributeSheet(
            goal = goal,
            onDismiss = { contributing = null },
            onAdd = { amount ->
                viewModel.addSavings(goal, amount)
                contributing = null
            },
            onRemove = { amount ->
                viewModel.addSavings(goal, -amount)
                contributing = null
            }
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    today: LocalDate,
    onEdit: () -> Unit,
    onContribute: () -> Unit
) {
    val sub = goalSubline(goal, today)
    LCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = L.OnBox,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${money(goal.savedAmount)} / ${money(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = L.Gold,
                    maxLines = 1
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(L.Gold)
                    .clickable(onClick = onContribute),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = L.BoxDeep, modifier = Modifier.size(20.dp))
            }
        }
        LProgress(goal.progressPercent / 100f)
        if (!sub.isNullOrBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (sub == "Late") L.Danger else L.OnBoxMuted
            )
        }
    }
}

private fun parseDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()

@Composable
private fun GoalSheet(
    existing: Goal?,
    onDismiss: () -> Unit,
    onSave: (String, Double, LocalDate?, String) -> Unit,
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var targetText by remember { mutableStateOf(existing?.targetAmount?.toString() ?: "") }
    var dateText by remember { mutableStateOf(existing?.targetDate?.toString() ?: "") }
    var place by remember { mutableStateOf(existing?.location ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val target = targetText.toDoubleOrNull()
    val dateValid = dateText.isBlank() || parseDate(dateText) != null
    val canSave = name.isNotBlank() && target != null && target > 0 && dateValid

    LSheet(
        title = if (existing == null) "New goal" else "Edit goal",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { if (canSave) onSave(name.trim(), target!!, parseDate(dateText), place.trim()) },
        primaryEnabled = canSave,
        secondary = if (existing != null) "Delete" else null,
        onSecondary = { confirmDelete = true }
    ) {
        LField(name, { name = it }, "Name")
        LField(
            targetText,
            { targetText = it },
            "Target",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        LField(place, { place = it }, "Place")
        LField(dateText, { dateText = it }, "Deadline")
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = L.Page,
            title = { Text("Delete?", color = L.Ink) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = L.Box) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = L.InkMuted) }
            }
        )
    }
}

@Composable
private fun ContributeSheet(
    goal: Goal,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onRemove: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val canApply = amount != null && amount > 0

    LSheet(
        title = goal.name,
        onDismiss = onDismiss,
        primary = "Add",
        onPrimary = { if (canApply) onAdd(amount!!) },
        primaryEnabled = canApply,
        secondary = "Remove",
        onSecondary = { if (canApply) onRemove(amount!!) }
    ) {
        LField(
            amountText,
            { amountText = it },
            "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}
