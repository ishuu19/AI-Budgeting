package com.ledgerai.app.presentation.screens.debts

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.theme.ExpenseRed
import com.ledgerai.app.presentation.theme.IncomeGreen
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(viewModel: DebtsViewModel = hiltViewModel()) {
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
        topBar = { TopAppBar(title = { Text("Debt Tracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add debt")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoChip("Owed to Me", "+\$${"%.2f".format(state.totalOwedToMe)}", IncomeGreen, Modifier.weight(1f))
                    InfoChip("I Owe", "-\$${"%.2f".format(state.totalIOwe)}", ExpenseRed, Modifier.weight(1f))
                }
            }

            if (state.activeDebts.isEmpty()) {
                item {
                    EmptyStateCard(emoji = "🤝", title = "No active debts",
                        subtitle = "Track money you've lent or borrowed from friends")
                }
            } else {
                items(state.activeDebts, key = { it.id }) { debt ->
                    DebtCard(
                        debt = debt,
                        onMarkPaid = { viewModel.markAsPaid(debt) },
                        onDelete = { viewModel.deleteDebt(debt) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddDebtDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, direction, dueDate, phone, email, note ->
                viewModel.addDebt(name, amount, direction, dueDate, phone, email, note)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun DebtCard(debt: Debt, onMarkPaid: () -> Unit, onDelete: () -> Unit) {
    val isOwedToMe = debt.direction == DebtDirection.THEY_OWE
    val color = if (isOwedToMe) IncomeGreen else ExpenseRed

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(debt.friendName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isOwedToMe) "${debt.friendName} owes you" else "You owe ${debt.friendName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
                Text("\$${"%.2f".format(debt.amount)}", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = color)
            }

            debt.dueDate?.let {
                val daysLeft = LocalDate.now().until(it).days
                val dueDateText = when {
                    daysLeft < 0 -> "Overdue by ${-daysLeft} days"
                    daysLeft == 0 -> "Due today!"
                    else -> "Due in $daysLeft days (${it})"
                }
                Text(dueDateText, style = MaterialTheme.typography.bodySmall,
                    color = if (daysLeft <= 1) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (debt.note.isNotEmpty()) {
                Text(debt.note, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onMarkPaid,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mark Paid", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDebtDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, DebtDirection, LocalDate?, String, String, String) -> Unit
) {
    var friendName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(DebtDirection.THEY_OWE) }
    var dueDateText by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Debt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = friendName, onValueChange = { friendName = it },
                    label = { Text("Friend's name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebtDirection.entries.forEach { dir ->
                        FilterChip(
                            selected = direction == dir,
                            onClick = { direction = dir },
                            label = { Text(if (dir == DebtDirection.THEY_OWE) "They owe me" else "I owe them") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(value = dueDateText, onValueChange = { dueDateText = it },
                    label = { Text("Due date (YYYY-MM-DD, optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@TextButton
                    val dueDate = dueDateText.takeIf { it.isNotBlank() }?.let {
                        try { LocalDate.parse(it) } catch (_: Exception) { null }
                    }
                    onConfirm(friendName, amount, direction, dueDate, phone, email, note)
                },
                enabled = friendName.isNotBlank() && amountText.toDoubleOrNull() != null
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
