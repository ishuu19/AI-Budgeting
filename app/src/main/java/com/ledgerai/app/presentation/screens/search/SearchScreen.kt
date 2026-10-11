package com.ledgerai.app.presentation.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.inventory.StockItem
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.people.Person
import com.ledgerai.app.domain.wardrobe.WardrobeItem
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSearchBox
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.label
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.components.rememberVoiceInput
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.LifeSeg
import com.ledgerai.app.presentation.navigation.OpenKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** A result row. [open] + [id] open a money or plan item. Life rows use [life] or [personId]. */
data class SearchHit(
    val group: String,
    val title: String,
    val sub: String = "",
    val trailing: String? = null,
    val icon: ImageVector? = null,
    val browse: String,
    val open: OpenKind? = null,
    val id: Long = 0,
    val life: LifeSeg? = null,
    val personId: String? = null,
)

data class SearchCorpus(
    val transactions: List<Transaction> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val jobs: List<JobApplication> = emptyList(),
    val pantry: List<StockItem> = emptyList(),
    val wardrobe: List<WardrobeItem> = emptyList(),
    val people: List<Person> = emptyList(),
)

private val SearchKinds = listOf(
    "Spending", "Income", "Bills", "Debts", "Goals", "Budgets",
    "Events", "Notes", "Jobs", "Pantry", "Wardrobe", "People",
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
    jobs: JobRepository,
    inventory: InventoryRepository,
    wardrobe: WardrobeRepository,
    peopleRepo: PeopleRepository,
    session: UserSession,
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

    private val ownerId = session.userInfo.map { it.userId }.distinctUntilChanged()
    private val garments = ownerId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList()) else wardrobe.observeGarments(id)
    }
    private val peopleRows = ownerId.flatMapLatest { id ->
        flow { emit(if (id.isBlank()) emptyList() else peopleRepo.listPeople(id)) }
    }
    private val home = combine(inventory.observeItems(), garments, peopleRows) { pantry, clothes, folks ->
        Triple(pantry, clothes, folks)
    }

    @Suppress("UNCHECKED_CAST")
    val corpus: StateFlow<SearchCorpus> = combine(money, life, home) { m, l, h ->
        SearchCorpus(
            transactions = m[0] as List<Transaction>,
            bills = m[1] as List<Bill>,
            debts = m[2] as List<Debt>,
            goals = m[3] as List<Goal>,
            budgets = m[4] as List<Budget>,
            events = l[0] as List<CalendarEvent>,
            notes = l[1] as List<NoteItem>,
            jobs = l[2] as List<JobApplication>,
            pantry = h.first,
            wardrobe = h.second,
            people = h.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchCorpus())
}

private fun List<String>.hits(q: String) = any { it.contains(q, true) }

private fun pluralKind(label: String) = when (label) {
    "Class" -> "Classes"
    else -> if (label.endsWith("s")) label else "${label}s"
}

internal fun searchHits(corpus: SearchCorpus, q: String, kind: String? = null): List<SearchHit> {
    if (q.isEmpty() && kind == null) return emptyList()
    fun want(browse: String) = kind == null || kind == browse
    fun ok(fields: List<String>) = q.isEmpty() || fields.hits(q)
    fun <T> List<T>.cap() = if (q.isEmpty()) take(40) else this
    return buildList {
        if (want("Events")) {
            corpus.events.filter { ok(listOf(it.title, it.notes, it.location, it.links)) }.cap().forEach {
                add(SearchHit(pluralKind(it.kind.label()), it.title, it.location, icon = Icons.Filled.Today, browse = "Events", open = OpenKind.Event, id = it.id))
            }
        }
        if (want("Spending")) {
            corpus.transactions.filter { it.type != TransactionType.INCOME && ok(listOf(it.merchant, it.note, it.location, it.category.displayName)) }.cap().forEach {
                add(SearchHit("Spending", it.merchant.ifBlank { it.category.displayName }, it.category.displayName, money(it.amount), Icons.Filled.Payments, "Spending", OpenKind.Transaction, it.id))
            }
        }
        if (want("Income")) {
            corpus.transactions.filter { it.type == TransactionType.INCOME && ok(listOf(it.merchant, it.note, it.location, it.category.displayName)) }.cap().forEach {
                add(SearchHit("Income", it.merchant.ifBlank { it.category.displayName }, it.category.displayName, money(it.amount), Icons.Filled.CurrencyExchange, "Income", OpenKind.Transaction, it.id))
            }
        }
        if (want("Notes")) {
            corpus.notes.filter { ok(listOf(it.title, it.body, it.location)) }.cap().forEach {
                add(SearchHit("Notes", it.title, it.location, icon = Icons.Filled.Description, browse = "Notes", open = OpenKind.Note, id = it.id))
            }
        }
        if (want("Bills")) {
            corpus.bills.filter { ok(listOf(it.name, it.note, it.location)) }.cap().forEach {
                add(SearchHit("Bills", it.name, it.frequency.displayName, money(it.amount), Icons.Filled.EventRepeat, "Bills", OpenKind.Bill, it.id))
            }
        }
        if (want("Debts")) {
            corpus.debts.filter { ok(listOf(it.friendName, it.note, it.location)) }.cap().forEach {
                val who = if (it.direction == DebtDirection.I_OWE) "You owe" else "Owes you"
                add(SearchHit("Debts", it.friendName, who, money(it.amount), Icons.Filled.Groups, "Debts", OpenKind.Debt, it.id))
            }
        }
        if (want("Goals")) {
            corpus.goals.filter { ok(listOf(it.name, it.note, it.location)) }.cap().forEach {
                add(SearchHit("Goals", it.name, it.note, money(it.targetAmount), Icons.Filled.Flag, "Goals", OpenKind.Goal, it.id))
            }
        }
        if (want("Budgets")) {
            corpus.budgets.filter { ok(listOf(it.category.displayName)) }.cap().forEach {
                add(SearchHit("Budgets", it.category.displayName, trailing = money(it.monthlyLimit), icon = Icons.Filled.PieChart, browse = "Budgets", open = OpenKind.Budget, id = it.id))
            }
        }
        if (want("Jobs")) {
            corpus.jobs.filter { ok(listOf(it.company, it.title, it.notes, it.contact, it.source)) }.cap().forEach {
                add(SearchHit("Jobs", it.company, it.title, icon = Icons.Filled.Work, browse = "Jobs", open = OpenKind.Job, id = it.id))
            }
        }
        if (want("Pantry")) {
            corpus.pantry.filter { ok(listOf(it.name, it.category, it.location)) }.cap().forEach {
                add(SearchHit("Pantry", it.name, it.category, icon = Icons.Filled.Kitchen, browse = "Pantry", life = LifeSeg.Pantry, id = it.id))
            }
        }
        if (want("Wardrobe")) {
            corpus.wardrobe.filter { ok(listOf(it.name, it.type)) }.cap().forEach {
                add(SearchHit("Wardrobe", it.name, it.type, icon = Icons.Filled.Checkroom, browse = "Wardrobe", life = LifeSeg.Wardrobe, id = it.id))
            }
        }
        if (want("People")) {
            corpus.people.filter { ok(listOf(it.name, it.org.orEmpty(), it.role.orEmpty(), it.notes)) }.cap().forEach {
                val sub = listOfNotNull(it.org, it.role).filter { part -> part.isNotBlank() }.joinToString(" · ")
                add(SearchHit("People", it.name, sub, icon = Icons.Filled.Person, browse = "People", personId = it.id))
            }
        }
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
    var kind by rememberSaveable { mutableStateOf("") }
    var recentRaw by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val voice = rememberVoiceInput { query = it }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(250)
        q = query.trim()
    }
    LaunchedEffect(q) {
        if (q.isEmpty()) return@LaunchedEffect
        val prior = recentRaw.split('\n').filter { it.isNotBlank() && !it.equals(q, true) }
        recentRaw = (listOf(q) + prior).take(6).joinToString("\n")
    }
    val kindFilter = kind.ifBlank { null }
    val hits = remember(corpus, q, kindFilter) { searchHits(corpus, q, kindFilter) }
    val recent = remember(recentRaw) { recentRaw.split('\n').filter { it.isNotBlank() } }

    LScreen(title = "Search", onBack = onBack) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LSearchBox(query, { query = it }, "Search", Modifier.weight(1f).focusRequester(focus))
                LToolButton(
                    Icons.Filled.KeyboardVoice,
                    if (voice.listening) "Stop listening" else "Voice search",
                    voice.toggle,
                    active = voice.listening,
                )
            }
        }
        item {
            ChipsRow {
                SearchKinds.forEach { name ->
                    LChip(name, selected = kind == name, onClick = { kind = if (kind == name) "" else name })
                }
            }
        }
        if (q.isEmpty() && kindFilter == null && recent.isNotEmpty()) {
            item {
                Column {
                    LSection("Recent")
                    LGroup {
                        recent.forEachIndexed { index, term ->
                            if (index > 0) LGroupDivider()
                            LGroupRow(title = term, icon = Icons.Filled.Search, onClick = { query = term })
                        }
                    }
                }
            }
        }
        if ((q.isNotEmpty() || kindFilter != null) && hits.isEmpty()) {
            item { LEmpty(Icons.Filled.Search, "No matches") }
        }
        hits.groupBy { it.group }.forEach { (group, rows) ->
            item(key = "h-$group") {
                Column {
                    LSection(group)
                    LGroup {
                        rows.forEachIndexed { index, hit ->
                            if (index > 0) LGroupDivider()
                            LGroupRow(
                                title = hit.title,
                                sub = hit.sub.ifBlank { null },
                                trailing = hit.trailing,
                                icon = hit.icon,
                                onClick = {
                                    when {
                                        hit.personId != null -> links.person(hit.personId)
                                        hit.life != null -> links.life(hit.life)
                                        hit.open != null -> links.open(hit.open, hit.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
