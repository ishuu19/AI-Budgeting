package com.ledgerai.app.domain.capture

import com.ledgerai.app.domain.assistant.AssistantArea
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
        assertEquals(AskRoute.DATA, AskPrompts.parseRoute("""{"route":" Data "}"""))
        assertEquals(
            setOf(AssistantArea.PANTRY, AssistantArea.SHOPPING),
            AskPrompts.parseAreas("""{"route":"data","areas":["Pantry","shopping","nonsense"]}"""),
        )
        assertEquals(emptySet<AssistantArea>(), AskPrompts.parseAreas("""{"route":"data"}"""))
        assertEquals(listOf("eggs", "Ana"), AskPrompts.parseKeywords("""{"keywords":[" eggs ","Ana","x","eggs"]}"""))
        assertEquals(emptyList<String>(), AskPrompts.parseKeywords("""{"route":"data"}"""))
        assertEquals(
            listOf("dentist") to setOf("Events", "People"),
            AskPrompts.parseSearch("""{"keywords":["dentist"],"kinds":["events","People","Bananas"]}"""),
        )
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
        assertEquals(listOf("Eggs", "Rice"), ideas[0].uses)
        assertEquals(listOf("Spring onion", "Soy sauce"), ideas[0].missing)
        assertEquals(15, ideas[0].minutes)
        assertEquals(listOf("Cook rice.", "Scramble eggs."), ideas[0].steps)
    }

    @Test
    fun namesWithAmountsStillMatchThePantry() {
        // The model often copies the amount from the stock line. That must not turn owned food into "missing".
        val pantry = listOf(
            PantryLine("Chicken Leg Piece", 6.0, "", null),
            PantryLine("potatoes", 3.0, "whole", null),
            PantryLine("Turmeric Powder", 250.0, "grams", null),
            PantryLine("onion", 6.0, "", null),
        )
        val ideas = AskPrompts.parseMeals(
            """{"ideas":[{"title":"Chicken curry","minutes":45,
                "uses":["Chicken Leg Piece 6","potatoes 3 whole","Turmeric Powder 250 grams","onions"],
                "missing":["Rice"],"steps":["Cook."]}]}""",
            pantry,
        )
        assertEquals(1, ideas.size)
        assertEquals(listOf("Chicken Leg Piece", "potatoes", "Turmeric Powder", "onion"), ideas[0].uses)
        assertEquals(listOf("Rice"), ideas[0].missing)
    }

    @Test
    fun somethingTheModelCallsMissingButTheUserHasIsNotMissing() {
        val ideas = AskPrompts.parseMeals(
            """{"ideas":[{"title":"Egg rice","uses":["Rice"],"missing":["eggs","Soy sauce"],"steps":["Cook."]}]}""",
            stock,
        )
        assertEquals(listOf("Soy sauce"), ideas[0].missing)
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
