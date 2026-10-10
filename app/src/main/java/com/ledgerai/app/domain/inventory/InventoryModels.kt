package com.ledgerai.app.domain.inventory

import java.time.LocalDate
import java.time.LocalDateTime

/** `items.kind` values from the shared data model. */
enum class ItemKind(val stored: String) {
    FOOD("food"),
    SUPPLY("supply"),
    CLOTHING("clothing"),
    OTHER("other");

    companion object {
        fun fromStored(value: String): ItemKind =
            entries.firstOrNull { it.stored == value }
                ?: throw IllegalArgumentException("Unknown item kind: $value")
    }
}

/** `items.confidence` values from the shared data model. */
enum class ItemConfidence(val stored: String) {
    CONFIRMED("confirmed"),
    INFERRED("inferred");

    companion object {
        fun fromStored(value: String): ItemConfidence =
            entries.firstOrNull { it.stored == value }
                ?: throw IllegalArgumentException("Unknown item confidence: $value")
    }
}

/**
 * Shopping line `status`. The data model names the column and does not list values.
 * `open` is still to buy. `checked` means the user checked it off.
 */
object ShoppingStatus {
    const val OPEN = "open"
    const val CHECKED = "checked"
}

/** Room/domain row for `items`. */
data class StockItem(
    val id: Long = 0,
    val ownerUserId: String? = null,
    val householdId: String? = null,
    val name: String,
    val category: String = "",
    val quantity: Double? = null,
    val unit: String = "",
    val location: String = "",
    val expiresOn: LocalDate? = null,
    val kind: ItemKind = ItemKind.FOOD,
    val sourceType: String = "manual",
    val sourceId: String? = null,
    val confidence: ItemConfidence = ItemConfidence.CONFIRMED,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Room/domain row for `shopping_lists`. */
data class ShoppingList(
    val id: Long = 0,
    val householdId: String? = null,
    val userId: String? = null,
    val name: String,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** Room/domain row for `shopping_items`. */
data class ShoppingLine(
    val id: Long = 0,
    val listId: Long,
    val itemName: String,
    val qty: Double? = null,
    val addedBy: String? = null,
    val boughtBy: String? = null,
    val boughtAt: LocalDateTime? = null,
    val status: String = ShoppingStatus.OPEN,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
)

/**
 * Answer to "do I have this?". [WithQuantity.quantity] is the stored value.
 * Unknown stock is [UnknownQuantity], not a guessed number. No row is [Missing].
 */
sealed class ItemAvailability {
    data class WithQuantity(val item: StockItem, val quantity: Double) : ItemAvailability()
    data class UnknownQuantity(val item: StockItem) : ItemAvailability()
    data object Missing : ItemAvailability()
}
