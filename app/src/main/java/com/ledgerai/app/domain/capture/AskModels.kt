package com.ledgerai.app.domain.capture

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

/** Where a typed or spoken message goes. [LOG] is the existing voice pipeline (expenses, reminders, notes). */
enum class AskRoute { MEAL, OUTFIT, BUY, LOG }

/** One line the model may rely on. Only names and short facts are sent, never photos. */
data class PantryLine(
    val name: String,
    val quantity: Double?,
    val unit: String,
    val expiresOn: LocalDate?,
    val location: String = "",
    val low: Boolean = false,
)

data class WardrobeLine(
    val id: Long,
    val name: String,
    val type: String,
    val colors: List<String>,
    val seasons: List<String>,
    val laundry: String,
    /** Days since it was last worn. Null when it never was. */
    val daysSinceWorn: Int? = null,
)

/** One meal. [missing] is what is not in stock; ideas never carry more than [AskPrompts.MAX_MISSING]. */
data class MealIdea(
    val title: String,
    val minutes: Int?,
    val uses: List<String>,
    val missing: List<String>,
    val steps: List<String>,
)

data class OutfitIdea(val title: String, val itemIds: List<Long>, val why: String)

/** One thing worth buying and the short reason, from what is low, used up or about to expire. */
data class BuyIdea(val name: String, val reason: String)

object AskPrompts {

    val ROUTE_SYSTEM: String = """
        Classify the user's message for a personal organizer. Reply with ONLY JSON: {"route":"meal|outfit|buy|log"}.
        meal   = they want something to eat or cook, or ask what they can make with what they have.
        outfit = they want clothes or an outfit suggested (an event, weather, a plan to go somewhere).
        buy    = they ask what to buy, what is running out, or what to add to a shopping list.
        log    = anything else: recording an expense, a reminder, a note, a person, a bill.
        A statement about money spent on food is "log", not "meal".
    """.trimIndent()

    fun parseRoute(raw: String): AskRoute {
        val root = rootObject(raw) ?: return AskRoute.LOG
        return when (root.get("route")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()) {
            "meal" -> AskRoute.MEAL
            "outfit" -> AskRoute.OUTFIT
            "buy" -> AskRoute.BUY
            else -> AskRoute.LOG
        }
    }

    /** The most items a suggested meal may lack. More than this is not a suggestion, it is a shopping trip. */
    const val MAX_MISSING = 2

    val MEAL_SYSTEM: String = """
        You are a practical home cook cooking from THIS user's own kitchen. You get everything they own (amounts may be unknown) and a request.
        The stock list is complete. Read all of it before you answer and build meals from it: combine several items,
        not just one or two, and use up what expires first. Items marked LOW are nearly gone, so do not rely on them.
        Reply with ONLY JSON:
        {"ideas":[{"title":"","minutes":20,"uses":["names from the list"],"missing":["things not in the list"],"steps":["short step","short step"]}]}
        Rules:
        - Suggest 1 to 3 meals, best first. Prefer meals that need nothing extra, and use items that expire soonest.
        - A meal may lack AT MOST 2 ingredients. Fewer is better. Never suggest a meal that needs 3 or more things the user does not have.
        - "uses" and "missing" hold item NAMES ONLY, copied from the list without amounts, units or numbers
          (write "Chicken Leg Piece", not "Chicken Leg Piece 6"). Salt, pepper, cooking oil and water may be assumed.
        - Anything else not in the list goes in "missing".
        - "steps": 4 to 8 clear steps a beginner can follow, in order. Start with prep (wash, chop, measure), then cooking with heat
          level and timing ("medium heat, 3 minutes"), then how to tell it is done and how to serve. One action per step.
        - Give amounts as a typical serving ("2 eggs", "1 cup rice"). Never say the user has more than the list shows.
        - If no meal works with at most 2 missing ingredients, return {"ideas":[]}. Do not stretch.
    """.trimIndent()

    fun mealUser(request: String, stock: List<PantryLine>, today: LocalDate): String = buildString {
        appendLine("Today: $today")
        appendLine("Request: ${request.trim().take(300)}")
        appendLine("In stock:")
        stock.take(300).forEach { s ->
            val qty = s.quantity?.let { " ${trim(it)} ${s.unit}".trimEnd() } ?: " (amount unknown)"
            val exp = s.expiresOn?.let { ", expires $it" }.orEmpty()
            val where = if (s.location.isNotBlank()) ", in the ${s.location}" else ""
            val low = if (s.low) " [LOW]" else ""
            appendLine("- ${s.name}$qty$exp$where$low")
        }
    }

    val OUTFIT_SYSTEM: String = """
        You are a stylist who picks from the user's own wardrobe. You get one line per garment: id | name | type | colors | seasons | laundry | last worn.
        The wardrobe list is complete. Look at all of it. Match the occasion, today's events and the weather, and prefer pieces
        that were not worn recently. Build whole outfits (top, bottom or dress, shoes, outerwear if cold) from what they own.
        Reply with ONLY JSON: {"outfits":[{"title":"","itemIds":[1,2],"why":"one short sentence"}]}
        Rules:
        - Give 1 to 3 outfits. Use ONLY ids from the list. Avoid garments whose laundry status says dirty or washing.
        - An outfit needs at least two garments unless it is a single dress or suit.
        - If nothing fits the occasion, return {"outfits":[]}.
    """.trimIndent()

    fun outfitUser(request: String, wardrobe: List<WardrobeLine>, weather: String? = null, events: List<String> = emptyList()): String = buildString {
        appendLine("Occasion: ${request.trim().take(300)}")
        // Only stated when known. Missing weather is left out, never guessed.
        if (!weather.isNullOrBlank()) appendLine("Weather now: $weather")
        if (events.isNotEmpty()) appendLine("Today: ${events.take(6).joinToString("; ")}")
        appendLine("Wardrobe:")
        wardrobe.take(300).forEach { w ->
            val worn = w.daysSinceWorn?.let { if (it == 0) "today" else "$it days ago" } ?: "never"
            appendLine("${w.id} | ${w.name} | ${w.type} | ${w.colors.joinToString("/")} | ${w.seasons.joinToString("/")} | ${w.laundry} | $worn")
        }
    }

    val BUY_SYSTEM: String = """
        You help the user decide what to buy. You get everything in their pantry (with LOW flags and expiry dates) and what is
        already on their shopping list. Reply with ONLY JSON: {"items":[{"name":"","reason":"short reason"}]}
        Rules:
        - Suggest at most 12 items, most important first: things that are LOW or used up, things that are about to expire and need
          replacing, and staples that go with what they already cook with.
        - Never suggest something already on the shopping list, and never suggest something they clearly have plenty of.
        - "reason" is under 8 words, for example "only a little left" or "expires in 2 days".
        - If nothing is needed, return {"items":[]}.
    """.trimIndent()

    fun buyUser(request: String, stock: List<PantryLine>, onList: List<String>, today: LocalDate): String = buildString {
        appendLine("Today: $today")
        appendLine("Request: ${request.trim().take(300)}")
        appendLine("Already on the shopping list: ${if (onList.isEmpty()) "nothing" else onList.take(80).joinToString(", ")}")
        appendLine("Pantry:")
        stock.take(300).forEach { s ->
            val qty = s.quantity?.let { " ${trim(it)} ${s.unit}".trimEnd() } ?: " (amount unknown)"
            val exp = s.expiresOn?.let { ", expires $it" }.orEmpty()
            appendLine("- ${s.name}$qty$exp${if (s.low) " [LOW]" else ""}")
        }
    }

    fun parseBuy(raw: String, onList: List<String>): List<BuyIdea> {
        val root = rootObject(raw) ?: return emptyList()
        val skip = onList.map { it.trim().lowercase() }.toSet()
        return root.getAsJsonArray("items")?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = o.text("name").orEmpty()
            if (name.isEmpty() || name.lowercase() in skip) null else BuyIdea(name.take(60), o.text("reason").orEmpty().take(80))
        }.orEmpty().distinctBy { it.name.lowercase() }.take(12)
    }

    private val AMOUNT_WORDS = Regex("\\b(g|gm|gram|grams|kg|ml|l|litre|litres|pack|packs|pcs|pc|piece|pieces|whole|root|tbsp|tsp|cup|cups|bag|bags|can|cans)\\b")

    /** A comparable form of an item name: no amounts, units, brackets or case. */
    internal fun clean(raw: String): String = raw.lowercase()
        .replace(Regex("[()\\[\\],]"), " ")
        .replace(Regex("\\b\\d+(\\.\\d+)?\\b"), " ")
        .replace(AMOUNT_WORDS, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * The pantry item a model-written name refers to, or null. Matches ignore amounts and case, and accept
     * a name that contains the other ("chicken" and "Chicken Leg Piece"), so "Chicken Leg Piece 6" still counts.
     */
    internal fun matchStock(name: String, stock: List<PantryLine>): PantryLine? {
        val u = clean(name)
        if (u.length < 2) return null
        stock.firstOrNull { clean(it.name) == u }?.let { return it }
        return stock
            .filter { val n = clean(it.name); n.length >= 3 && u.length >= 3 && (u.contains(n) || n.contains(u)) }
            .maxByOrNull { clean(it.name).length }
    }

    fun parseMeals(raw: String, stock: List<PantryLine>): List<MealIdea> {
        val root = rootObject(raw) ?: return emptyList()
        return root.getAsJsonArray("ideas")?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val title = o.text("title").orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val uses = o.list("uses")
            // Names go back to what is in the pantry; anything not found there is missing, never stock.
            val ownedLines = uses.mapNotNull { matchStock(it, stock) }.distinctBy { it.name.lowercase() }
            val notOwned = uses.filter { matchStock(it, stock) == null }
            val missing = (o.list("missing") + notOwned)
                .map { it.trim() }
                .filter { it.isNotEmpty() && matchStock(it, stock) == null }
                .distinctBy { clean(it) }
            // The limit is enforced here too, so a model that ignores it cannot send the user shopping.
            if (missing.size > MAX_MISSING) return@mapNotNull null
            val steps = o.list("steps").map { it.trim().replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.isNotEmpty() }
            MealIdea(
                title = title,
                minutes = o.get("minutes")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }?.takeIf { it in 1..600 },
                uses = ownedLines.map { it.name },
                missing = missing,
                steps = steps,
            )
        }.orEmpty().sortedBy { it.missing.size }.take(3)
    }

    fun parseOutfits(raw: String, wardrobe: List<WardrobeLine>): List<OutfitIdea> {
        val root = rootObject(raw) ?: return emptyList()
        val valid = wardrobe.map { it.id }.toSet()
        return root.getAsJsonArray("outfits")?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val ids = o.getAsJsonArray("itemIds")
                ?.mapNotNull { runCatching { it.asLong }.getOrNull() }
                ?.filter { it in valid }?.distinct().orEmpty()
            val title = o.text("title").orEmpty()
            if (ids.isEmpty() || title.isEmpty()) null else OutfitIdea(title, ids, o.text("why").orEmpty())
        }.orEmpty().take(3)
    }

    private fun rootObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject }.getOrNull()
    }

    private fun JsonObject.text(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive }?.asString?.trim()

    private fun JsonObject.list(k: String): List<String> =
        getAsJsonArray(k)?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null } }.orEmpty()

    private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
