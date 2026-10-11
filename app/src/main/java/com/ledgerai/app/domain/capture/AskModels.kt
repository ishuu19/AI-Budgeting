package com.ledgerai.app.domain.capture

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

/** Where a typed or spoken message goes. [LOG] is the existing voice pipeline (expenses, reminders, notes). */
enum class AskRoute { MEAL, OUTFIT, LOG }

/** One line the model may rely on. Only names and short facts are sent, never photos. */
data class PantryLine(val name: String, val quantity: Double?, val unit: String, val expiresOn: LocalDate?)

data class WardrobeLine(
    val id: Long,
    val name: String,
    val type: String,
    val colors: List<String>,
    val seasons: List<String>,
    val laundry: String,
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

object AskPrompts {

    val ROUTE_SYSTEM: String = """
        Classify the user's message for a personal organizer. Reply with ONLY JSON: {"route":"meal|outfit|log"}.
        meal   = they want something to eat or cook, or ask what they can make with what they have.
        outfit = they want clothes or an outfit suggested (an event, weather, a plan to go somewhere).
        log    = anything else: recording an expense, a reminder, a note, a person, a bill.
        A statement about money spent on food is "log", not "meal".
    """.trimIndent()

    fun parseRoute(raw: String): AskRoute {
        val root = rootObject(raw) ?: return AskRoute.LOG
        return when (root.get("route")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()) {
            "meal" -> AskRoute.MEAL
            "outfit" -> AskRoute.OUTFIT
            else -> AskRoute.LOG
        }
    }

    /** The most items a suggested meal may lack. More than this is not a suggestion, it is a shopping trip. */
    const val MAX_MISSING = 2

    val MEAL_SYSTEM: String = """
        You are a practical home cook. You get the user's stock list (amounts may be unknown) and a request.
        Reply with ONLY JSON:
        {"ideas":[{"title":"","minutes":20,"uses":["names from the list"],"missing":["things not in the list"],"steps":["short step","short step"]}]}
        Rules:
        - Suggest 1 to 3 meals, best first. Prefer meals that need nothing extra, and use items that expire soonest.
        - A meal may lack AT MOST 2 ingredients. Fewer is better. Never suggest a meal that needs 3 or more things the user does not have.
        - "uses" may contain ONLY names exactly as written in the list. Salt, pepper, cooking oil and water may be assumed.
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
        stock.take(120).forEach { s ->
            val qty = s.quantity?.let { " ${trim(it)} ${s.unit}".trimEnd() } ?: " (amount unknown)"
            val exp = s.expiresOn?.let { ", expires $it" }.orEmpty()
            appendLine("- ${s.name}$qty$exp")
        }
    }

    val OUTFIT_SYSTEM: String = """
        You are a stylist who picks from the user's own wardrobe. You get one line per garment: id | name | type | colors | seasons | laundry.
        Reply with ONLY JSON: {"outfits":[{"title":"","itemIds":[1,2],"why":"one short sentence"}]}
        Rules:
        - Give 1 to 3 outfits. Use ONLY ids from the list. Avoid garments whose laundry status says dirty or washing.
        - An outfit needs at least two garments unless it is a single dress or suit.
        - If nothing fits the occasion, return {"outfits":[]}.
    """.trimIndent()

    fun outfitUser(request: String, wardrobe: List<WardrobeLine>, weather: String? = null): String = buildString {
        appendLine("Occasion: ${request.trim().take(300)}")
        // Only stated when known. Missing weather is left out, never guessed.
        if (!weather.isNullOrBlank()) appendLine("Weather now: $weather")
        appendLine("Wardrobe:")
        wardrobe.take(150).forEach { w ->
            appendLine("${w.id} | ${w.name} | ${w.type} | ${w.colors.joinToString("/")} | ${w.seasons.joinToString("/")} | ${w.laundry}")
        }
    }

    fun parseMeals(raw: String, stock: List<PantryLine>): List<MealIdea> {
        val root = rootObject(raw) ?: return emptyList()
        val known = stock.map { it.name.trim().lowercase() }.toSet()
        return root.getAsJsonArray("ideas")?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val title = o.text("title").orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val uses = o.list("uses")
            // A named "use" the user does not own is a missing item, never stock.
            val (owned, notOwned) = uses.partition { it.trim().lowercase() in known }
            val missing = (o.list("missing") + notOwned).distinct()
            // The limit is enforced here too, so a model that ignores it cannot send the user shopping.
            if (missing.size > MAX_MISSING) return@mapNotNull null
            val steps = o.list("steps").map { it.trim().replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.isNotEmpty() }
            MealIdea(
                title = title,
                minutes = o.get("minutes")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }?.takeIf { it in 1..600 },
                uses = owned,
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
