package com.ledgerai.app.presentation.screens.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.label
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.OpenKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** A result row. [kind] + [id] tell the shell which item sheet to open. */
data class SearchHit(val group: String, val title: String, val sub: String, val kind: OpenKind, val id: Long)

data class SearchCorpus(
    val transactions: List<Transaction> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val jobs: List<JobApplication> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    transactions: TransactionRepository,
    events: CalendarRepository,
    notes: NoteRepository,
    bills: BillRepository,
    debts: DebtRepository,
    goals: GoalRepository,
    budgets: BudgetRepository,
    jobs: JobRepository
) : ViewModel() {
    private val today = LocalDate.now()

    private val money = combine(
        transactions.getAllTransactions(),
        bills.getAllBills(),
        debts.getAllDebts(),
        goals.getAllGoals(),
        budgets.getBudgetsForMonth(today.monthValue, today.year)
    ) { tx, bill, debt, goal, budget -> listOf(tx, bill, debt, goal, budget) }

    private val life = combine(events.observeAll(), notes.observeNotes(), jobs.observeAll()) { e, n, j -> listOf(e, n, j) }

    @Suppress("UNCHECKED_CAST")
    val corpus: StateFlow<SearchCorpus> = combine(money, life) { m, l ->
        SearchCorpus(
            transactions = m[0] as List<Transaction>,
            bills = m[1] as List<Bill>,
            debts = m[2] as List<Debt>,
            goals = m[3] as List<Goal>,
            budgets = m[4] as List<Budget>,
            events = l[0] as List<CalendarEvent>,
            notes = l[1] as List<NoteItem>,
            jobs = l[2] as List<JobApplication>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchCorpus())
}

private fun List<String>.hits(q: String) = any { it.contains(q, true) }

internal fun searchHits(corpus: SearchCorpus, q: String): List<SearchHit> = if (q.isEmpty()) emptyList() else buildList {
    corpus.events.filter { listOf(it.title, it.notes, it.location, it.links).hits(q) }.forEach {
        add(SearchHit(it.kind.label(), it.title, it.location, OpenKind.Event, it.id))
    }
    corpus.transactions.filter { listOf(it.merchant, it.note, it.location, it.category.displayName).hits(q) }.forEach {
        add(SearchHit("Spend", it.merchant.ifBlank { it.category.displayName }, money(it.amount), OpenKind.Transaction, it.id))
    }
    corpus.notes.filter { listOf(it.title, it.body, it.location).hits(q) }.forEach {
        add(SearchHit("Note", it.title, it.location, OpenKind.Note, it.id))
    }
    corpus.bills.filter { listOf(it.name, it.note, it.location).hits(q) }.forEach {
        add(SearchHit("Bill", it.name, money(it.amount), OpenKind.Bill, it.id))
    }
    corpus.debts.filter { listOf(it.friendName, it.note, it.location).hits(q) }.forEach {
        add(SearchHit("Debt", it.friendName, money(it.amount), OpenKind.Debt, it.id))
    }
    corpus.goals.filter { listOf(it.name, it.note, it.location).hits(q) }.forEach {
        add(SearchHit("Goal", it.name, money(it.targetAmount), OpenKind.Goal, it.id))
    }
    corpus.budgets.filter { it.category.displayName.contains(q, true) }.forEach {
        add(SearchHit("Budget", it.category.displayName, money(it.monthlyLimit), OpenKind.Budget, it.id))
    }
    corpus.jobs.filter { listOf(it.company, it.title, it.notes, it.contact, it.source).hits(q) }.forEach {
        add(SearchHit("Job", it.company, it.title, OpenKind.Job, it.id))
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    links: AppLinks = AppLinks(),
    viewModel: SearchViewModel = hiltViewModel()
) {
    val corpus by viewModel.corpus.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(250)
        q = query.trim()
    }
    val hits = remember(corpus, q) { searchHits(corpus, q) }

    LScreen(title = "Search", onBack = onBack) {
        item {
            LField(query, { query = it }, "Search", modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty() && hits.isEmpty()) {
            item { LEmpty(Icons.Filled.Search, "No matches") }
        }
        hits.groupBy { it.group }.forEach { (group, rows) ->
            item(key = "h-$group") { LSection(group) }
            items(rows, key = { "${it.kind}-${it.id}" }) { hit ->
                LRow(title = hit.title, sub = hit.sub.ifBlank { null }, onClick = { links.open(hit.kind, hit.id) })
            }
        }
    }
}
