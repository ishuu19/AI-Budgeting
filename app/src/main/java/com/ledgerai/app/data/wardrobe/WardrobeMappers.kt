package com.ledgerai.app.data.wardrobe

import com.ledgerai.app.domain.wardrobe.Outfit
import com.ledgerai.app.domain.wardrobe.WardrobeItem

private const val LIST_SEP = "\u001F"

internal fun encodeTextList(values: List<String>): String = values.joinToString(LIST_SEP)

internal fun decodeTextList(stored: String): List<String> =
    if (stored.isEmpty()) emptyList() else stored.split(LIST_SEP)

internal fun encodeIds(values: List<Long>): String = values.joinToString(",")

internal fun decodeIds(stored: String): List<Long> =
    if (stored.isBlank()) emptyList() else stored.split(",").map { it.toLong() }

fun WardrobeItemEntity.toDomain(): WardrobeItem = WardrobeItem(
    id = id,
    userId = userId,
    name = name,
    type = type,
    colors = decodeTextList(colors),
    seasons = decodeTextList(seasons),
    photoPath = photoPath,
    laundryStatus = laundryStatus,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun OutfitEntity.toDomain(): Outfit = Outfit(
    id = id,
    userId = userId,
    occasion = occasion,
    itemIds = decodeIds(itemIds),
    wornOn = wornOn,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
