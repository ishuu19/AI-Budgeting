package com.ledgerai.app.domain.wardrobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WardrobeRulesTest {

    @Test
    fun blankOrUnknownColorStaysUnknown() {
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(emptyList()))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("")))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("  ")))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("unknown")))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("N/A")))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("?", "na", "NA")))
        assertEquals(listOf(WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf(" ? ")))
        assertEquals("unknown", WardrobeRules.colorsOrUnknown(listOf("")).single())
    }

    @Test
    fun namedColorIsKeptAndNotReplaced() {
        assertEquals(listOf("Navy"), WardrobeRules.colorsOrUnknown(listOf(" Navy ")))
        assertEquals(listOf("burgundy"), WardrobeRules.colorsOrUnknown(listOf(" burgundy ")))
        assertEquals(listOf("Navy", WardrobeRules.UNKNOWN), WardrobeRules.colorsOrUnknown(listOf("Navy", "")))
    }

    @Test
    fun omittedSeasonStaysEmpty() {
        assertEquals(emptyList<String>(), WardrobeRules.seasonsAsGiven(emptyList()))
        assertEquals(emptyList<String>(), WardrobeRules.seasonsAsGiven(listOf(" ", "")))
        assertEquals(listOf("winter"), WardrobeRules.seasonsAsGiven(listOf(" winter ")))
    }

    @Test
    fun blankTypeAndLaundryStayUnknown() {
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.typeOrUnknown(null))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.typeOrUnknown("  "))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.typeOrUnknown("unknown"))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.typeOrUnknown("?"))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.typeOrUnknown("n/a"))
        assertEquals("outer", WardrobeRules.typeOrUnknown(" outer "))
        assertEquals("poncho", WardrobeRules.typeOrUnknown(" poncho "))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.laundryOrUnknown(null))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.laundryOrUnknown("  "))
        assertEquals(WardrobeRules.UNKNOWN, WardrobeRules.laundryOrUnknown("n/a"))
        assertEquals("clean", WardrobeRules.laundryOrUnknown(" clean "))
    }

    @Test
    fun blankPhotoPathIsNotStored() {
        assertEquals(null, WardrobeRules.photoPathOrNull(null))
        assertEquals(null, WardrobeRules.photoPathOrNull("  "))
        assertEquals("wardrobe/navy-coat.jpg", WardrobeRules.photoPathOrNull(" wardrobe/navy-coat.jpg "))
    }

    @Test
    fun sameItemsOnTheSameDayIsADuplicateWear() {
        val day = LocalDate.of(2026, 10, 11)
        val existing = listOf(outfit(itemIds = listOf(2L, 1L), wornOn = day))
        assertTrue(WardrobeRules.isDuplicateWear(existing, listOf(1L, 2L), day))
    }

    @Test
    fun differentDayOrDifferentItemsIsNotADuplicateWear() {
        val day = LocalDate.of(2026, 10, 11)
        val existing = listOf(
            outfit(itemIds = listOf(1L, 2L), wornOn = day),
            outfit(itemIds = listOf(1L), wornOn = null)
        )
        assertFalse(WardrobeRules.isDuplicateWear(existing, listOf(1L, 2L), day.plusDays(1)))
        assertFalse(WardrobeRules.isDuplicateWear(existing, listOf(1L), day))
        assertFalse(WardrobeRules.isDuplicateWear(existing, emptyList(), day))
    }

    @Test
    fun softDeletedWearDoesNotBlockTheSameDay() {
        val day = LocalDate.of(2026, 10, 11)
        val existing = listOf(outfit(itemIds = listOf(1L), wornOn = day, deletedAt = 5L))
        assertFalse(WardrobeRules.isDuplicateWear(existing, listOf(1L), day))
    }

    private fun outfit(itemIds: List<Long>, wornOn: LocalDate?, deletedAt: Long? = null) = Outfit(
        id = 1L,
        userId = "user-1",
        occasion = "",
        itemIds = itemIds,
        wornOn = wornOn,
        deletedAt = deletedAt
    )
}
