package com.ledgerai.app.presentation.screens.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LItemSheet
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.LimitedGroup
import com.ledgerai.app.presentation.screens.money.MutedLine
import com.ledgerai.app.presentation.screens.transactions.amountInput
import com.ledgerai.app.presentation.screens.transactions.spendIcon

private val AlertSteps = listOf(50, 70, 80, 90)

/** Budgets section for the Money Plan segment: most urgent (over, then fullest) first, with progress on every row. */
fun LazyListScope.budgetItems(
    state: BudgetUiState,
    onAdd: () -> Unit,
    onEdit: (Budget) -> Unit,
    onAsk: () -> Unit,
    onCopyLastMonth: () -> Unit
) {
    val ordered = state.budgets.sortedWith(
        compareByDescending<Budget> { it.isOverBudget }.thenByDescending { it.usagePercent }
    )
    item(key = "budgets-header") { LSection("Budgets", action = "Add", onAction = onAdd) }
    item(key = "budgets-summary") {
        MutedLine("Daily ${money(state.dailyAllowance)} · Unbudgeted ${money(state.unbudgetedSpend)}")
    }
    item(key = "budgets-actions") {
        ChipsRow {
            LChip("Ask AI", false, onClick = onAsk)
            if (state.canCopyLastMonth) LChip("Copy last month", false, onClick = onCopyLastMonth)
        }
    }
    if (!state.isLoading && ordered.isEmpty()) {
        item(key = "budgets-empty") { LEmpty(Icons.Filled.PieChart, "Say: budget 300 for food") }
    } else if (ordered.isNotEmpty()) {
        item(key = "budgets-group") {
            LimitedGroup(ordered, id = { it.id }, expandKey = "budgets") { budget ->
                BudgetRow(budget, state.daysLeft, onClick = { onEdit(budget) })
            }
        }
    }
}

@Composable
private fun BudgetRow(budget: Budget, daysLeft: Int, onClick: () -> Unit) {
    val perDay = budget.remaining / daysLeft.coerceAtLeast(1)
    val over = budget.isOverBudget
    val tint = when {
        over -> L.Danger
        budget.isNearLimit -> L.Highlight
        else -> L.Gold
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(spendIcon(budget.category), contentDescription = null, tint = L.Gold, modifier = Modifier.size(20.dp))
            Text(
                budget.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (over) "Over by ${money(-budget.remaining)}" else "${money(budget.remaining)} left",
                style = MaterialTheme.typography.titleSmall,
                color = if (over) L.Danger else L.OnBox
            )
        }
        LProgress(
            fraction = if (budget.monthlyLimit > 0) (budget.spent / budget.monthlyLimit).toFloat() else 0f,
            color = tint
        )
        Text(
            "${money(budget.spent)} of ${money(budget.monthlyLimit)}" + if (over) "" else " · ${money(perDay)}/day",
            style = MaterialTheme.typography.bodySmall,
            color = L.OnBoxMuted
        )
    }
}

/** Add or edit a budget. [taken] lists categories that already have a budget this month. */
@Composable
fun BudgetSheet(
    existing: Budget?,
    taken: Set<TransactionCategory>,
    advice: String?,
    onDismiss: () -> Unit,
    onSave: (TransactionCategory, Double, Int) -> Unit,
    onAdvice: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    val blocked = if (existing != null) taken - existing.category else taken
    val firstFree = TransactionCategory.entries.firstOrNull { it !in blocked } ?: TransactionCategory.FOOD
    var category by rememberSaveable { mutableStateOf(existing?.category ?: firstFree) }
    var limitText by rememberSaveable { mutableStateOf(existing?.monthlyLimit?.let(::amountInput) ?: "") }
    var threshold by rememberSaveable { mutableStateOf(existing?.alertThreshold ?: 80) }
    val limit = limitText.toDoubleOrNull()?.takeIf { it > 0 }
    val steps = (AlertSteps + (existing?.alertThreshold ?: 80)).distinct().sorted()
    val duplicate = category in blocked

    val body: @Composable ColumnScope.() -> Unit = {
        ChipsRow {
            TransactionCategory.entries.forEach { cat ->
                LChip(cat.displayName, cat == category, onClick = { category = cat })
            }
        }
        if (duplicate) {
            Text("Already has a budget", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
        }

        DecimalField(limitText, { limitText = it }, "Limit")

        Text("Alert at", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        ChipsRow {
            steps.forEach { pct ->
                LChip("$pct%", threshold == pct, onClick = { threshold = pct })
            }
        }

        if (existing != null) {
            if (!advice.isNullOrBlank()) {
                Text(advice, style = MaterialTheme.typography.bodyMedium, color = L.Ink)
            } else {
                LGhostButton("Advice", onClick = onAdvice)
            }
        }
    }

    val canSave = limit != null && !duplicate
    val save = { limit?.let { onSave(category, it, threshold) }; Unit }
    if (existing != null) {
        LItemSheet(
            title = "Edit budget",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            onDelete = onDelete,
            content = body
        )
    } else {
        LSheet(
            title = "New budget",
            onDismiss = onDismiss,
            primary = "Save",
            onPrimary = save,
            primaryEnabled = canSave,
            content = body
        )
    }
}
