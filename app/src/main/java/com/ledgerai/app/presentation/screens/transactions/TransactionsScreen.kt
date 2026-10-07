package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.theme.ExpenseRed
import com.ledgerai.app.presentation.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onNavigateToVoice: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNlpDialog by remember { mutableStateOf(false) }
    var nlpInput by remember { mutableStateOf("") }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transactions") })
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Manual add
                SmallFloatingActionButton(
                    onClick = { viewModel.showAddSheet() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add manually")
                }
                // Voice / AI add — primary action
                ExtendedFloatingActionButton(
                    onClick = onNavigateToVoice,
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    text = { Text("Voice Add") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.transactions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    emoji = "💳",
                    title = "No transactions yet",
                    subtitle = "Tap + to add one, or the mic to use natural language"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.transactions, key = { it.id }) { tx ->
                    TransactionCard(
                        transaction = tx,
                        onDelete = { viewModel.deleteTransaction(tx) }
                    )
                }
            }
        }
    }

    // Add transaction bottom sheet
    if (state.showAddSheet) {
        AddTransactionSheet(
            prefilled = state.parsedTransaction,
            onDismiss = { viewModel.hideAddSheet() },
            onConfirm = { amount, type, category, merchant, note, date ->
                viewModel.addTransaction(amount, type, category, merchant, note, date)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionCard(
    transaction: com.ledgerai.app.domain.model.Transaction,
    onDelete: () -> Unit
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)) {
                CategoryDot(transaction.category, size = 12.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.merchant.ifEmpty { transaction.category.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryChip(transaction.category)
                        Text(transaction.date.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (transaction.note.isNotEmpty()) {
                        Text(transaction.note, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(amount = transaction.amount, isExpense = isExpense, prefix = "$")
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}
