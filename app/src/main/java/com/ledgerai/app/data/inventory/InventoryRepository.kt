package com.ledgerai.app.data.inventory

import com.ledgerai.app.domain.inventory.ItemAvailability
import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.inventory.ShoppingLine
import com.ledgerai.app.domain.inventory.ShoppingList
import com.ledgerai.app.domain.inventory.ShoppingStatus
import com.ledgerai.app.domain.inventory.StockItem
import com.ledgerai.app.domain.inventory.StockQuantity
import com.ledgerai.app.domain.inventory.StockWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pantry and shopping list. Hilt provides this from [com.ledgerai.app.di.DatabaseModule].
 */
class InventoryRepository(
    private val items: ItemDao,
    private val lists: ShoppingListDao,
    private val lines: ShoppingItemDao,
) {

    fun observeItems(): Flow<List<StockItem>> =
        items.observeActive().map { rows -> rows.map { it.toDomain() }.visible().sortedByName() }

    fun observeLines(listId: Long): Flow<List<ShoppingLine>> =
        lines.observeForList(listId).map { rows ->
            rows.map { it.toDomain() }.filter { StockQuantity.isVisible(it.deletedAt) }
        }

    suspend fun listItems(): List<StockItem> =
        items.listAll().map { it.toDomain() }.visible().sortedByName()

    suspend fun listLowOrOut(): List<StockItem> =
        listItems().filter { StockQuantity.isLowOrOut(it.quantity) }

    /**
     * "Do I have [name]?" First visible case-insensitive match.
     * Quantity is only present when the row already stores one.
     */
    suspend fun findByName(name: String): ItemAvailability {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ItemAvailability.Missing
        val match = listItems().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        return StockQuantity.availability(match)
    }

    suspend fun listLists(): List<ShoppingList> =
        lists.listAll().map { it.toDomain() }.filter { StockQuantity.isVisible(it.deletedAt) }

    suspend fun listLines(listId: Long): List<ShoppingLine> =
        lines.listAll().map { it.toDomain() }.filter {
            it.listId == listId && StockQuantity.isVisible(it.deletedAt)
        }

    suspend fun addItem(
        name: String,
        quantity: Double?,
        ownerUserId: String? = null,
        householdId: String? = null,
        category: String = "",
        unit: String = "",
        location: String = "",
        expiresOn: LocalDate? = null,
        kind: ItemKind = ItemKind.FOOD,
        sourceType: String = "manual",
        sourceId: String? = null,
        confidence: ItemConfidence = ItemConfidence.CONFIRMED,
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        return items.insert(
            ItemEntity(
                ownerUserId = ownerUserId,
                householdId = householdId,
                name = trimmed,
                category = category,
                quantity = quantity,
                unit = unit,
                location = location,
                expiresOn = expiresOn,
                kind = kind.stored,
                sourceType = sourceType,
                sourceId = sourceId,
                confidence = confidence.stored,
                updatedAt = now(),
            )
        )
    }

    /** Adds [delta] only when the current quantity is known. Unknown stays null. */
    suspend fun adjustQuantity(id: Long, delta: Double) {
        val row = items.getById(id) ?: return
        if (!StockQuantity.isVisible(row.deletedAt)) return
        val next = StockQuantity.adjust(row.quantity, delta)
        if (next == null) return
        items.update(row.copy(quantity = next, updatedAt = now()))
    }

    /** Writes the quantity the user entered. Null stays unknown. */
    suspend fun saveItem(id: Long, quantity: Double?, unit: String) {
        val row = items.getById(id) ?: return
        if (!StockQuantity.isVisible(row.deletedAt)) return
        items.update(row.copy(quantity = quantity, unit = unit.trim(), updatedAt = now()))
    }

    suspend fun ensureList(
        name: String,
        userId: String? = null,
        householdId: String? = null,
    ): Long {
        val trimmed = name.trim()
        val existing = listLists().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing.id
        return lists.insert(
            ShoppingListEntity(
                name = trimmed,
                userId = userId,
                householdId = householdId,
                updatedAt = now(),
            )
        )
    }

    /**
     * Adds one known low or out item to [listId].
     * Unknown quantity is not low and is not added.
     * The shopping line quantity stays null; on-hand stock is not a buy amount.
     * Returns the new line id, or 0 when nothing was added.
     */
    suspend fun addLowOrOutToList(itemId: Long, listId: Long, addedBy: String? = null): Long {
        val row = items.getById(itemId) ?: return 0L
        if (!StockQuantity.isVisible(row.deletedAt)) return 0L
        if (!StockQuantity.isLowOrOut(row.quantity)) return 0L
        return addShoppingLine(
            listId = listId,
            itemName = row.name,
            qty = null,
            addedBy = addedBy,
        )
    }

    suspend fun addShoppingLine(
        listId: Long,
        itemName: String,
        qty: Double?,
        addedBy: String?,
    ): Long {
        val trimmed = itemName.trim()
        if (trimmed.isEmpty()) return 0L
        return lines.insert(
            ShoppingItemEntity(
                listId = listId,
                itemName = trimmed,
                qty = qty,
                addedBy = addedBy,
                status = ShoppingStatus.OPEN,
                updatedAt = now(),
            )
        )
    }

    /**
     * Marks the line checked. Creates or increases stock only when both the
     * line quantity and any existing stock quantity are known.
     */
    suspend fun checkOff(lineId: Long, boughtBy: String? = null) {
        val line = lines.getById(lineId) ?: return
        if (!StockQuantity.isVisible(line.deletedAt)) return
        if (line.status == ShoppingStatus.CHECKED) return
        val ts = now()
        lines.update(
            line.copy(
                status = ShoppingStatus.CHECKED,
                boughtBy = boughtBy ?: line.boughtBy,
                boughtAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()),
                updatedAt = ts,
            )
        )
        val existing = listItems().firstOrNull { it.name.equals(line.itemName.trim(), ignoreCase = true) }
        when (val write = StockQuantity.stockAfterCheckOff(line.qty, existing)) {
            null -> Unit
            is StockWrite.Create -> {
                val list = lists.getById(line.listId)
                addItem(
                    name = line.itemName,
                    quantity = write.quantity,
                    ownerUserId = list?.userId,
                    householdId = list?.householdId,
                    sourceType = "manual",
                    sourceId = line.id.toString(),
                    confidence = ItemConfidence.CONFIRMED,
                )
            }
            is StockWrite.Update -> {
                val row = items.getById(existing!!.id) ?: return
                items.update(row.copy(quantity = write.quantity, updatedAt = now()))
            }
        }
    }

    suspend fun softDeleteItem(id: Long) {
        val ts = now()
        items.softDelete(id, deletedAt = ts, updatedAt = ts)
    }

    suspend fun softDeleteLine(id: Long) {
        val ts = now()
        lines.softDelete(id, deletedAt = ts, updatedAt = ts)
    }

    suspend fun softDeleteList(id: Long) {
        val ts = now()
        lists.softDelete(id, deletedAt = ts, updatedAt = ts)
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun List<StockItem>.visible(): List<StockItem> =
        filter { StockQuantity.isVisible(it.deletedAt) }

    private fun List<StockItem>.sortedByName(): List<StockItem> =
        sortedBy { it.name.lowercase() }
}
