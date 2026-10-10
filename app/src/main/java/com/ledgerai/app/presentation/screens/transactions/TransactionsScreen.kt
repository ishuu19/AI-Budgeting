package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LIconButton
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.screens.money.SnackEffect
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SpendFilter(val label: String) { ALL("All"), INCOME("In"), EXPENSE("Out") }

@Composable
fun TransactionsScreen(
    onNavigateToVoice: () -> Unit,
    onBack: () -> Unit = {},
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    addSpend: Boolean = false,
    onAddSpendConsumed: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var filterName by rememberSaveable { mutableStateOf(SpendFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var fromText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var toText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    val filter = SpendFilter.valueOf(filterName)
    val category = categoryName?.let { name -> TransactionCategory.entries.firstOrNull { it.name == name } }
    val fromDate = remember(fromText) { runCatching { LocalDate.parse(fromText) }.getOrDefault(LocalDate.now().withDayOfMonth(1)) }
    val toDate = remember(toText) { runCatching { LocalDate.parse(toText) }.getOrDefault(LocalDate.now()) }
    val rangeStart = if (fromDate.isAfter(toDate)) toDate else fromDate
    val rangeEnd = if (fromDate.isAfter(toDate)) fromDate else toDate

    SnackEffect(state.snackbarMessage, snackbarHostState) { viewModel.clearSnackbar() }

    LaunchedEffect(addSpend) {
        if (addSpend) {
            viewModel.showAddSheet()
            onAddSpendConsumed()
        }
    }

    LaunchedEffect(open, state.transactions, state.isLoading) {
        val request = open
        if (request != null && request.kind == OpenKind.Transaction) {
            val tx = state.transactions.firstOrNull { it.id == request.id }
            if (tx != null) {
                val ym = YearMonth.from(tx.date)
                fromText = ym.atDay(1).toString()
                toText = ym.atEndOfMonth().toString()
                viewModel.showEditSheet(tx)
                onOpened()
            } else if (!state.isLoading) {
                onOpened()
            }
        }
    }

    val type = when (filter) {
        SpendFilter.ALL -> null
        SpendFilter.INCOME -> TransactionType.INCOME
        SpendFilter.EXPENSE -> TransactionType.EXPENSE
    }
    val rangeRows = remember(state.transactions, rangeStart, rangeEnd) {
        SpendQuery.inRange(state.transactions, rangeStart, rangeEnd)
    }
    val monthCategories = remember(rangeRows) { SpendQuery.categoriesIn(rangeRows) }
    LaunchedEffect(rangeStart, rangeEnd, monthCategories) {
        if (category != null && category !in monthCategories) categoryName = null
    }
    val visible = remember(state.transactions, rangeStart, rangeEnd, type, category, query) {
        SpendQuery.filter(state.transactions, rangeStart, rangeEnd, type, category, query)
            .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
    }
    val total = remember(visible, filter) {
        visible.sumOf { tx ->
            when (filter) {
                SpendFilter.INCOME -> if (tx.type == TransactionType.INCOME) tx.amount else 0.0
                else -> if (tx.type == TransactionType.EXPENSE) tx.amount else 0.0
            }
        }
    }
    val days = remember(visible) {
        visible.groupBy { it.date }.toSortedMap(Comparator.reverseOrder())
    }
    LScreen(
        title = "Spend",
        onBack = onBack,
        action = { LIconButton(Icons.Filled.Mic, "Add by voice", onNavigateToVoice) },
        fab = { LFab(Icons.Filled.Add, onClick = { viewModel.showAddSheet() }, label = "Add transaction") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        item(key = "total") {
            LHero(label = "Total", value = money(total))
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickChip(rangeStart, selected = false, onDate = { fromText = it.toString() }, label = "From " + shortDate(rangeStart))
                    DatePickChip(rangeEnd, selected = false, onDate = { toText = it.toString() }, label = "To " + shortDate(rangeEnd))
                }
                LField(query, { query = it }, "Search")
                ChipsRow {
                    SpendFilter.entries.forEach { f ->
                        LChip(f.label, filter == f, onClick = { filterName = f.name; categoryName = null })
                    }
                    monthCategories.forEach { cat ->
                        LChip(
                            cat.displayName,
                            category == cat,
                            onClick = { categoryName = if (category == cat) null else cat.name }
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            item(key = "loading") { LLoading() }
        } else if (days.isEmpty()) {
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
                        { Icon(Icons.Filled.Repeat, contentDescription = "Repeats", tint = L.OnBoxMuted, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }

    if (state.showAddSheet) {
        AddTransactionSheet(
            prefilled = state.parsedTransaction,
            copyDraft = state.copyDraft,
            suggest = viewModel::suggestFor,
            onDismiss = { viewModel.hideAddSheet() },
            onConfirm = { amount, type, category, merchant, note, date, location, isRecurring ->
                viewModel.addTransaction(amount, type, category, merchant, note, date, location, isRecurring)
            }
        )
    }

    state.editingTransaction?.let { editing ->
        androidx.compose.runtime.key(editing.id) {
            AddTransactionSheet(
                existing = editing,
                suggest = viewModel::suggestFor,
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
