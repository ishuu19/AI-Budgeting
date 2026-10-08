package com.ledgerai.app.presentation.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.screens.transactions.CategoryChipsRow
import com.ledgerai.app.presentation.screens.transactions.ConfirmDelete
import com.ledgerai.app.presentation.screens.transactions.amountInput

private val AlertSteps = listOf(50, 70, 80, 90)

@Composable
fun BudgetScreen(
    onNavigateToAi: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val daysSub = when {
        state.daysLeft <= 0 -> "Last day"
        state.daysLeft == 1 -> "1 day left"
        else -> "${state.daysLeft} days left"
    }

    Box(Modifier.fillMaxSize()) {
        LScreen(
            title = "Budget",
            onBack = onBack,
            action = {
                LGhostButton("Ask", onNavigateToAi, modifier = Modifier.width(84.dp).height(40.dp))
            },
            fab = { LFab(Icons.Filled.Add, onClick = { adding = true }) }
        ) {
            item(key = "hero") {
                LHero(
                    label = "Left",
                    value = money(state.totalRemaining),
                    sub = daysSub,
                    valueColor = if (state.totalRemaining < 0) L.Danger else L.OnBox
                )
            }

            item(key = "stats") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LStat(
                        "Daily",
                        money(state.dailyAllowance),
                        Modifier.weight(1f),
                        valueColor = if (state.dailyAllowance < 0) L.Danger else L.OnBox
                    )
                    LStat(
                        "Unbudgeted",
                        money(state.unbudgetedSpend),
                        Modifier.weight(1f),
                        valueColor = if (state.unbudgetedSpend > 0) L.Danger else L.OnBox
                    )
                }
            }

            if (state.canCopyLastMonth) {
                item(key = "copy") {
                    LButton("Copy", onClick = { viewModel.copyLastMonth() })
                }
            }

            if (!state.isLoading && state.budgets.isEmpty()) {
                item(key = "empty") { LEmpty(Icons.Filled.PieChart, "No budgets") }
            }

            items(state.budgets, key = { it.id }) { budget ->
                BudgetCard(
                    budget = budget,
                    advice = state.aiAdvice[budget.id],
                    daysLeft = state.daysLeft,
                    onClick = { editing = budget }
                )
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        )
    }

    if (adding) {
        BudgetSheet(
            existing = null,
            advice = null,
            onDismiss = { adding = false },
            onSave = { category, limit, threshold ->
                viewModel.addBudget(category, limit, threshold)
                adding = false
            }
        )
    }

    editing?.let { budget ->
        key(budget.id) {
            BudgetSheet(
                existing = budget,
                advice = state.aiAdvice[budget.id],
                onDismiss = { editing = null },
                onSave = { category, limit, threshold ->
                    viewModel.updateBudget(budget, category, limit, threshold)
                    editing = null
                },
                onAdvice = { viewModel.getAiAdvice(budget) },
                onDelete = {
                    viewModel.deleteBudget(budget)
                    editing = null
                }
            )
        }
    }
}

@Composable
private fun BudgetCard(budget: Budget, advice: String?, daysLeft: Int, onClick: () -> Unit) {
    val perDay = budget.remaining / daysLeft.coerceAtLeast(1)
    LCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                budget.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${money(budget.spent)} / ${money(budget.monthlyLimit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (budget.isOverBudget) L.Danger else L.OnBoxMuted
            )
        }
        LProgress(
            fraction = if (budget.monthlyLimit > 0) (budget.spent / budget.monthlyLimit).toFloat() else 0f,
            color = if (budget.isOverBudget) L.Danger else L.Gold
        )
        Text(
            "${money(perDay)}/day left",
            style = MaterialTheme.typography.bodySmall,
            color = if (perDay < 0) L.Danger else L.OnBoxMuted
        )
        if (!advice.isNullOrBlank()) {
            Text(advice, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
        }
    }
}

@Composable
private fun BudgetSheet(
    existing: Budget?,
    advice: String?,
    onDismiss: () -> Unit,
    onSave: (TransactionCategory, Double, Int) -> Unit,
    onAdvice: () -> Unit = {},
    onDelete: (() -> Unit)? = null
) {
    var category by remember { mutableStateOf(existing?.category ?: TransactionCategory.FOOD) }
    var limitText by remember { mutableStateOf(existing?.monthlyLimit?.let(::amountInput) ?: "") }
    var threshold by remember { mutableIntStateOf(existing?.alertThreshold ?: 80) }
    var confirmDelete by remember { mutableStateOf(false) }
    val limit = limitText.toDoubleOrNull()?.takeIf { it > 0 }
    val steps = remember { (AlertSteps + threshold).distinct().sorted() }

    LSheet(
        title = if (existing != null) "Edit" else "New",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { limit?.let { onSave(category, it, threshold) } },
        primaryEnabled = limit != null,
        secondary = if (existing != null && onDelete != null) "Delete" else null,
        onSecondary = { confirmDelete = true }
    ) {
        CategoryChipsRow(selected = category, onSelect = { category = it })

        LField(
            value = limitText,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1) limitText = cleaned
            },
            label = "Limit",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alert", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
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

    if (confirmDelete) {
        ConfirmDelete(
            onConfirm = { confirmDelete = false; onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
