package com.ledgerai.app.presentation.screens.inventory

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.domain.inventory.ItemAvailability
import com.ledgerai.app.domain.inventory.ShoppingLine
import com.ledgerai.app.domain.inventory.ShoppingStatus
import com.ledgerai.app.domain.inventory.StockItem
import com.ledgerai.app.domain.inventory.StockQuantity
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private enum class InventoryPage { Pantry, Shopping }

private val expiryLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

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
            _addNotice.value = if (lineId != 0L) "Added to shopping" else "Not added"
        }
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
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    LScreen(
        title = if (page == InventoryPage.Pantry) "Pantry" else "Shopping",
        onBack = onBack,
        fab = { LFab(Icons.Filled.Add, onClick = { adding = true }, label = "Add") }
    ) {
        item(key = "pages") {
            LSegments(
                options = InventoryPage.entries,
                selected = page,
                label = { if (it == InventoryPage.Pantry) "Pantry" else "Shopping" },
                onSelect = { page = it }
            )
        }
        if (page == InventoryPage.Pantry) {
            pantry(
                items = items,
                haveName = haveName,
                onHaveName = { haveName = it },
                onCheck = { viewModel.findByName(haveName) },
                availability = availability,
                addNotice = addNotice,
                onOpen = { editingId = it },
                onAddLow = viewModel::addLowOrOut,
            )
        } else {
            shopping(lines, onCheckOff = viewModel::checkOff, onDelete = viewModel::deleteLine)
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
            }
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
            onDelete = {
                viewModel.deleteItem(id)
                editingId = null
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pantry(
    items: List<StockItem>,
    haveName: String,
    onHaveName: (String) -> Unit,
    onCheck: () -> Unit,
    availability: ItemAvailability?,
    addNotice: String?,
    onOpen: (Long) -> Unit,
    onAddLow: (Long) -> Unit,
) {
    item(key = "have-name") {
        LField(haveName, onHaveName, "Do I have")
    }
    item(key = "have-check") {
        LGhostButton("Check", onClick = onCheck, enabled = haveName.isNotBlank())
    }
    if (availability != null) {
        item(key = "have-answer") {
            Text(
                availabilityText(availability),
                style = MaterialTheme.typography.bodyLarge,
                color = L.Ink
            )
        }
    }
    if (!addNotice.isNullOrBlank()) {
        item(key = "add-notice") {
            Text(addNotice, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        }
    }
    if (items.isEmpty()) {
        item(key = "pantry-empty") { LEmpty(Icons.Filled.Kitchen, "No items") }
        return
    }
    val low = items.count { StockQuantity.isLowOrOut(it.quantity) }
    item(key = "pantry-hero") { LHero(label = "Low", value = low.toString()) }
    item(key = "pantry-list") {
        LGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) LGroupDivider()
                val lowOrOut = StockQuantity.isLowOrOut(item.quantity)
                LGroupRow(
                    title = item.name,
                    sub = stockSub(item),
                    trailing = formatQuantity(item.quantity, item.unit),
                    trailingColor = if (item.quantity != null && item.quantity <= 0.0) L.Danger else L.Gold,
                    onClick = { onOpen(item.id) },
                    end = if (lowOrOut) {
                        {
                            TextButton(onClick = { onAddLow(item.id) }) {
                                Text("Add to list", color = L.Gold)
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shopping(
    lines: List<ShoppingLine>,
    onCheckOff: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (lines.isEmpty()) {
        item(key = "shop-empty") { LEmpty(Icons.Filled.ShoppingCart, "Nothing to buy") }
        return
    }
    val open = lines.count { it.status != ShoppingStatus.CHECKED }
    item(key = "shop-hero") { LHero(label = "To buy", value = open.toString()) }
    item(key = "shop-list") {
        LGroup {
            lines.forEachIndexed { index, line ->
                if (index > 0) LGroupDivider()
                val checked = line.status == ShoppingStatus.CHECKED
                LGroupRow(
                    title = line.itemName,
                    trailing = if (checked) "Bought" else formatQuantity(line.qty, ""),
                    onClick = if (checked) null else ({ onCheckOff(line.id) }),
                    end = {
                        IconButton(onClick = { onDelete(line.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${line.itemName}",
                                tint = L.OnBox
                            )
                        }
                    }
                )
            }
        }
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
    val quantity = parseOptionalQuantity(quantityText)
    LSheet(
        title = if (page == InventoryPage.Pantry) "Add item" else "Add line",
        onDismiss = onDismiss,
        primary = "Add",
        onPrimary = {
            if (page == InventoryPage.Pantry) onAddItem(name, quantity.value, unit)
            else onAddLine(name, quantity.value)
        },
        primaryEnabled = name.isNotBlank() && quantity.valid
    ) {
        LField(name, { name = it }, "Name")
        LField(
            quantityText,
            { quantityText = it },
            "Quantity",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (page == InventoryPage.Pantry) {
            LField(unit, { unit = it }, "Unit")
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
        onSecondary = onDelete
    ) {
        LField(
            quantityText,
            { quantityText = it },
            "Quantity",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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

private fun availabilityText(result: ItemAvailability): String = when (result) {
    is ItemAvailability.WithQuantity ->
        "${result.item.name}: ${formatQuantity(result.quantity, result.item.unit)}"
    is ItemAvailability.UnknownQuantity -> "${result.item.name}: Unknown"
    ItemAvailability.Missing -> "You do not have it"
}

private fun formatQuantity(quantity: Double?, unit: String): String {
    if (quantity == null) return "Unknown"
    val number = formatNumber(quantity)
    return if (unit.isBlank()) number else "$number $unit"
}

private fun formatNumber(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toLong().toString() else quantity.toString()

private fun stockSub(item: StockItem): String? {
    val bits = buildList {
        if (item.location.isNotBlank()) add(item.location)
        item.expiresOn?.let { add("Expires ${expiryLabel.format(it)}") }
        when {
            item.quantity == null -> Unit
            item.quantity <= 0.0 -> add("Out")
            item.quantity <= StockQuantity.LOW_OR_OUT_AT -> add("Low")
        }
    }
    return bits.joinToString(" · ").ifBlank { null }
}
