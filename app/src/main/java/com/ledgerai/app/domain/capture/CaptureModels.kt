package com.ledgerai.app.domain.capture

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

enum class CaptureKind(val wire: String) {
    RECEIPT("receipt"),
    FOOD("food"),
    CLOTHING("clothing"),
    PERSON("person"),
    OTHER("other");

    companion object {
        fun fromWire(raw: String?): CaptureKind =
            entries.firstOrNull { it.wire == raw?.trim()?.lowercase() } ?: OTHER
    }
}

data class FoodGuess(
    val name: String,
    val quantity: Double?,
    val unit: String,
    val location: String,
    val expiresOn: LocalDate?,
)

data class GarmentGuess(
    val name: String,
    val type: String,
    val colors: List<String>,
    val seasons: List<String>,
)

data class ReceiptLineGuess(val text: String, val qty: Double?, val price: Double?, val name: String? = null, val food: Boolean = false)

/** A repeating charge seen on a receipt or billing screenshot. Never filed without a tap. */
data class SubscriptionGuess(val period: String, val nextRenewalOn: LocalDate?)

data class ReceiptGuess(
    val merchant: String?,
    val total: Double?,
    val currency: String?,
    val date: LocalDate?,
    val lines: List<ReceiptLineGuess>,
    val subscription: SubscriptionGuess?,
)

data class PersonGuess(
    val name: String?,
    val on: LocalDate?,
    val place: String?,
    val memory: String?,
)

/** What the AI saw. Values it could not read stay null; nothing here is invented. */
data class CaptureResult(
    val kind: CaptureKind,
    val title: String,
    val description: String,
    val food: List<FoodGuess> = emptyList(),
    val garment: GarmentGuess? = null,
    val receipt: ReceiptGuess? = null,
    val person: PersonGuess? = null,
)

object CapturePrompts {
    val SYSTEM: String = """
        You file photos for a personal life-organizer app. Look at the image and the user's optional note.
        Reply with ONLY one JSON object, no markdown:
        {"kind":"receipt|food|clothing|person|other",
         "title":"2-5 word name",
         "description":"one or two plain sentences about what is visible",
         "food":[{"name":"","quantity":null,"unit":"","location":"fridge|freezer|pantry|","expiresOn":null}],
         "clothing":{"name":"","type":"shirt|pants|dress|jacket|shoes|accessory|other","colors":[],"seasons":[]},
         "receipt":{"merchant":null,"total":null,"currency":null,"date":null,
                    "lines":[{"text":"","name":"","food":false,"qty":null,"price":null}],
                    "subscription":null},
         "person":{"name":null,"when":null,"where":null,"memory":null}}
        Rules:
        - Fill only the block that matches "kind"; use null or [] for the rest.
        - Never guess. If a price, quantity, date, or name is not clearly visible or stated, use null.
        - A person's name comes ONLY from the user's note, never from how someone looks.
        - "person.memory" is a short factual sentence built from the note (what happened, who, where).
        - Dates are YYYY-MM-DD. "when" may be a date from the note; otherwise null.
        - For each receipt line, "name" is the plain product name (no codes), and "food" is true only for food or
          drink you would keep at home.
        - "subscription" is {"period":"weekly|monthly|quarterly|yearly","nextRenewalOn":null} only if the
          receipt or screen shows a repeating charge, else null.
        - For a fridge or pantry photo, list each distinct food you can identify in "food".
    """.trimIndent()

    fun user(note: String): String =
        if (note.isBlank()) "Describe and classify this photo. JSON only."
        else "User note: \"${note.trim().take(500)}\"\nDescribe and classify this photo. JSON only."
}

object CaptureJson {
    /** Parses the model reply. Returns null when no usable JSON object is present. */
    fun parse(raw: String): CaptureResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject }
            .getOrNull() ?: return null

        val kind = CaptureKind.fromWire(root.str("kind"))
        val title = root.str("title").orEmpty().trim().take(80)
        val description = root.str("description").orEmpty().trim().take(400)

        val food = root.arr("food").mapNotNull { e ->
            val o = e.asObjOrNull() ?: return@mapNotNull null
            val name = o.str("name")?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            FoodGuess(
                name = name.take(60),
                quantity = o.num("quantity"),
                unit = o.str("unit").orEmpty().trim().take(16),
                location = o.str("location").orEmpty().trim().lowercase().take(16),
                expiresOn = o.date("expiresOn"),
            )
        }

        val garment = root.obj("clothing")?.let { o ->
            val name = o.str("name")?.trim().orEmpty().ifEmpty { title }
            if (name.isEmpty()) null else GarmentGuess(
                name = name.take(60),
                type = o.str("type").orEmpty().trim(),
                colors = o.strList("colors"),
                seasons = o.strList("seasons"),
            )
        }

        val receipt = root.obj("receipt")?.let { o ->
            ReceiptGuess(
                merchant = o.str("merchant")?.trim()?.ifEmpty { null },
                total = o.num("total"),
                currency = o.str("currency")?.trim()?.ifEmpty { null },
                date = o.date("date"),
                lines = o.arr("lines").mapNotNull { e ->
                    val l = e.asObjOrNull() ?: return@mapNotNull null
                    val text = l.str("text")?.trim().orEmpty()
                    if (text.isEmpty()) null else ReceiptLineGuess(
                        text.take(120), l.num("qty"), l.num("price"),
                        name = l.str("name")?.trim()?.ifEmpty { null }?.take(60),
                        food = l.get("food")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false,
                    )
                },
                subscription = o.obj("subscription")?.let { s ->
                    val period = s.str("period")?.trim()?.lowercase()
                    if (period in PERIODS) SubscriptionGuess(period!!, s.date("nextRenewalOn")) else null
                },
            )
        }

        val person = root.obj("person")?.let { o ->
            PersonGuess(
                name = o.str("name")?.trim()?.ifEmpty { null }?.take(80),
                on = o.date("when"),
                place = o.str("where")?.trim()?.ifEmpty { null }?.take(80),
                memory = o.str("memory")?.trim()?.ifEmpty { null }?.take(300),
            )
        }

        return CaptureResult(kind, title, description, food, garment, receipt, person)
    }

    private val PERIODS = setOf("weekly", "monthly", "quarterly", "yearly")

    private fun JsonElement.asObjOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.obj(k: String): JsonObject? = get(k)?.asObjOrNull()
    private fun JsonObject.arr(k: String): List<JsonElement> =
        get(k)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

    private fun JsonObject.str(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.num(k: String): Double? =
        get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonObject.date(k: String): LocalDate? =
        str(k)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    private fun JsonObject.strList(k: String): List<String> =
        arr(k).mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null } }
}
