package com.ledgerai.app.presentation.screens.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LCurrency
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.money.MutedLine
import com.ledgerai.app.presentation.screens.money.OptionalDateField
import com.ledgerai.app.presentation.screens.transactions.amountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    fun addGoal(name: String, target: Double, targetDate: LocalDate?, location: String = "") {
        viewModelScope.launch {
            goalRepo.insert(
                Goal(name = name, targetAmount = target, targetDate = targetDate, location = location)
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

    fun updateGoal(goal: Goal, name: String, target: Double, targetDate: LocalDate?, location: String) {
        viewModelScope.launch {
            goalRepo.update(
                goal.copy(
                    name = name,
                    targetAmount = target,
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

// ─── Section ──────────────────────────────────────────────────────────────────

private val DeadlineFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

enum class GoalFilter(val label: String) { ACTIVE("Active"), DONE("Done") }

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

private fun isLate(goal: Goal, today: LocalDate): Boolean =
    !goal.isCompleted && goal.targetDate?.isBefore(today) == true

private fun goalSubline(goal: Goal, today: LocalDate): String? {
    val date = goal.targetDate
    val pace = monthlyPace(goal, today)
    return when {
        pace != null -> "${money(pace)}/mo"
        isLate(goal, today) -> "Late"
        date != null -> date.format(DeadlineFormat)
        else -> null
    }
}

/** Goals section for the Money Plan segment: soonest deadline first, one-tap add on every row. */
fun LazyListScope.goalItems(
    goals: List<Goal>,
    filter: GoalFilter,
    onFilter: (GoalFilter) -> Unit,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Goal) -> Unit,
    onContribute: (Goal) -> Unit
) {
    val visible = when (filter) {
        GoalFilter.ACTIVE -> goals.filter { !it.isCompleted }.sortedWith(activeGoalOrder)
        GoalFilter.DONE -> goals.filter { it.isCompleted }
    }
    item(key = "goals-header") { LSection("Goals", action = "Add", onAction = onAdd) }
    if (goals.isNotEmpty()) {
        item(key = "goals-summary") {
            MutedLine("Saved ${money(goals.sumOf { it.savedAmount })} of ${money(goals.sumOf { it.targetAmount })}")
        }
        item(key = "goals-chips") {
            LKindChips(GoalFilter.entries.toList(), filter, { it.label }, onFilter)
        }
    }
    if (visible.isEmpty()) {
        item(key = "goals-empty") {
            LEmpty(
                Icons.Filled.Flag,
                if (filter == GoalFilter.DONE && goals.isNotEmpty()) "No finished goals yet" else "Say: save 2000 for a trip by June"
            )
        }
    } else {
        item(key = "goals-group") {
            LimitedGroup(visible, id = { it.id }, expandKey = "goals-${filter.name}") { goal ->
                GoalRow(goal, today, onEdit = { onEdit(goal) }, onContribute = { onContribute(goal) })
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, today: LocalDate, onEdit: () -> Unit, onContribute: () -> Unit) {
    val sub = goalSubline(goal, today)
    val late = isLate(goal, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Flag, contentDescription = null, tint = L.Gold, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = L.OnBox,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${goal.progressPercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = L.Gold,
                    maxLines = 1
                )
            }
            LProgress(goal.progressPercent / 100f, color = if (late) L.Danger else L.Gold)
            Text(
                "${money(goal.savedAmount)} of ${money(goal.targetAmount)}" + if (sub.isNullOrBlank()) "" else " · $sub",
                style = MaterialTheme.typography.bodySmall,
                color = if (late) L.Danger else L.OnBoxMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onContribute) {
            Icon(Icons.Filled.Add, contentDescription = "Add to ${goal.name}", tint = L.Primary)
        }
    }
}

@Composable
fun GoalSheet(
    existing: Goal?,
    onDismiss: () -> Unit,
    onSave: (String, Double, LocalDate?, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var targetText by rememberSaveable { mutableStateOf(existing?.targetAmount?.let(::amountInput) ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.targetDate) }
    var place by rememberSaveable { mutableStateOf(existing?.location ?: "") }

    val target = targetText.toDoubleOrNull()
    val canSave = name.isNotBlank() && target != null && target > 0
    val save = { if (canSave) onSave(name.trim(), target!!, date, place.trim()) }

    val body: @Composable ColumnScope.() -> Unit = {
        LField(name, { name = it }, "Name")
        DecimalField(targetText, { targetText = it }, "Target")
        OptionalDateField("Deadline", date) { date = it }
        LField(place, { place = it }, "Place")
    }
    if (existing != null) {
        LItemSheet(
            title = "Edit goal",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            onDelete = onDelete,
            content = body
        )
    } else {
        LSheet(
            title = "New goal",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            content = body
        )
    }
}

@Composable
fun ContributeSheet(
    goal: Goal,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
    onRemove: (Double) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
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
        ChipsRow {
            listOf(10.0, 25.0, 50.0, 100.0).forEach { preset ->
                LChip(
                    "+" + LCurrency.symbol + amountInput(preset),
                    amountText == amountInput(preset),
                    onClick = { amountText = amountInput(preset) }
                )
            }
            if (goal.remaining > 0.0) {
                val rest = kotlin.math.ceil(goal.remaining * 100) / 100
                LChip("Finish it", amountText == amountInput(rest), onClick = { amountText = amountInput(rest) })
            }
        }
        DecimalField(amountText, { amountText = it }, "Amount")
    }
}
