package com.ledgerai.app.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure quantity rules. Name search and list insertion live on the repository. */
class StockQuantityTest {

    @Test
    fun adjustLeavesUnknownQuantityUnknown() {
        assertNull(StockQuantity.adjust(current = null, delta = 2.0))
        assertNull(StockQuantity.adjust(current = null, delta = 0.0))
        assertEquals(5.0, StockQuantity.adjust(current = 3.0, delta = 2.0)!!, 0.0)
    }

    @Test
    fun unknownQuantityIsNotLowOrOut() {
        assertFalse(StockQuantity.isLowOrOut(null))
        assertTrue(StockQuantity.isLowOrOut(0.0))
        assertTrue(StockQuantity.isLowOrOut(StockQuantity.LOW_OR_OUT_AT))
        assertFalse(StockQuantity.isLowOrOut(StockQuantity.LOW_OR_OUT_AT + 0.1))
    }

    @Test
    fun checkOffDoesNotInventAQuantity() {
        assertNull(StockQuantity.stockAfterCheckOff(lineQty = null, existing = null))
        assertNull(StockQuantity.stockAfterCheckOff(lineQty = null, existing = item(quantity = 1.0)))
        assertNull(StockQuantity.stockAfterCheckOff(lineQty = 2.0, existing = item(quantity = null)))
        assertEquals(
            StockWrite.Create(2.0),
            StockQuantity.stockAfterCheckOff(lineQty = 2.0, existing = null),
        )
        assertEquals(
            StockWrite.Update(3.0),
            StockQuantity.stockAfterCheckOff(lineQty = 2.0, existing = item(quantity = 1.0)),
        )
    }

    @Test
    fun availabilityKeepsUnknownAndMissingDistinct() {
        val known = item(quantity = 2.0)
        val out = item(quantity = 0.0)
        val unknown = item(quantity = null)
        val deleted = item(quantity = 1.0).copy(deletedAt = 5L)
        assertEquals(ItemAvailability.WithQuantity(known, 2.0), StockQuantity.availability(known))
        assertEquals(ItemAvailability.WithQuantity(out, 0.0), StockQuantity.availability(out))
        assertEquals(ItemAvailability.UnknownQuantity(unknown), StockQuantity.availability(unknown))
        assertEquals(ItemAvailability.Missing, StockQuantity.availability(null))
        assertEquals(ItemAvailability.Missing, StockQuantity.availability(deleted))
    }

    @Test
    fun softDeletedRowIsNotVisible() {
        assertTrue(StockQuantity.isVisible(deletedAt = null))
        assertFalse(StockQuantity.isVisible(deletedAt = 9L))
    }

    private fun item(quantity: Double?) = StockItem(name = "Milk", quantity = quantity)
}
