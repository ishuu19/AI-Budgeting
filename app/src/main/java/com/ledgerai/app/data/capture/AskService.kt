package com.ledgerai.app.data.capture

import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.capture.AskPrompts
import com.ledgerai.app.domain.capture.AskRoute
import com.ledgerai.app.domain.capture.BuyIdea
import com.ledgerai.app.domain.capture.MealIdea
import com.ledgerai.app.domain.capture.OutfitIdea
import com.ledgerai.app.domain.capture.PantryLine
import com.ledgerai.app.domain.capture.WardrobeLine
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.wardrobe.WardrobeItem
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AskResult {
    data class Meals(val ideas: List<MealIdea>) : AskResult
    data class Outfits(val ideas: List<OutfitIdea>, val garments: Map<Long, WardrobeItem>) : AskResult
    data class Shopping(val items: List<BuyIdea>) : AskResult
    /** Not a question for the pantry or wardrobe: hand the text to the normal voice pipeline. */
    data object LogIt : AskResult
    data class Empty(val reason: String) : AskResult
}

/**
 * "Make something for today" and "I have a party". The model reads names and short facts only:
 * stock lines for meals, garment lines for outfits. Photos are never sent here. The app shows
 * a photo only for the garments the model picked, and only from local storage.
 */
@Singleton
class AskService @Inject constructor(
    private val ai: AiGateway,
    private val inventory: InventoryRepository,
    private val wardrobe: WardrobeRepository,
    private val weather: WeatherSource,
    private val calendar: com.ledgerai.app.data.repository.CalendarRepository,
) {

    suspend fun ask(userId: String, text: String): AskResult {
        val message = text.trim()
        if (message.isEmpty()) return AskResult.LogIt
        // If the router call fails (offline, no key) the message takes the normal voice path.
        val route = ai.fast(AskPrompts.ROUTE_SYSTEM, message).getOrNull()
            ?.let(AskPrompts::parseRoute) ?: AskRoute.LOG
        return when (route) {
            AskRoute.MEAL -> meals(message)
            AskRoute.OUTFIT -> outfits(userId, message)
            AskRoute.BUY -> buy(userId, message)
            AskRoute.LOG -> AskResult.LogIt
        }
    }

    /** Everything the user owns that counts as food, with what is low and where it is. */
    private suspend fun pantryLines(): List<PantryLine> =
        inventory.listItems()
            .filter { it.kind == ItemKind.FOOD }
            .map {
                PantryLine(
                    name = it.name,
                    quantity = it.quantity,
                    unit = it.unit,
                    expiresOn = it.expiresOn,
                    location = it.location,
                    low = com.ledgerai.app.domain.inventory.StockQuantity.isLowOrOut(it.quantity),
                )
            }

    private suspend fun meals(message: String): AskResult {
        val stock = pantryLines()
        if (stock.isEmpty()) {
            return AskResult.Empty("Your pantry is empty. Take a photo of your fridge and I'll add what I see.")
        }
        val raw = ai.fast(AskPrompts.MEAL_SYSTEM, AskPrompts.mealUser(message, stock, LocalDate.now()))
            .getOrElse { return AskResult.Empty("I couldn't reach the AI. Try again when you're online.") }
        val ideas = AskPrompts.parseMeals(raw, stock)
        return if (ideas.isEmpty()) AskResult.Empty("Not possible right now. Every meal I could think of needs more than two things you don't have. Add some groceries or scan a receipt, then ask again.")
        else AskResult.Meals(ideas)
    }

    private suspend fun buy(userId: String, message: String): AskResult {
        val stock = pantryLines()
        val onList = inventory.listLists().flatMap { list -> inventory.listLines(list.id) }
            .filter { it.status != com.ledgerai.app.domain.inventory.ShoppingStatus.CHECKED }
            .map { it.itemName }
        val raw = ai.fast(AskPrompts.BUY_SYSTEM, AskPrompts.buyUser(message, stock, onList, LocalDate.now()))
            .getOrElse { return AskResult.Empty("I couldn't reach the AI. Try again when you're online.") }
        val items = AskPrompts.parseBuy(raw, onList)
        return if (items.isEmpty()) AskResult.Empty("You're stocked. Nothing needs buying right now.") else AskResult.Shopping(items)
    }

    /** Adds the suggested items to the shopping list in one tap. Returns how many were added. */
    suspend fun addToShopping(names: List<String>): Int {
        val listId = inventory.ensureList("Shopping")
        var added = 0
        for (name in names) if (inventory.addShoppingLine(listId, name, qty = null, addedBy = null) != 0L) added++
        return added
    }

    private suspend fun outfits(userId: String, message: String): AskResult {
        val garments = wardrobe.listGarments(userId)
        if (garments.isEmpty()) {
            return AskResult.Empty("Your wardrobe is empty. Photograph a few clothes and I'll name and file them.")
        }
        val today = LocalDate.now()
        val lastWorn = wardrobe.listOutfits(userId)
            .flatMap { o -> o.itemIds.map { id -> id to o.wornOn } }
            .filter { it.second != null }
            .groupBy({ it.first }, { it.second!! })
            .mapValues { (_, days) -> days.max() }
        val lines = garments.map {
            WardrobeLine(
                it.id, it.name, it.type, it.colors, it.seasons, it.laundryStatus,
                daysSinceWorn = lastWorn[it.id]?.let { d -> java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt() },
            )
        }
        val events = runCatching {
            calendar.observeRange(today, today).first()
                .filter { !it.allDay || it.hasDate }
                .map { e -> (if (e.allDay) "all day" else e.startAt.toLocalTime().toString().take(5)) + " " + e.title }
        }.getOrDefault(emptyList())
        val raw = ai.fast(AskPrompts.OUTFIT_SYSTEM, AskPrompts.outfitUser(message, lines, weather.describeNow(), events))
            .getOrElse { return AskResult.Empty("I couldn't reach the AI. Try again when you're online.") }
        val ideas = AskPrompts.parseOutfits(raw, lines)
        return if (ideas.isEmpty()) AskResult.Empty("Nothing in your wardrobe fits that yet.")
        else AskResult.Outfits(ideas, garments.associateBy { it.id })
    }
}
