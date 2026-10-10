package com.ledgerai.app.data.wardrobe

import com.ledgerai.app.domain.wardrobe.WearLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WardrobeRepositoryTest {

    @Test
    fun blankColorTypeAndLaundryStayUnknown() = runBlocking {
        val repo = memoryWardrobeRepository()
        val id = repo.addGarment(
            userId = "a",
            name = "Navy shirt",
            type = "?",
            colors = listOf("n/a", "unknown"),
            seasons = listOf(" "),
            photoPath = "  ",
            laundryStatus = " ",
        )
        assertTrue(id > 0L)
        val item = repo.listGarments("a").single()
        assertEquals("Navy shirt", item.name)
        assertEquals(listOf("unknown"), item.colors)
        assertEquals("unknown", item.type)
        assertEquals(emptyList<String>(), item.seasons)
        assertEquals("unknown", item.laundryStatus)
        assertNull(item.photoPath)
    }

    @Test
    fun namedColorAndPhotoPathAreStoredAsGiven() = runBlocking {
        val repo = memoryWardrobeRepository()
        repo.addGarment(
            userId = "a",
            name = " Coat ",
            type = " outer ",
            colors = listOf(" Navy "),
            seasons = listOf(" winter "),
            photoPath = " wardrobe/coat.jpg ",
            laundryStatus = " clean ",
        )
        val item = repo.listGarments("a").single()
        assertEquals("Coat", item.name)
        assertEquals("outer", item.type)
        assertEquals(listOf("Navy"), item.colors)
        assertEquals(listOf("winter"), item.seasons)
        assertEquals("wardrobe/coat.jpg", item.photoPath)
        assertEquals("clean", item.laundryStatus)
    }

    @Test
    fun listIsUserScopedAndSkipsSoftDeleted() = runBlocking {
        val repo = memoryWardrobeRepository()
        val id = repo.addGarment(userId = "a", name = "Coat", type = "outer", colors = listOf("Navy"))
        repo.addGarment(userId = "a", name = "Scarf", type = "neck", colors = listOf("Red"))
        repo.addGarment(userId = "b", name = "Hat", type = "head", colors = listOf("Black"))
        repo.softDeleteGarment("a", id)
        assertEquals(listOf("Scarf"), repo.listGarments("a").map { it.name })
        assertEquals(listOf("Hat"), repo.listGarments("b").map { it.name })
    }

    @Test
    fun blankNameOrUserIsNotStored() = runBlocking {
        val repo = memoryWardrobeRepository()
        assertEquals(0L, repo.addGarment(userId = "a", name = "  ", type = "outer", colors = emptyList()))
        assertEquals(0L, repo.addGarment(userId = "  ", name = "Coat", type = "outer", colors = emptyList()))
        assertTrue(repo.listGarments("a").isEmpty())
    }

    @Test
    fun sameItemsOnTheSameDayIsADuplicateWear() = runBlocking {
        val repo = memoryWardrobeRepository()
        val coat = repo.addGarment(userId = "a", name = "Coat", type = "outer", colors = listOf("Navy"))
        val day = LocalDate.of(2026, 10, 11)
        val first = repo.logWear("a", listOf(coat), day)
        val second = repo.logWear("a", listOf(coat), day)
        assertTrue(first is WearLog.Saved)
        assertTrue(second is WearLog.DuplicateSameDay)
        val saved = repo.listOutfits("a").single()
        assertEquals(day, saved.wornOn)
        assertEquals(listOf(coat), saved.itemIds)
        assertTrue(repo.logWear("a", listOf(coat), day.plusDays(1)) is WearLog.Saved)
    }

    @Test
    fun deletedWearCanBeLoggedAgainOnThatDay() = runBlocking {
        val repo = memoryWardrobeRepository()
        val coat = repo.addGarment(userId = "a", name = "Coat", type = "outer", colors = listOf("Navy"))
        val day = LocalDate.of(2026, 10, 11)
        val first = repo.logWear("a", listOf(coat), day) as WearLog.Saved
        repo.softDeleteOutfit("a", first.outfitId)
        assertTrue(repo.logWear("a", listOf(coat), day) is WearLog.Saved)
        assertEquals(1, repo.listOutfits("a").size)
    }

    @Test
    fun wearRequiresAGarmentThisUserStillHas() = runBlocking {
        val repo = memoryWardrobeRepository()
        val hat = repo.addGarment(userId = "b", name = "Hat", type = "head", colors = listOf("Black"))
        val coat = repo.addGarment(userId = "a", name = "Coat", type = "outer", colors = listOf("Navy"))
        val day = LocalDate.of(2026, 10, 11)
        assertTrue(repo.logWear("a", listOf(hat), day) is WearLog.UnknownGarment)
        assertTrue(repo.logWear("a", emptyList(), day) is WearLog.UnknownGarment)
        assertTrue(repo.logWear("  ", listOf(coat), day) is WearLog.UnknownGarment)
        repo.softDeleteGarment("a", coat)
        assertTrue(repo.listGarments("a").isEmpty())
        assertTrue(repo.logWear("a", listOf(coat), day) is WearLog.UnknownGarment)
        assertTrue(repo.listOutfits("a").isEmpty())
        assertTrue(repo.listOutfits("b").isEmpty())
    }
}

private fun memoryWardrobeRepository(): WardrobeRepository =
    WardrobeRepository(MemoryWardrobeItemDao(), MemoryOutfitDao())

private class MemoryWardrobeItemDao : WardrobeItemDao {
    private val rows = mutableListOf<WardrobeItemEntity>()
    private val state = MutableStateFlow<List<WardrobeItemEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive(userId: String): Flow<List<WardrobeItemEntity>> =
        state.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun listAll(): List<WardrobeItemEntity> = rows.toList()

    override suspend fun getById(id: Long): WardrobeItemEntity? = rows.find { it.id == id }

    override suspend fun insert(entity: WardrobeItemEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.removeAll { it.id == id }
        rows.add(entity.copy(id = id))
        state.value = rows.toList()
        return id
    }

    override suspend fun softDelete(id: Long, userId: String, deletedAt: Long, updatedAt: Long) {
        val index = rows.indexOfFirst { it.id == id && it.userId == userId && it.deletedAt == null }
        if (index < 0) return
        rows[index] = rows[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        state.value = rows.toList()
    }
}

private class MemoryOutfitDao : OutfitDao {
    private val rows = mutableListOf<OutfitEntity>()
    private val state = MutableStateFlow<List<OutfitEntity>>(emptyList())
    private var nextId = 1L

    override fun observeActive(userId: String): Flow<List<OutfitEntity>> =
        state.map { list -> list.filter { it.userId == userId && it.deletedAt == null } }

    override suspend fun listAll(): List<OutfitEntity> = rows.toList()

    override suspend fun getById(id: Long): OutfitEntity? = rows.find { it.id == id }

    override suspend fun insert(entity: OutfitEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.removeAll { it.id == id }
        rows.add(entity.copy(id = id))
        state.value = rows.toList()
        return id
    }

    override suspend fun softDelete(id: Long, userId: String, deletedAt: Long, updatedAt: Long) {
        val index = rows.indexOfFirst { it.id == id && it.userId == userId && it.deletedAt == null }
        if (index < 0) return
        rows[index] = rows[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        state.value = rows.toList()
    }
}
