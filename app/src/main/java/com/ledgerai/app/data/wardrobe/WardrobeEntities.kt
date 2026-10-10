package com.ledgerai.app.data.wardrobe

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * `wardrobe_items`. List columns are unit-separator text because this package
 * does not register a Room converter. [photoPath] is a path string only.
 */
@Entity(
    tableName = "wardrobe_items",
    indices = [Index("userId")]
)
data class WardrobeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val name: String,
    val type: String,
    val colors: String,
    val seasons: String,
    val photoPath: String? = null,
    val laundryStatus: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** `outfits`. [itemIds] is a comma-separated list of [WardrobeItemEntity.id]. */
@Entity(
    tableName = "outfits",
    indices = [Index("userId")]
)
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val occasion: String,
    val itemIds: String,
    val wornOn: LocalDate? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
