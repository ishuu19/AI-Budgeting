package com.ledgerai.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AskPromptsTest {

    private val stock = listOf(
        PantryLine("Eggs", null, "", null),
        PantryLine("Rice", 2.0, "kg", null),
        PantryLine("Spinach", 1.0, "bag", LocalDate.of(2026, 10, 12)),
    )
    private val wardrobe = listOf(
        WardrobeLine(1, "White shirt", "shirt", listOf("white"), emptyList(), "clean"),
        WardrobeLine(2, "Navy blazer", "jacket", listOf("navy"), emptyList(), "clean"),
    )

    @Test
    fun routeDefaultsToLogOnAnythingUnclear() {
        assertEquals(AskRoute.LOG, AskPrompts.parseRoute("hmm"))
        assertEquals(AskRoute.LOG, AskPrompts.parseRoute("""{"route":"banana"}"""))
        assertEquals(AskRoute.MEAL, AskPrompts.parseRoute("""{"route":"meal"}"""))
        assertEquals(AskRoute.OUTFIT, AskPrompts.parseRoute("""{"route":" Outfit "}"""))
    }

    @Test
    fun mealPromptNeverStatesAnUnknownAmount() {
        val prompt = AskPrompts.mealUser("dinner", stock, LocalDate.of(2026, 10, 11))
        assertTrue(prompt.contains("Eggs (amount unknown)"))
        assertTrue(prompt.contains("Rice 2 kg"))
        assertTrue(prompt.contains("expires 2026-10-12"))
    }

    @Test
    fun mealIdeaMovesUnownedItemsToMissing() {
        val ideas = AskPrompts.parseMeals(
            """{"ideas":[{"title":"Egg fried rice","minutes":15,"uses":["eggs","Rice","Soy sauce"],"missing":["Spring onion"],"steps":["1. Cook rice.","Scramble eggs."]}]}""",
            stock,
        )
        assertEquals(1, ideas.size)
        assertEquals(listOf("eggs", "Rice"), ideas[0].uses)
        assertEquals(listOf("Spring onion", "Soy sauce"), ideas[0].missing)
        assertEquals(15, ideas[0].minutes)
        assertEquals(listOf("Cook rice.", "Scramble eggs."), ideas[0].steps)
    }

    @Test
    fun mealNeedingMoreThanTwoMissingIsDropped() {
        val ideas = AskPrompts.parseMeals(
            """{"ideas":[
                {"title":"Too much shopping","uses":["Rice"],"missing":["Beef","Onion","Carrot"],"steps":["Cook."]},
                {"title":"Spinach eggs","uses":["Eggs","Spinach"],"missing":[],"steps":["Cook."]},
                {"title":"Needs two","uses":["Rice"],"missing":["Soy sauce","Spring onion"],"steps":["Cook."]}]}""",
            stock,
        )
        assertEquals(listOf("Spinach eggs", "Needs two"), ideas.map { it.title })
    }

    @Test
    fun noWorkableMealGivesNoIdeas() {
        assertTrue(AskPrompts.parseMeals("""{"ideas":[]}""", stock).isEmpty())
        assertTrue(
            AskPrompts.parseMeals("""{"ideas":[{"title":"X","uses":[],"missing":["a","b","c"],"steps":[]}]}""", stock).isEmpty()
        )
    }

    @Test
    fun outfitDropsInventedIdsAndEmptyOutfits() {
        val ideas = AskPrompts.parseOutfits(
            """{"outfits":[
                {"title":"Smart","itemIds":[1,2,99],"why":"Classic."},
                {"title":"Ghost","itemIds":[98,99],"why":"None of these exist."}]}""",
            wardrobe,
        )
        assertEquals(1, ideas.size)
        assertEquals(listOf(1L, 2L), ideas[0].itemIds)
    }

    @Test
    fun outfitPromptListsNamesNotPhotos() {
        val prompt = AskPrompts.outfitUser("party", wardrobe)
        assertTrue(prompt.contains("1 | White shirt | shirt | white"))
        assertFalse(prompt.contains("photo", ignoreCase = true))
    }

    @Test
    fun badJsonGivesNoIdeas() {
        assertTrue(AskPrompts.parseMeals("nope", stock).isEmpty())
        assertTrue(AskPrompts.parseOutfits("nope", wardrobe).isEmpty())
    }
}
