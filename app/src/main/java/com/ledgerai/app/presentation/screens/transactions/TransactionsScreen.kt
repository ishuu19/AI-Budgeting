package com.ledgerai.app.presentation.screens.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ledgerai.app.presentation.components.Delta
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.theme.IncomeGreen
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.screens.money.SnackEffect
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SpendFilter(val label: String) { ALL("All"), INCOME("Income"), EXPENSE("Spending") }

@Composable
fun TransactionsScreen(
    onNavigateToVoice: () -> Unit,
    onBack: (() -> Unit)? = null,
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    addSpend: Boolean = false,
    onAddSpendConsumed: () -> Unit = {},
    query: String = "",
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var filterName by rememberSaveable { mutableStateOf(SpendFilter.ALL.name) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var fromText by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var toText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }

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
    fun amountOf(tx: Transaction): Double = when (filter) {
        SpendFilter.INCOME -> if (tx.type == TransactionType.INCOME) tx.amount else 0.0
        else -> if (tx.type == TransactionType.EXPENSE) tx.amount else 0.0
    }
    val total = remember(visible, filter) { visible.sumOf(::amountOf) }
    val span = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1
    val prevEnd = rangeStart.minusDays(1)
    val prevStart = prevEnd.minusDays(span - 1)
    val prevTotal = remember(state.transactions, prevStart, prevEnd, type, category, query, filter) {
        SpendQuery.filter(state.transactions, prevStart, prevEnd, type, category, query).sumOf(::amountOf)
    }
    val daily = remember(visible, rangeStart, span, filter) {
        val daysInRange = span.coerceAtMost(42)
        (0 until daysInRange).map { offset ->
            val day = rangeStart.plusDays(offset.toLong())
            visible.filter { it.date == day }.sumOf(::amountOf)
        }
    }
    val days = remember(visible) {
        visible.groupBy { it.date }.toSortedMap(Comparator.reverseOrder())
    }
    val delta = if (prevTotal > 0.0) ((total - prevTotal) / prevTotal * 100).toInt() else null
    LScreen(
        title = "Spend",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { viewModel.showAddSheet() }, label = "New transaction") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        item(key = "total") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LHero(
                    label = if (filter == SpendFilter.INCOME) "Income" else "Spent",
                    value = money(total),
                    sub = if (delta == null) "${shortDate(rangeStart)} – ${shortDate(rangeEnd)}" else null,
                    delta = delta?.takeIf { it != 0 }?.let {
                        Delta(
                            "${kotlin.math.abs(it)}% vs prior",
                            up = it > 0,
                            good = if (filter == SpendFilter.INCOME) it > 0 else it < 0
                        )
                    }
                )
                DailyBars(daily)
            }
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LSegments(SpendFilter.entries, filter, { it.label }, onSelect = { chosen ->
                    filterName = chosen.name
                    categoryName = null
                })
                ChipsRow {
                    LChip(
                        "${shortDate(rangeStart)} – ${shortDate(rangeEnd)}",
                        false,
                        onClick = { showDates = true }
                    )
                    LChip(
                        category?.displayName ?: "Category",
                        category != null,
                        onClick = { showCategories = true }
                    )
                }
            }
        }

        if (state.isLoading) {
            item(key = "loading") { LLoading() }
        } else if (days.isEmpty()) {
            item(key = "empty") {
                LEmpty(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    if (state.transactions.isEmpty()) "No spending" else "Nothing in this range"
                )
            }
        }

        days.forEach { (date, list) ->
            item(key = "day-$date") {
                val daySpend = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dayLabel(date),
                            style = MaterialTheme.typography.titleSmall,
                            color = L.Ink,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        Text(
                            money(daySpend),
                            style = MaterialTheme.typography.labelLarge,
                            color = L.InkMuted
                        )
                    }
                    LGroup {
                        list.forEachIndexed { index, tx ->
                            androidx.compose.runtime.key(tx.id) {
                                if (index > 0) LGroupDivider()
                                val income = tx.type == TransactionType.INCOME
                                LGroupRow(
                                    title = tx.merchant.ifBlank { tx.category.displayName },
                                    sub = listOf(tx.category.displayName, tx.location, tx.note)
                                        .filter { it.isNotBlank() && it != tx.merchant.ifBlank { tx.category.displayName } }
                                        .joinToString(" · ").ifBlank { null },
                                    trailing = (if (income) "+" else "") + money(tx.amount),
                                    trailingColor = if (income) IncomeGreen else L.OnBox,
                                    icon = spendIcon(tx.category),
                                    onClick = { viewModel.showEditSheet(tx) },
                                    end = if (tx.isRecurring) {
                                        {
                                            Icon(
                                                Icons.Filled.Repeat,
                                                contentDescription = "Repeats",
                                                tint = L.OnBoxMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDates) {
        LSheet(
            title = "Dates",
            onDismiss = { showDates = false },
            primary = "Done",
            onPrimary = { showDates = false }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
                DatePickChip(rangeStart, selected = true, onDate = { fromText = it.toString() }, label = shortDate(rangeStart))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("To", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
                DatePickChip(rangeEnd, selected = true, onDate = { toText = it.toString() }, label = shortDate(rangeEnd))
            }
        }
    }
    if (showCategories) {
        LSheet(
            title = "Category",
            onDismiss = { showCategories = false },
            primary = "Done",
            onPrimary = { showCategories = false }
        ) {
            ChipsRow {
                LChip("All", category == null, onClick = { categoryName = null })
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

@Composable
private fun DailyBars(amounts: List<Double>) {
    if (amounts.isEmpty()) return
    val max = amounts.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Row(
        Modifier.fillMaxWidth().height(32.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        amounts.forEach { amount ->
            val frac = (amount / max).toFloat().coerceIn(0.08f, 1f)
            Box(Modifier.width(8.dp).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (amount <= 0.0) 0.08f else frac)
                        .clip(RoundedCornerShape(topStart = L.Space.x4, topEnd = L.Space.x4))
                        .background(if (amount <= 0.0) L.BoxDeep else L.Gold)
                )
            }
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
