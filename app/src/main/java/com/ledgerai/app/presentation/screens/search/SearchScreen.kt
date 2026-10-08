package com.ledgerai.app.presentation.screens.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.TaskItem
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchHit(val kind: String, val title: String, val sub: String)

data class SearchCorpus(
    val transactions: List<Transaction> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val bills: List<Bill> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    transactions: TransactionRepository,
    tasks: TaskRepository,
    notes: NoteRepository,
    bills: BillRepository
) : ViewModel() {
    val corpus: StateFlow<SearchCorpus> = combine(
        transactions.getAllTransactions(),
        tasks.observeTasks(),
        notes.observeNotes(),
        bills.getAllBills()
    ) { tx, task, note, bill ->
        SearchCorpus(tx, task, note, bill)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchCorpus())
}

@Composable
fun SearchScreen(onBack: () -> Unit = {}, viewModel: SearchViewModel = hiltViewModel()) {
    val corpus by viewModel.corpus.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val hits = remember(corpus, q) {
        if (q.isEmpty()) emptyList() else buildList {
            corpus.transactions.filter { tx ->
                listOf(tx.merchant, tx.note, tx.location, tx.category.displayName)
                    .any { it.contains(q, true) }
            }.forEach { tx ->
                add(SearchHit("Spend", tx.merchant.ifBlank { tx.category.displayName }, money(tx.amount)))
            }
            corpus.tasks.filter { task ->
                listOf(task.title, task.notes, task.location, task.links).any { it.contains(q, true) }
            }.forEach { task ->
                add(SearchHit("Task", task.title, task.location))
            }
            corpus.notes.filter { note ->
                listOf(note.title, note.body, note.location).any { it.contains(q, true) }
            }.forEach { note ->
                add(SearchHit("Note", note.title, note.location))
            }
            corpus.bills.filter { bill ->
                listOf(bill.name, bill.note, bill.location).any { it.contains(q, true) }
            }.forEach { bill ->
                add(SearchHit("Bill", bill.name, money(bill.amount)))
            }
        }
    }

    LScreen(title = "Search", onBack = onBack) {
        item {
            LField(query, { query = it }, "Search", modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty() && hits.isEmpty()) {
            item { LEmpty(Icons.Filled.Search, "No matches") }
        }
        hits.groupBy { it.kind }.forEach { (kind, rows) ->
            item { LSection(kind) }
            items(rows) { hit ->
                LRow(title = hit.title, sub = hit.sub.ifBlank { null })
            }
        }
    }
}
