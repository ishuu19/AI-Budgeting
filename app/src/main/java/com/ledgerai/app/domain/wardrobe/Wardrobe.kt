package com.ledgerai.app.domain.wardrobe

import java.time.LocalDate

/**
 * `wardrobe_items` from docs/life-os/data-model.md.
 * [colors] and [seasons] are the `colors[]` and `season[]` columns.
 * [photoPath] is a stored path string. Nothing here reads or uploads a file.
 */
data class WardrobeItem(
    val id: Long = 0,
    val userId: String,
    val name: String,
    val type: String,
    val colors: List<String>,
    val seasons: List<String>,
    val photoPath: String?,
    val laundryStatus: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/**
 * `outfits`. [wornOn] is optional in the schema.
 * This slice only creates a row when a wear is logged, so [wornOn] is set then.
 */
data class Outfit(
    val id: Long = 0,
    val userId: String,
    val occasion: String,
    val itemIds: List<Long>,
    val wornOn: LocalDate?,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

sealed class WearLog {
    data class Saved(val outfitId: Long) : WearLog()
    data object DuplicateSameDay : WearLog()
    data object UnknownGarment : WearLog()
}

/**
 * Facts the model does not state stay unknown.
 * A wear is the same item set on the same [wornOn] day.
 */
object WardrobeRules {
    const val UNKNOWN = "unknown"

    fun colorsOrUnknown(raw: List<String>): List<String> {
        val colors = raw.map { tokenOrNull(it) }.map { it ?: UNKNOWN }.distinct()
        return colors.ifEmpty { listOf(UNKNOWN) }
    }

    fun seasonsAsGiven(raw: List<String>): List<String> =
        raw.mapNotNull { tokenOrNull(it) }.distinct()

    fun typeOrUnknown(raw: String?): String = tokenOrNull(raw) ?: UNKNOWN

    fun laundryOrUnknown(raw: String?): String = tokenOrNull(raw) ?: UNKNOWN

    fun photoPathOrNull(raw: String?): String? = raw?.trim()?.ifEmpty { null }

    fun isDuplicateWear(existing: List<Outfit>, itemIds: List<Long>, day: LocalDate): Boolean {
        val key = itemIds.distinct().toSet()
        if (key.isEmpty()) return false
        return existing.any { outfit ->
            outfit.deletedAt == null &&
                outfit.wornOn == day &&
                outfit.itemIds.toSet() == key
        }
    }

    private fun tokenOrNull(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val folded = trimmed.lowercase()
        if (folded == UNKNOWN || folded == "n/a" || folded == "na" || folded == "?") return null
        return trimmed
    }
}
