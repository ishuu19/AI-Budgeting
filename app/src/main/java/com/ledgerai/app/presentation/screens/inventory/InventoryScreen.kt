package com.ledgerai.app.presentation.screens.inventory

import com.ledgerai.app.presentation.components.LSearchToolbar
import com.ledgerai.app.presentation.components.LToolButton
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.domain.inventory.ItemAvailability
import com.ledgerai.app.domain.inventory.ShoppingLine
import com.ledgerai.app.domain.inventory.ShoppingStatus
import com.ledgerai.app.domain.inventory.StockItem
import com.ledgerai.app.domain.inventory.StockQuantity
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.rememberVoiceInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class InventoryPage { Pantry, Shopping }

private enum class PantryFilter { All, Low, Expiring }

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val repository: InventoryRepository,
) : ViewModel() {

    val items: StateFlow<List<StockItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listId = MutableStateFlow(0L)

    val lines: StateFlow<List<ShoppingLine>> = listId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else repository.observeLines(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _availability = MutableStateFlow<ItemAvailability?>(null)
    val availability: StateFlow<ItemAvailability?> = _availability.asStateFlow()

    private val _addNotice = MutableStateFlow<String?>(null)
    val addNotice: StateFlow<String?> = _addNotice.asStateFlow()

    init {
        viewModelScope.launch {
            listId.value = repository.ensureList("Shopping")
        }
    }

    fun addItem(name: String, quantity: Double?, unit: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addItem(name = trimmed, quantity = quantity, unit = unit.trim())
        }
    }

    fun saveItem(id: Long, quantity: Double?, unit: String) {
        viewModelScope.launch { repository.saveItem(id, quantity, unit.trim()) }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.softDeleteItem(id) }
    }

    fun addLine(name: String, qty: Double?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = listId.value.takeIf { it != 0L }
                ?: repository.ensureList("Shopping").also { listId.value = it }
            repository.addShoppingLine(id, itemName = trimmed, qty = qty, addedBy = null)
        }
    }

    fun checkOff(id: Long) {
        viewModelScope.launch { repository.checkOff(id) }
    }

    fun deleteLine(id: Long) {
        viewModelScope.launch { repository.softDeleteLine(id) }
    }

    fun findByName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _availability.value = repository.findByName(trimmed)
        }
    }

    fun addLowOrOut(itemId: Long) {
        viewModelScope.launch {
            val id = listId.value.takeIf { it != 0L }
                ?: repository.ensureList("Shopping").also { listId.value = it }
            val lineId = repository.addLowOrOutToList(itemId, id)
            _addNotice.value = if (lineId != 0L) "Added to list" else "Not added"
        }
    }

    fun consumeNotice() {
        _addNotice.value = null
    }
}

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    onBack: (() -> Unit)? = null,
) {
    val items by viewModel.items.collectAsState()
    val lines by viewModel.lines.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val addNotice by viewModel.addNotice.collectAsState()
    var page by rememberSaveable { mutableStateOf(InventoryPage.Pantry) }
    var haveName by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filterName by rememberSaveable { mutableStateOf(PantryFilter.All.name) }
    var showBought by rememberSaveable { mutableStateOf(false) }
    var deleteItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteLineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var photoNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val notices = remember { SnackbarHostState() }
    val photos: com.ledgerai.app.presentation.components.PhotoSaveViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val photosIn = com.ledgerai.app.presentation.components.rememberPhotoActions(max = 6) { uris ->
        scope.launch {
            val note = "This may be food at home or a receipt. If it is a receipt, treat it as a receipt. Otherwise list each food item."
            val n = photos.fileWithAi(uris, note)
            photoNotice = if (n > 0) "Adding from photo…" else "Couldn't read that photo."
        }
    }
    val filter = PantryFilter.entries.firstOrNull { it.name == filterName } ?: PantryFilter.All

    LaunchedEffect(photoNotice) {
        val text = photoNotice ?: return@LaunchedEffect
        notices.showSnackbar(text)
        photoNotice = null
    }
    LaunchedEffect(addNotice) {
        val text = addNotice ?: return@LaunchedEffect
        notices.showSnackbar(text)
        viewModel.consumeNotice()
    }

    Column(Modifier.fillMaxSize()) {
        if (page == InventoryPage.Pantry) {
            LSearchToolbar(
                query = haveName,
                onQuery = { haveName = it },
                searching = searching,
                onSearching = { searching = it },
                hint = "Do I have…",
                submitLabel = "Check",
                onSubmit = { viewModel.findByName(haveName) },
            ) {
                LToolButton(Icons.Filled.PhotoCamera, "Camera", photosIn.shoot)
                LToolButton(Icons.Filled.PhotoLibrary, "Gallery", photosIn.pick)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = L.Gutter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LSegments(
                options = listOf(InventoryPage.Pantry, InventoryPage.Shopping),
                selected = page,
                label = { if (it == InventoryPage.Pantry) "Pantry" else "To buy" },
                onSelect = { page = it },
            )
            if (page == InventoryPage.Pantry) {
                val today = LocalDate.now()
                val lowCount = items.count { StockQuantity.isLowOrOut(it.quantity) }
                val weekCount = items.count { item ->
                    val days = item.expiresOn?.let { daysUntil(it, today) } ?: return@count false
                    days in 0..7
                }
                LSmallPair(
                    left = { modifier ->
                        LSmallBlock(
                            label = "Low",
                            value = lowCount.toString(),
                            modifier = modifier,
                            valueColor = if (lowCount > 0) L.Highlight else L.OnBox,
                            onClick = { filterName = if (filter == PantryFilter.Low) PantryFilter.All.name else PantryFilter.Low.name },
                        )
                    },
                    right = { modifier ->
                        LSmallBlock(
                            label = "Expiring this week",
                            value = weekCount.toString(),
                            modifier = modifier,
                            valueColor = if (weekCount > 0) L.Highlight else L.OnBox,
                            onClick = {
                                filterName = if (filter == PantryFilter.Expiring) PantryFilter.All.name else PantryFilter.Expiring.name
                            },
                        )
                    },
                )
            }
            if (availability != null && searching && page == InventoryPage.Pantry) {
                HaveAnswer(
                    result = availability!!,
                    query = haveName,
                    onAddItem = viewModel::addLowOrOut,
                    onAddMissing = { viewModel.addLine(haveName, null) },
                )
            }
        }
        LScreen(
            title = if (page == InventoryPage.Pantry) "Pantry" else "To buy",
            modifier = Modifier.weight(1f),
            onBack = onBack,
            itemSpacing = 16.dp,
            topPadding = 12.dp,
            snackbarHost = { NoticeHost(notices) },
            action = {
                TextButton(
                    onClick = { adding = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        if (page == InventoryPage.Pantry) "New item" else "Add to list",
                        color = L.Primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        ) {
            if (page == InventoryPage.Pantry) {
                pantry(
                    items = items,
                    filter = filter,
                    onOpen = { editingId = it },
                    onAddLow = viewModel::addLowOrOut,
                )
            } else {
                shopping(
                    lines = lines,
                    showBought = showBought,
                    onToggleBought = { showBought = !showBought },
                    onCheckOff = viewModel::checkOff,
                    onDelete = { deleteLineId = it },
                )
            }
        }
    }

    if (adding) {
        AddSheet(
            page = page,
            onDismiss = { adding = false },
            onAddItem = { name, quantity, unit ->
                viewModel.addItem(name, quantity, unit)
                adding = false
            },
            onAddLine = { name, qty ->
                viewModel.addLine(name, qty)
                adding = false
            },
        )
    }

    editingId?.let { id ->
        val item = items.firstOrNull { it.id == id } ?: return@let
        EditItemSheet(
            item = item,
            onDismiss = { editingId = null },
            onSave = { quantity, unit ->
                viewModel.saveItem(id, quantity, unit)
                editingId = null
            },
            onDelete = { deleteItemId = id },
        )
    }

    deleteItemId?.let { id ->
        LConfirmDelete(
            title = "Delete this item?",
            onConfirm = {
                viewModel.deleteItem(id)
                deleteItemId = null
                editingId = null
            },
            onDismiss = { deleteItemId = null },
        )
    }
    deleteLineId?.let { id ->
        LConfirmDelete(
            title = "Delete this line?",
            onConfirm = {
                viewModel.deleteLine(id)
                deleteLineId = null
            },
            onDismiss = { deleteLineId = null },
        )
    }
}

@Composable
private fun NoticeHost(host: SnackbarHostState) {
    SnackbarHost(host) { data ->
        Snackbar(snackbarData = data, containerColor = L.Box, contentColor = L.OnBox)
    }
}

@Composable
private fun HaveAnswer(
    result: ItemAvailability,
    query: String,
    onAddItem: (Long) -> Unit,
    onAddMissing: () -> Unit,
) {
    when (result) {
        is ItemAvailability.WithQuantity -> LCard {
            Text(result.item.name, style = MaterialTheme.typography.titleSmall, color = L.OnBox)
            QtyChip(formatQuantity(result.quantity, result.item.unit))
            LGhostButton("Add to list", onClick = { onAddItem(result.item.id) })
        }
        is ItemAvailability.UnknownQuantity -> LCard {
            Text(result.item.name, style = MaterialTheme.typography.titleSmall, color = L.OnBox)
            QtyChip("—")
            LGhostButton("Add to list", onClick = { onAddItem(result.item.id) })
        }
        ItemAvailability.Missing -> LCard {
            Text(query.ifBlank { "Not in pantry" }, style = MaterialTheme.typography.titleSmall, color = L.OnBox)
            LGhostButton("Add to list", onClick = onAddMissing)
        }
    }
}

@Composable
private fun QtyChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = L.OnBox,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(L.BoxDeep)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.pantry(
    items: List<StockItem>,
    filter: PantryFilter,
    onOpen: (Long) -> Unit,
    onAddLow: (Long) -> Unit,
) {
    val today = LocalDate.now()
    if (items.isEmpty()) {
        item(key = "pantry-empty") { LEmpty(Icons.Filled.Kitchen, "Nothing here yet") }
        return
    }
    val visible = when (filter) {
        PantryFilter.All -> items
        PantryFilter.Low -> items.filter { StockQuantity.isLowOrOut(it.quantity) }
        PantryFilter.Expiring -> items.filter { item ->
            val days = item.expiresOn?.let { daysUntil(it, today) } ?: return@filter false
            days in 0..7
        }
    }
    if (visible.isEmpty()) {
        item(key = "pantry-none") { LEmpty(Icons.Filled.Kitchen, "No matches") }
        return
    }
    val soon = visible
        .filter { item -> item.expiresOn?.let { daysUntil(it, today) <= 7 } == true }
        .sortedBy { it.expiresOn }
    val soonIds = soon.map { it.id }.toSet()
    val rest = visible
        .filter { it.id !in soonIds }
        .sortedWith(compareBy<StockItem> { it.expiresOn == null }.thenBy { it.expiresOn }.thenBy { it.name.lowercase() })
    if (soon.isNotEmpty()) {
        item(key = "expiring-head") { LSection("Expiring soon") }
        item(key = "expiring-list") {
            LGroup { StockRows(soon, today, onOpen, onAddLow) }
        }
    }
    if (rest.isNotEmpty()) {
        item(key = "pantry-list") {
            LGroup { StockRows(rest, today, onOpen, onAddLow) }
        }
    }
}

@Composable
private fun StockRows(
    items: List<StockItem>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    onAddLow: (Long) -> Unit,
) {
    items.forEachIndexed { index, item ->
        if (index > 0) LGroupDivider()
        val lowOrOut = StockQuantity.isLowOrOut(item.quantity)
        val expiry = item.expiresOn
        val badge = expiry?.let { expiryBadge(it, today) }
        val expired = expiry?.let { daysUntil(it, today) < 0 } == true
        LGroupRow(
            title = item.name,
            sub = stockSub(item),
            trailing = formatQuantity(item.quantity, item.unit),
            trailingColor = if (item.quantity != null && item.quantity <= 0.0) L.Danger else L.OnBox,
            onClick = { onOpen(item.id) },
            end = if (badge != null || lowOrOut) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (badge != null) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (expired) L.Danger else L.Highlight,
                            )
                        }
                        if (lowOrOut) {
                            LToolButton(Icons.Filled.ShoppingCart, "Add to list", onClick = { onAddLow(item.id) })
                        }
                    }
                }
            } else {
                null
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shopping(
    lines: List<ShoppingLine>,
    showBought: Boolean,
    onToggleBought: () -> Unit,
    onCheckOff: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (lines.isEmpty()) {
        item(key = "shop-empty") { LEmpty(Icons.Filled.ShoppingCart, "Nothing to buy") }
        return
    }
    val open = lines.filter { it.status != ShoppingStatus.CHECKED }
    val bought = lines.filter { it.status == ShoppingStatus.CHECKED }
    if (open.isNotEmpty()) {
        item(key = "shop-hero") { LHero(label = "To buy", value = open.size.toString()) }
        item(key = "shop-list") {
            LGroup {
                open.forEachIndexed { index, line ->
                    if (index > 0) LGroupDivider()
                    LGroupRow(
                        title = line.itemName,
                        trailing = formatQuantity(line.qty, ""),
                        onClick = { onCheckOff(line.id) },
                        end = {
                            LToolButton(Icons.Filled.Delete, "Delete ${line.itemName}", onClick = { onDelete(line.id) })
                        },
                    )
                }
            }
        }
    }
    if (bought.isNotEmpty()) {
        item(key = "bought-head") {
            LSection(
                "Bought",
                action = if (showBought) "Hide" else bought.size.toString(),
                onAction = onToggleBought,
            )
        }
        if (showBought) {
            item(key = "bought-list") {
                LGroup {
                    bought.forEachIndexed { index, line ->
                        if (index > 0) LGroupDivider()
                        BoughtLine(line.itemName) { onDelete(line.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoughtLine(name: String, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = L.OnBoxMuted,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
        )
        LToolButton(Icons.Filled.Delete, "Delete $name", onDelete)
    }
}

@Composable
private fun AddSheet(
    page: InventoryPage,
    onDismiss: () -> Unit,
    onAddItem: (String, Double?, String) -> Unit,
    onAddLine: (String, Double?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var more by rememberSaveable { mutableStateOf(false) }
    val voice = rememberVoiceInput { name = it }
    val quantity = if (more) parseOptionalQuantity(quantityText) else QuantityInput(valid = true, value = null)
    LSheet(
        title = if (page == InventoryPage.Pantry) "New item" else "Add to list",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            if (page == InventoryPage.Pantry) onAddItem(name, quantity.value, if (more) unit else "")
            else onAddLine(name, quantity.value)
        },
        primaryEnabled = name.isNotBlank() && quantity.valid,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LField(name, { name = it }, "Name", modifier = Modifier.weight(1f))
            LToolButton(Icons.Filled.Mic, "Speak", voice.toggle, active = voice.listening)
        }
        LGhostButton(if (more) "Less" else "More", onClick = { more = !more })
        if (more) {
            LField(
                quantityText,
                { quantityText = it },
                "Quantity",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (page == InventoryPage.Pantry) {
                LField(unit, { unit = it }, "Unit")
            }
        }
    }
}

@Composable
private fun EditItemSheet(
    item: StockItem,
    onDismiss: () -> Unit,
    onSave: (Double?, String) -> Unit,
    onDelete: () -> Unit,
) {
    var quantityText by rememberSaveable(item.id) {
        mutableStateOf(item.quantity?.let { formatNumber(it) }.orEmpty())
    }
    var unit by rememberSaveable(item.id) { mutableStateOf(item.unit) }
    val quantity = parseOptionalQuantity(quantityText)
    LSheet(
        title = item.name,
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(quantity.value, unit) },
        primaryEnabled = quantity.valid,
        secondary = "Delete",
        onSecondary = onDelete,
    ) {
        LField(
            quantityText,
            { quantityText = it },
            "Quantity",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        LField(unit, { unit = it }, "Unit")
    }
}

private data class QuantityInput(val valid: Boolean, val value: Double?)

/** Blank is unknown. A non-number is invalid and is not stored as zero or unknown. */
private fun parseOptionalQuantity(text: String): QuantityInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return QuantityInput(valid = true, value = null)
    val number = trimmed.toDoubleOrNull() ?: return QuantityInput(valid = false, value = null)
    return QuantityInput(valid = true, value = number)
}

private fun formatQuantity(quantity: Double?, unit: String): String {
    if (quantity == null) return "—"
    val number = formatNumber(quantity)
    return if (unit.isBlank()) number else "$number $unit"
}

private fun formatNumber(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

private fun stockSub(item: StockItem): String? {
    val bits = buildList {
        if (item.location.isNotBlank()) add(item.location)
        when {
            item.quantity == null -> Unit
            item.quantity <= 0.0 -> add("Out")
            item.quantity <= StockQuantity.LOW_OR_OUT_AT -> add("Low")
        }
    }
    return bits.joinToString(" · ").ifBlank { null }
}

private fun daysUntil(date: LocalDate, today: LocalDate): Long =
    ChronoUnit.DAYS.between(today, date)

private fun expiryBadge(date: LocalDate, today: LocalDate): String {
    val days = daysUntil(date, today)
    return when {
        days == 0L -> "Today"
        days > 0L && days <= 14L -> "${days}d"
        days < 0L && days >= -14L -> "${-days}d ago"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    }
}
