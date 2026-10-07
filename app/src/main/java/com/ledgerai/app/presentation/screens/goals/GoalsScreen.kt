package com.ledgerai.app.presentation.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.presentation.components.BudgetProgressBar
import com.ledgerai.app.presentation.components.EmptyStateCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class GoalsViewModel @Inject constructor(private val goalRepo: GoalRepository) : ViewModel() {

    val goals: StateFlow<List<Goal>> = goalRepo.getActiveGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addGoal(name: String, target: Double, emoji: String, targetDate: LocalDate?) {
        viewModelScope.launch {
            goalRepo.insert(Goal(name = name, targetAmount = target, emoji = emoji, targetDate = targetDate))
        }
    }

    fun addSavings(goal: Goal, amount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(savedAmount = goal.savedAmount + amount,
                isCompleted = goal.savedAmount + amount >= goal.targetAmount)
            goalRepo.update(updated)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { goalRepo.delete(goal) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var depositGoal by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Savings Goals") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add goal")
            }
        }
    ) { padding ->
        if (goals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(emoji = "🎯", title = "No goals yet",
                    subtitle = "Set a savings goal and track your progress!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onAddSavings = { depositGoal = goal },
                        onDelete = { viewModel.deleteGoal(goal) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, target, emoji, date ->
                viewModel.addGoal(name, target, emoji, date)
                showAddDialog = false
            }
        )
    }

    depositGoal?.let { goal ->
        DepositDialog(
            goal = goal,
            onDismiss = { depositGoal = null },
            onConfirm = { amount ->
                viewModel.addSavings(goal, amount)
                depositGoal = null
            }
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, onAddSavings: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.emoji, fontSize = 24.sp)
                    Column {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        goal.targetDate?.let {
                            Text("Target: $it", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\$${"%.2f".format(goal.savedAmount)} saved", style = MaterialTheme.typography.bodyMedium)
                Text("of \$${"%.2f".format(goal.targetAmount)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            BudgetProgressBar(usagePercent = goal.progressPercent)
            Text("${goal.progressPercent}% complete — \$${"%.2f".format(goal.remaining)} to go",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedButton(onClick = onAddSavings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Savings")
            }
        }
    }
}

@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String, LocalDate?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var dateText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") },
                    modifier = Modifier.width(80.dp), singleLine = true)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = targetText, onValueChange = { targetText = it },
                    label = { Text("Target amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = dateText, onValueChange = { dateText = it },
                    label = { Text("Target date (YYYY-MM-DD, optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = targetText.toDoubleOrNull() ?: return@TextButton
                    val date = dateText.takeIf { it.isNotBlank() }?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } }
                    onConfirm(name, target, emoji, date)
                },
                enabled = name.isNotBlank() && targetText.toDoubleOrNull() != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DepositDialog(goal: Goal, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to ${goal.name}") },
        text = {
            OutlinedTextField(value = amountText, onValueChange = { amountText = it },
                label = { Text("Amount ($)") }, prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        },
        confirmButton = {
            TextButton(
                onClick = { amountText.toDoubleOrNull()?.let { onConfirm(it) } },
                enabled = amountText.toDoubleOrNull() != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
