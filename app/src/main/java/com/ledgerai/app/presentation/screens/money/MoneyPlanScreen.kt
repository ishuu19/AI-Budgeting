package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.VoiceSeg
import com.ledgerai.app.presentation.screens.budget.BudgetSheet
import com.ledgerai.app.presentation.screens.budget.BudgetViewModel
import com.ledgerai.app.presentation.screens.budget.budgetItems
import com.ledgerai.app.presentation.screens.forecast.ForecastViewModel
import com.ledgerai.app.presentation.screens.forecast.forecastItems
import com.ledgerai.app.presentation.screens.goals.ContributeSheet
import com.ledgerai.app.presentation.screens.goals.GoalFilter
import com.ledgerai.app.presentation.screens.goals.GoalSheet
import com.ledgerai.app.presentation.screens.goals.GoalsViewModel
import com.ledgerai.app.presentation.screens.goals.goalItems

/** Money > Plan: one hero (budget left), then Budgets (most urgent first), Goals and Forecast. Adding is voice first; sheets are the manual path. */
@Composable
fun MoneyPlanScreen(
    links: AppLinks,
    open: OpenItem?,
    onOpened: () -> Unit,
    budgetVm: BudgetViewModel = hiltViewModel(),
    goalsVm: GoalsViewModel = hiltViewModel(),
    forecastVm: ForecastViewModel = hiltViewModel()
) {
    val budgetState by budgetVm.uiState.collectAsState()
    val goals by goalsVm.goals.collectAsState()
    val forecast by forecastVm.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val today = rememberToday()

    var addingBudget by rememberSaveable { mutableStateOf(false) }
    var editingBudget by rememberSaveable { mutableStateOf(NO_ID) }
    var addingGoal by rememberSaveable { mutableStateOf(false) }
    var editingGoal by rememberSaveable { mutableStateOf(NO_ID) }
    var contributingGoal by rememberSaveable { mutableStateOf(NO_ID) }
    var goalFilter by rememberSaveable { mutableStateOf(GoalFilter.ACTIVE) }
    var forecastOpen by rememberSaveable { mutableStateOf(false) }

    SnackEffect(budgetState.snackbarMessage, snackbar) { budgetVm.clearSnackbar() }

    LaunchedEffect(open, budgetState.budgets, goals) {
        val request = open ?: return@LaunchedEffect
        when (request.kind) {
            OpenKind.Budget -> if (budgetState.budgets.any { it.id == request.id }) {
                editingBudget = request.id
                onOpened()
            }
            OpenKind.Goal -> goals.firstOrNull { it.id == request.id }?.let { goal ->
                goalFilter = if (goal.isCompleted) GoalFilter.DONE else GoalFilter.ACTIVE
                editingGoal = request.id
                onOpened()
            }
            else -> Unit
        }
    }
    OpenTimeout(open, onOpened)

    val daysSub = when {
        budgetState.daysLeft <= 0 -> "Last day"
        budgetState.daysLeft == 1 -> "1 day left"
        else -> "${budgetState.daysLeft} days left"
    }

    val overCount = budgetState.budgets.count { it.isOverBudget }
    val heroSub = if (overCount > 0) "$daysSub · $overCount over budget" else daysSub

    LScreen(title = "Plan", snackbarHost = { SnackbarHost(snackbar) }) {
        item(key = "hero") {
            LHero(
                label = "Budget left",
                value = money(budgetState.totalRemaining),
                sub = heroSub,
                valueColor = if (budgetState.totalRemaining < 0) L.Danger else L.OnBox
            )
        }
        budgetItems(
            state = budgetState,
            onAdd = { addingBudget = true },
            onEdit = { editingBudget = it.id },
            onAsk = { links.voice(VoiceSeg.Ask) },
            onCopyLastMonth = budgetVm::copyLastMonth
        )
        goalItems(
            goals = goals,
            filter = goalFilter,
            onFilter = { goalFilter = it },
            today = today,
            onAdd = { addingGoal = true },
            onEdit = { editingGoal = it.id },
            onContribute = { contributingGoal = it.id }
        )
        forecastItems(
            state = forecast,
            expanded = forecastOpen,
            onToggle = { forecastOpen = !forecastOpen },
            onPredict = forecastVm::generateForecast
        )
    }

    val taken = budgetState.budgets.map { it.category }.toSet()
    if (addingBudget) {
        BudgetSheet(
            existing = null,
            taken = taken,
            advice = null,
            onDismiss = { addingBudget = false },
            onSave = { category, limit, threshold ->
                budgetVm.addBudget(category, limit, threshold)
                addingBudget = false
            }
        )
    }
    budgetState.budgets.firstOrNull { it.id == editingBudget }?.let { budget ->
        key(budget.id) {
            BudgetSheet(
                existing = budget,
                taken = taken,
                advice = budgetState.aiAdvice[budget.id],
                onDismiss = { editingBudget = NO_ID },
                onSave = { category, limit, threshold ->
                    budgetVm.updateBudget(budget, category, limit, threshold)
                    editingBudget = NO_ID
                },
                onAdvice = { budgetVm.getAiAdvice(budget) },
                onDelete = {
                    budgetVm.deleteBudget(budget)
                    editingBudget = NO_ID
                }
            )
        }
    }

    if (addingGoal) {
        GoalSheet(
            existing = null,
            onDismiss = { addingGoal = false },
            onSave = { name, target, date, place ->
                goalsVm.addGoal(name, target, date, place)
                addingGoal = false
            }
        )
    }
    goals.firstOrNull { it.id == editingGoal }?.let { goal ->
        key(goal.id) {
            GoalSheet(
                existing = goal,
                onDismiss = { editingGoal = NO_ID },
                onSave = { name, target, date, place ->
                    goalsVm.updateGoal(goal, name, target, date, place)
                    editingGoal = NO_ID
                },
                onDelete = {
                    goalsVm.deleteGoal(goal)
                    editingGoal = NO_ID
                }
            )
        }
    }
    goals.firstOrNull { it.id == contributingGoal }?.let { goal ->
        key(goal.id) {
            ContributeSheet(
                goal = goal,
                onDismiss = { contributingGoal = NO_ID },
                onAdd = { amount ->
                    goalsVm.addSavings(goal, amount)
                    contributingGoal = NO_ID
                },
                onRemove = { amount ->
                    goalsVm.addSavings(goal, -amount)
                    contributingGoal = NO_ID
                }
            )
        }
    }
}
