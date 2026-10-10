package com.ledgerai.app.data.inventory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.inventory.ShoppingStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `items`. [householdId] is a nullable id with no foreign key.
 * [kind] and [confidence] are the data-model strings (`food`, `confirmed`).
 * [updatedAt] and [deletedAt] follow the shared sync convention.
 */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: String? = null,
    val householdId: String? = null,
    val name: String,
    val category: String = "",
    val quantity: Double? = null,
    val unit: String = "",
    val location: String = "",
    val expiresOn: LocalDate? = null,
    val kind: String = ItemKind.FOOD.stored,
    val sourceType: String = "manual",
    val sourceId: String? = null,
    val confidence: String = ItemConfidence.CONFIRMED.stored,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** `shopping_lists`. [householdId] is a nullable id with no foreign key. */
@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: String? = null,
    val userId: String? = null,
    val name: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

/** `shopping_items`. [qty] null means the quantity is unknown. */
@Entity(
    tableName = "shopping_items",
    indices = [Index("listId")]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val itemName: String,
    val qty: Double? = null,
    val addedBy: String? = null,
    val boughtBy: String? = null,
    val boughtAt: LocalDateTime? = null,
    val status: String = ShoppingStatus.OPEN,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
