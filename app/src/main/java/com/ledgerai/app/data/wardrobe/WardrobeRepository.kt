package com.ledgerai.app.data.wardrobe

import com.ledgerai.app.domain.wardrobe.Outfit
import com.ledgerai.app.domain.wardrobe.WardrobeItem
import com.ledgerai.app.domain.wardrobe.WardrobeRules
import com.ledgerai.app.domain.wardrobe.WearLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Garments and wear log for one user. Hilt provides the Room DAOs.
 * Unit tests still pass in-memory DAO fakes and construct this class directly.
 *
 * [photoPath] is stored as text. This repository does not open or upload files.
 */
class WardrobeRepository(
    private val items: WardrobeItemDao,
    private val outfits: OutfitDao,
) {

    fun observeGarments(userId: String): Flow<List<WardrobeItem>> {
        val owner = userId.trim()
        return items.observeActive(owner).map { rows -> rows.map { it.toDomain() }.visibleTo(owner) }
    }

    fun observeOutfits(userId: String): Flow<List<Outfit>> {
        val owner = userId.trim()
        return outfits.observeActive(owner).map { rows -> rows.map { it.toDomain() }.visibleOutfits(owner) }
    }

    suspend fun listGarments(userId: String): List<WardrobeItem> {
        val owner = userId.trim()
        return items.listAll().map { it.toDomain() }.visibleTo(owner)
    }

    suspend fun listOutfits(userId: String): List<Outfit> {
        val owner = userId.trim()
        return outfits.listAll().map { it.toDomain() }.visibleOutfits(owner)
    }

    suspend fun addGarment(
        userId: String,
        name: String,
        type: String,
        colors: List<String> = emptyList(),
        seasons: List<String> = emptyList(),
        photoPath: String? = null,
        laundryStatus: String? = null,
    ): Long {
        val owner = userId.trim()
        val trimmedName = name.trim()
        if (owner.isEmpty() || trimmedName.isEmpty()) return 0L
        val storedColors = WardrobeRules.colorsOrUnknown(colors)
        return items.insert(
            WardrobeItemEntity(
                userId = owner,
                name = trimmedName,
                type = WardrobeRules.typeOrUnknown(type),
                colors = encodeTextList(storedColors),
                seasons = encodeTextList(WardrobeRules.seasonsAsGiven(seasons)),
                photoPath = WardrobeRules.photoPathOrNull(photoPath),
                laundryStatus = WardrobeRules.laundryOrUnknown(laundryStatus),
                updatedAt = now(),
            )
        )
    }

    suspend fun logWear(
        userId: String,
        itemIds: List<Long>,
        wornOn: LocalDate,
        occasion: String = "",
    ): WearLog {
        val owner = userId.trim()
        if (owner.isEmpty()) return WearLog.UnknownGarment
        val ids = itemIds.distinct()
        val owned = listGarments(owner).map { it.id }.toSet()
        if (ids.isEmpty() || ids.any { it !in owned }) return WearLog.UnknownGarment
        if (WardrobeRules.isDuplicateWear(listOutfits(owner), ids, wornOn)) {
            return WearLog.DuplicateSameDay
        }
        val id = outfits.insert(
            OutfitEntity(
                userId = owner,
                occasion = occasion.trim(),
                itemIds = encodeIds(ids),
                wornOn = wornOn,
                updatedAt = now(),
            )
        )
        return WearLog.Saved(id)
    }

    suspend fun softDeleteGarment(userId: String, id: Long) {
        val ts = now()
        items.softDelete(id, userId.trim(), deletedAt = ts, updatedAt = ts)
    }

    suspend fun softDeleteOutfit(userId: String, id: Long) {
        val ts = now()
        outfits.softDelete(id, userId.trim(), deletedAt = ts, updatedAt = ts)
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun List<WardrobeItem>.visibleTo(userId: String): List<WardrobeItem> =
        filter { it.userId == userId && it.deletedAt == null }
            .sortedBy { it.name.lowercase() }

    private fun List<Outfit>.visibleOutfits(userId: String): List<Outfit> =
        filter { it.userId == userId && it.deletedAt == null }
            .sortedWith(compareByDescending<Outfit> { it.wornOn ?: LocalDate.MIN }.thenByDescending { it.id })
}
