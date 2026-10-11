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
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
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
import java.time.temporal.ChronoUnit
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

private val HitFormat = DateTimeFormatter.ofPattern("MMM d")

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

private fun goalPaceLine(goal: Goal, today: LocalDate): String? {
    val date = goal.targetDate ?: return null
    val pace = monthlyPace(goal, today) ?: return null
    return "${money(pace)}/mo to hit ${date.format(HitFormat)}"
}

/** Average saved per 30 days since the goal was created, compared with the required pace. */
private fun trackTag(goal: Goal, today: LocalDate): String? {
    if (goal.isCompleted) return null
    if (isLate(goal, today)) return "Behind"
    val required = monthlyPace(goal, today) ?: return null
    val days = ChronoUnit.DAYS.between(goal.createdAt, today)
    if (days < 1) return null
    val actual = goal.savedAmount / days * 30.0
    return if (actual + 0.5 >= required) "On track" else "Behind"
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
    item(key = "goals-header") { LSection("Goals", action = "New goal", onAction = onAdd) }
    if (goals.isNotEmpty()) {
        val saved = goals.sumOf { it.savedAmount }
        val target = goals.sumOf { it.targetAmount }
        item(key = "goals-summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LProgress(if (target > 0.0) (saved / target).toFloat() else 0f)
                Text(
                    "${money(saved)} of ${money(target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted
                )
            }
        }
        item(key = "goals-chips") {
            LSegments(GoalFilter.entries.toList(), filter, { it.label }, onFilter)
        }
    }
    if (visible.isEmpty()) {
        item(key = "goals-empty") {
            LEmpty(
                Icons.Filled.Flag,
                if (filter == GoalFilter.DONE && goals.isNotEmpty()) "No finished goals yet" else "No goals yet"
            )
        }
    } else {
        item(key = "goals-group") {
            LimitedGroup(visible, id = { it.id }, expandKey = "goals-${filter.name}", limit = 3) { goal ->
                GoalRow(goal, today, onEdit = { onEdit(goal) }, onContribute = { onContribute(goal) })
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, today: LocalDate, onEdit: () -> Unit, onContribute: () -> Unit) {
    val pace = goalPaceLine(goal, today)
    val tag = trackTag(goal, today)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                "${money(goal.savedAmount)} of ${money(goal.targetAmount)}",
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                maxLines = 1
            )
            IconButton(onClick = onContribute) {
                Icon(Icons.Filled.Add, contentDescription = "Contribute to ${goal.name}", tint = L.Primary)
            }
        }
        LProgress(goal.progressPercent / 100f, color = if (isLate(goal, today)) L.Danger else L.Gold)
        if (tag != null || pace != null) {
            Text(
                listOfNotNull(tag, pace).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (tag == "Behind") L.Highlight else if (tag == "On track") L.Gold else L.OnBoxMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        primary = "Contribute",
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
                LChip(
                    "Complete (${money(rest)})",
                    amountText == amountInput(rest),
                    onClick = { amountText = amountInput(rest) }
                )
            }
        }
        DecimalField(amountText, { amountText = it }, "Amount")
    }
}
