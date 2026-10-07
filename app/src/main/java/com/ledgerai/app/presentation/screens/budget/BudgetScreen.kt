package com.ledgerai.app.presentation.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.presentation.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateToAi: () -> Unit = {},
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budget Planner") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add budget")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.budgets.isEmpty()) {
                item {
                    EmptyStateCard(
                        emoji = "📊",
                        title = "No budgets set",
                        subtitle = "Tap + to set your first monthly budget"
                    )
                }
            } else {
                items(state.budgets, key = { it.id }) { budget ->
                    BudgetCard(
                        budget = budget,
                        aiAdvice = state.aiAdvice[budget.id],
                        onGetAdvice = { viewModel.getAiAdvice(budget) },
                        onDelete = { viewModel.deleteBudget(budget) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { category, limit, threshold ->
                viewModel.addBudget(category, limit, threshold)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    aiAdvice: String?,
    onGetAdvice: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryDot(budget.category)
                    Text(budget.category.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row {
                    IconButton(onClick = onGetAdvice, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Advice",
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${"%.2f".format(budget.spent)} spent", style = MaterialTheme.typography.bodyMedium)
                Text("of ${"%.2f".format(budget.monthlyLimit)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            BudgetProgressBar(usagePercent = budget.usagePercent)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val statusText = when {
                    budget.isOverBudget -> "Over budget!"
                    budget.isNearLimit -> "${budget.usagePercent}% used — near limit"
                    else -> "${budget.usagePercent}% used"
                }
                Text(statusText, style = MaterialTheme.typography.labelSmall,
                    color = if (budget.isOverBudget) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${"%.2f".format(budget.remaining)} left", style = MaterialTheme.typography.labelSmall)
            }

            aiAdvice?.let {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Budget?") },
            text = { Text("Remove budget for ${budget.category.displayName}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (TransactionCategory, Double, Int) -> Unit
) {
    var limitText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.FOOD) }
    var threshold by remember { mutableStateOf(80f) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Monthly Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        TransactionCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.displayName) },
                                onClick = { selectedCategory = cat; categoryExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Limit ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Alert at ${threshold.toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 50f..95f)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val limit = limitText.toDoubleOrNull() ?: return@TextButton
                    onConfirm(selectedCategory, limit, threshold.toInt())
                },
                enabled = limitText.toDoubleOrNull() != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
