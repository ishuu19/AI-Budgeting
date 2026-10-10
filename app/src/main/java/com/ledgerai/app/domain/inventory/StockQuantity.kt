package com.ledgerai.app.domain.inventory

/**
 * Quantity rules for pantry stock and shopping check-off.
 * A null quantity is unknown. These functions never replace unknown with a number.
 */
object StockQuantity {

    /** Known quantity at or below this is low or out. Unknown is neither. */
    const val LOW_OR_OUT_AT = 1.0

    fun isVisible(deletedAt: Long?): Boolean = deletedAt == null

    fun adjust(current: Double?, delta: Double): Double? = current?.plus(delta)

    fun isLowOrOut(quantity: Double?): Boolean =
        quantity != null && quantity <= LOW_OR_OUT_AT

    /**
     * Move a checked line into stock only when its quantity is known.
     * Unknown line quantity does not create stock.
     * Unknown stock stays unknown even when the line quantity is known.
     */
    fun stockAfterCheckOff(lineQty: Double?, existing: StockItem?): StockWrite? {
        if (lineQty == null) return null
        if (existing == null) return StockWrite.Create(lineQty)
        val current = existing.quantity ?: return null
        return StockWrite.Update(current + lineQty)
    }

    /** Classifies a visible row. A null quantity stays unknown; a missing row stays missing. */
    fun availability(item: StockItem?): ItemAvailability = when {
        item == null || !isVisible(item.deletedAt) -> ItemAvailability.Missing
        item.quantity == null -> ItemAvailability.UnknownQuantity(item)
        else -> ItemAvailability.WithQuantity(item, item.quantity)
    }
}

sealed class StockWrite {
    data class Create(val quantity: Double) : StockWrite()
    data class Update(val quantity: Double) : StockWrite()
}
