package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SpendFilter(val label: String) { ALL("All"), INCOME("In"), EXPENSE("Out") }

@Composable
fun TransactionsScreen(
    onNavigateToVoice: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(SpendFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val type = when (filter) {
        SpendFilter.ALL -> null
        SpendFilter.INCOME -> TransactionType.INCOME
        SpendFilter.EXPENSE -> TransactionType.EXPENSE
    }
    val monthRows = remember(state.transactions, month) {
        SpendQuery.inMonth(state.transactions, month)
    }
    val net = remember(monthRows) { SpendQuery.monthNet(monthRows) }
    val monthCategories = remember(monthRows) { SpendQuery.categoriesIn(monthRows) }
    LaunchedEffect(month, monthCategories) {
        if (category != null && category !in monthCategories) category = null
    }
    val visible = remember(state.transactions, month, type, category, query) {
        SpendQuery.filter(state.transactions, month, type, category, query)
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
    }
    val days = remember(visible) {
        visible.groupBy { it.date }.toSortedMap(Comparator.reverseOrder())
    }
    val monthLabel = remember(month) {
        month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
    }

    Box(Modifier.fillMaxSize()) {
        LScreen(
            title = "Spend",
            onBack = onBack,
            action = { LIconButton(Icons.Filled.Mic, "Voice", onNavigateToVoice) },
            fab = { LFab(Icons.Filled.Add, onClick = { viewModel.showAddSheet() }) }
        ) {
            item(key = "hero") {
                LHero(
                    label = monthLabel,
                    value = money(net),
                    valueColor = if (net < 0) L.Danger else L.OnBox
                )
            }
            item(key = "month") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LChip("Prev", false, onClick = { month = month.minusMonths(1) })
                    LChip("Next", false, onClick = { month = month.plusMonths(1) })
                }
            }
            item(key = "search") {
                LField(query, { query = it }, "Search")
            }
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpendFilter.entries.forEach { f ->
                        LChip(f.label, filter == f, onClick = { filter = f; category = null })
                    }
                }
            }
            item(key = "categories") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LChip("All", category == null, onClick = { category = null })
                    monthCategories.forEach { cat ->
                        LChip(cat.displayName, category == cat, onClick = { category = cat })
                    }
                }
            }

            if (!state.isLoading && days.isEmpty()) {
                item(key = "empty") { LEmpty(Icons.AutoMirrored.Filled.ReceiptLong, "No spending") }
            }

            days.forEach { (date, list) ->
                item(key = "day-$date") { LSection(dayLabel(date)) }
                items(list, key = { it.id }) { tx ->
                    val income = tx.type == TransactionType.INCOME
                    LRow(
                        title = tx.merchant.ifBlank { tx.category.displayName },
                        sub = listOf(tx.location, tx.note).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                        trailing = (if (income) "+" else "-") + money(tx.amount),
                        trailingColor = if (income) L.Gold else L.OnBox,
                        icon = spendIcon(tx.category),
                        onClick = { viewModel.showEditSheet(tx) },
                        end = if (tx.isRecurring) {
                            { LChip("Repeat", true, onClick = { viewModel.showEditSheet(tx) }) }
                        } else null
                    )
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        )
    }

    if (state.showAddSheet) {
        AddTransactionSheet(
            prefilled = state.parsedTransaction,
            copyDraft = state.copyDraft,
            onDismiss = { viewModel.hideAddSheet() },
            onConfirm = { amount, type, category, merchant, note, date, location, isRecurring ->
                viewModel.addTransaction(amount, type, category, merchant, note, date, location, isRecurring)
            }
        )
    }

    state.editingTransaction?.let { editing ->
        key(editing.id) {
            AddTransactionSheet(
                existing = editing,
                onDismiss = { viewModel.hideEditSheet() },
                onConfirm = { amount, type, category, merchant, note, date, location, isRecurring ->
                    viewModel.updateTransaction(editing, amount, type, category, merchant, note, date, location, isRecurring)
                },
                onDelete = {
                    viewModel.deleteTransaction(editing)
                    viewModel.hideEditSheet()
                },
                onCopy = { amount, type, category, merchant, note, location, isRecurring ->
                    viewModel.copyToNew(amount, type, category, merchant, note, location, isRecurring)
                }
            )
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            DateTimeFormatter.ofPattern(
                if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy",
                Locale.getDefault()
            )
        )
    }
}
