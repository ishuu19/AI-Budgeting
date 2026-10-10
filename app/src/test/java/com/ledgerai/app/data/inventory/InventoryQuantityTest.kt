package com.ledgerai.app.data.inventory

import com.ledgerai.app.domain.inventory.ItemAvailability
import com.ledgerai.app.domain.inventory.ShoppingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryQuantityTest {

    @Test
    fun adjustLeavesUnknownQuantityUnknown() = runBlocking {
        val repo = memoryInventoryRepository()
        val id = repo.addItem(name = "Eggs", quantity = null)
        repo.adjustQuantity(id, delta = 2.0)
        assertNull(repo.listItems().single().quantity)
    }

    @Test
    fun adjustAddsDeltaWhenQuantityIsKnown() = runBlocking {
        val repo = memoryInventoryRepository()
        val id = repo.addItem(name = "Eggs", quantity = 3.0)
        repo.adjustQuantity(id, delta = -1.0)
        assertEquals(2.0, repo.listItems().single().quantity!!, 0.0)
    }

    @Test
    fun checkOffUnknownQtyDoesNotCreateStock() = runBlocking {
        val repo = memoryInventoryRepository()
        val listId = repo.ensureList("Shopping")
        val lineId = repo.addShoppingLine(listId, itemName = "Rice", qty = null, addedBy = "me")
        repo.checkOff(lineId, boughtBy = "me")
        assertTrue(repo.listItems().isEmpty())
        assertEquals(ShoppingStatus.CHECKED, repo.listLines(listId).single().status)
    }

    @Test
    fun checkOffKnownQtyCreatesStockWithThatQuantity() = runBlocking {
        val repo = memoryInventoryRepository()
        val listId = repo.ensureList("Shopping")
        val lineId = repo.addShoppingLine(listId, itemName = "Milk", qty = 2.0, addedBy = "me")
        repo.checkOff(lineId, boughtBy = "me")
        val item = repo.listItems().single()
        assertEquals("Milk", item.name)
        assertEquals(2.0, item.quantity!!, 0.0)
    }

    @Test
    fun checkOffLeavesExistingUnknownStockUnknown() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Milk", quantity = null)
        val listId = repo.ensureList("Shopping")
        val lineId = repo.addShoppingLine(listId, itemName = "milk", qty = 2.0, addedBy = "me")
        repo.checkOff(lineId)
        assertEquals(1, repo.listItems().size)
        assertNull(repo.listItems().single().quantity)
    }

    @Test
    fun checkOffAddsKnownLineQtyOntoKnownStock() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Milk", quantity = 1.0)
        val listId = repo.ensureList("Shopping")
        val lineId = repo.addShoppingLine(listId, itemName = "MILK", qty = 2.0, addedBy = "me")
        repo.checkOff(lineId)
        assertEquals(3.0, repo.listItems().single().quantity!!, 0.0)
    }

    @Test
    fun lowOrOutListsKnownLowQuantitiesAndSkipsUnknown() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Mystery", quantity = null)
        repo.addItem(name = "Salt", quantity = 0.0)
        repo.addItem(name = "Oil", quantity = 1.0)
        repo.addItem(name = "Rice", quantity = 4.0)
        assertEquals(listOf("Oil", "Salt"), repo.listLowOrOut().map { it.name }.sorted())
    }

    @Test
    fun softDeletedRowsAreExcluded() = runBlocking {
        val repo = memoryInventoryRepository()
        val itemId = repo.addItem(name = "Salt", quantity = 0.0)
        val listId = repo.ensureList("Shopping")
        val lineId = repo.addShoppingLine(listId, itemName = "Bread", qty = 1.0, addedBy = "me")
        repo.softDeleteItem(itemId)
        repo.softDeleteLine(lineId)
        repo.softDeleteList(listId)
        assertTrue(repo.listItems().isEmpty())
        assertTrue(repo.listLowOrOut().isEmpty())
        assertTrue(repo.listLines(listId).isEmpty())
        assertTrue(repo.listLists().isEmpty())
    }

    @Test
    fun findByNameReturnsKnownQuantity() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Eggs", quantity = 3.0)
        val found = repo.findByName("  eggs ")
        assertTrue(found is ItemAvailability.WithQuantity)
        val withQuantity = found as ItemAvailability.WithQuantity
        assertEquals("Eggs", withQuantity.item.name)
        assertEquals(3.0, withQuantity.quantity, 0.0)
        assertEquals(3.0, withQuantity.item.quantity!!, 0.0)
        assertEquals(1, repo.listItems().size)
    }

    @Test
    fun findByNameReturnsUnknownQuantity() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Eggs", quantity = null)
        val found = repo.findByName("Eggs")
        assertTrue(found is ItemAvailability.UnknownQuantity)
        assertNull((found as ItemAvailability.UnknownQuantity).item.quantity)
        assertEquals(1, repo.listItems().size)
    }

    @Test
    fun findByNameMissingDoesNotInsertARow() = runBlocking {
        val repo = memoryInventoryRepository()
        assertEquals(ItemAvailability.Missing, repo.findByName("Eggs"))
        assertTrue(repo.listItems().isEmpty())

        val id = repo.addItem(name = "Eggs", quantity = 1.0)
        repo.softDeleteItem(id)
        assertEquals(ItemAvailability.Missing, repo.findByName("Eggs"))
        assertTrue(repo.listItems().isEmpty())
    }

    @Test
    fun findByNameBlankNameIsMissing() = runBlocking {
        val repo = memoryInventoryRepository()
        repo.addItem(name = "Eggs", quantity = 2.0)
        assertEquals(ItemAvailability.Missing, repo.findByName(""))
        assertEquals(ItemAvailability.Missing, repo.findByName("   "))
        assertEquals(1, repo.listItems().size)
    }

    @Test
    fun addLowOrOutAddsKnownLowItemWithNullLineQuantity() = runBlocking {
        val repo = memoryInventoryRepository()
        val salt = repo.addItem(name = "Salt", quantity = 0.0)
        val oil = repo.addItem(name = "Oil", quantity = 1.0)
        val listId = repo.ensureList("Shopping")

        val saltLine = repo.addLowOrOutToList(salt, listId, addedBy = "me")
        val oilLine = repo.addLowOrOutToList(oil, listId, addedBy = "me")

        val lines = repo.listLines(listId)
        assertEquals(setOf(saltLine, oilLine), lines.map { it.id }.toSet())
        assertTrue(saltLine != 0L)
        assertTrue(oilLine != 0L)
        assertEquals(listOf("Oil", "Salt"), lines.map { it.itemName }.sorted())
        assertTrue(lines.all { it.qty == null && it.addedBy == "me" })
        assertEquals(0.0, repo.listItems().single { it.name == "Salt" }.quantity!!, 0.0)
        assertEquals(1.0, repo.listItems().single { it.name == "Oil" }.quantity!!, 0.0)
    }

    @Test
    fun addLowOrOutSkipsUnknownPlentyAndMissing() = runBlocking {
        val repo = memoryInventoryRepository()
        val rice = repo.addItem(name = "Rice", quantity = 1.1)
        val mystery = repo.addItem(name = "Mystery", quantity = null)
        val bread = repo.addItem(name = "Bread", quantity = 0.0)
        repo.softDeleteItem(bread)
        val listId = repo.ensureList("Shopping")

        assertEquals(0L, repo.addLowOrOutToList(rice, listId, addedBy = "me"))
        assertEquals(0L, repo.addLowOrOutToList(mystery, listId, addedBy = "me"))
        assertEquals(0L, repo.addLowOrOutToList(bread, listId, addedBy = "me"))
        assertEquals(0L, repo.addLowOrOutToList(itemId = 999L, listId = listId))
        assertTrue(repo.listLines(listId).isEmpty())
        assertNull(repo.listItems().single { it.name == "Mystery" }.quantity)
        assertEquals(2, repo.listItems().size)
    }
}

/** In-memory DAOs. [listAll] returns soft-deleted rows so the repository filter is what the test checks. */
private fun memoryInventoryRepository(): InventoryRepository =
    InventoryRepository(MemoryItemDao(), MemoryShoppingListDao(), MemoryShoppingItemDao())

private class MemoryItemDao : ItemDao {
    private val rows = mutableListOf<ItemEntity>()
    private val state = MutableStateFlow<List<ItemEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<List<ItemEntity>> =
        state.map { list -> list.filter { it.deletedAt == null } }

    override suspend fun listAll(): List<ItemEntity> = rows.toList()

    override suspend fun getById(id: Long): ItemEntity? = rows.find { it.id == id }

    override suspend fun insert(entity: ItemEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.removeAll { it.id == id }
        rows.add(entity.copy(id = id))
        state.value = rows.toList()
        return id
    }

    override suspend fun update(entity: ItemEntity) {
        val index = rows.indexOfFirst { it.id == entity.id }
        if (index >= 0) rows[index] = entity
        state.value = rows.toList()
    }

    override suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        state.value = rows.toList()
    }
}

private class MemoryShoppingListDao : ShoppingListDao {
    private val rows = mutableListOf<ShoppingListEntity>()
    private val state = MutableStateFlow<List<ShoppingListEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<List<ShoppingListEntity>> =
        state.map { list -> list.filter { it.deletedAt == null } }

    override suspend fun listAll(): List<ShoppingListEntity> = rows.toList()

    override suspend fun getById(id: Long): ShoppingListEntity? = rows.find { it.id == id }

    override suspend fun insert(entity: ShoppingListEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.removeAll { it.id == id }
        rows.add(entity.copy(id = id))
        state.value = rows.toList()
        return id
    }

    override suspend fun update(entity: ShoppingListEntity) {
        val index = rows.indexOfFirst { it.id == entity.id }
        if (index >= 0) rows[index] = entity
        state.value = rows.toList()
    }

    override suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        state.value = rows.toList()
    }
}

private class MemoryShoppingItemDao : ShoppingItemDao {
    private val rows = mutableListOf<ShoppingItemEntity>()
    private val state = MutableStateFlow<List<ShoppingItemEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForList(listId: Long): Flow<List<ShoppingItemEntity>> =
        state.map { list -> list.filter { it.listId == listId && it.deletedAt == null } }

    override suspend fun listAll(): List<ShoppingItemEntity> = rows.toList()

    override suspend fun getById(id: Long): ShoppingItemEntity? = rows.find { it.id == id }

    override suspend fun insert(entity: ShoppingItemEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.removeAll { it.id == id }
        rows.add(entity.copy(id = id))
        state.value = rows.toList()
        return id
    }

    override suspend fun update(entity: ShoppingItemEntity) {
        val index = rows.indexOfFirst { it.id == entity.id }
        if (index >= 0) rows[index] = entity
        state.value = rows.toList()
    }

    override suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        state.value = rows.toList()
    }
}
