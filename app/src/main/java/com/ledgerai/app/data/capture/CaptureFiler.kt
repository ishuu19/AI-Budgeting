package com.ledgerai.app.data.capture

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ledgerai.app.data.assistant.ActionResult
import com.ledgerai.app.data.assistant.ActionStatus
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaKind
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.capture.CaptureKind
import com.ledgerai.app.domain.capture.CaptureResult
import com.ledgerai.app.domain.capture.PersonGuess
import com.ledgerai.app.domain.subscriptions.SubscriptionCharges
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class SuggestItem(val name: String, val qty: Double? = null)

/**
 * A one-tap suggestion kept on the photo until the user accepts or dismisses it.
 * Types: [CaptureFiler.SUGGEST_STOCK], [CaptureFiler.SUGGEST_EXPENSE], [CaptureFiler.SUGGEST_SUBSCRIPTION],
 * [CaptureFiler.SUGGEST_NAME].
 */
data class Suggestion(
    val type: String,
    val merchant: String? = null,
    val amount: Double? = null,
    val period: String? = null,
    val renewsOn: String? = null,
    val items: List<SuggestItem> = emptyList(),
    val category: String? = null,
    val date: String? = null,
)

/**
 * Turns what the AI saw into a typed action plan and hands it to the [Executor], the only place
 * that writes (rule 2). Risk comes from the registry (rule 3): private reversible records are
 * LOW and apply now; anything that touches stock counts from a receipt, money or a recurring charge is
 * MEDIUM and runs only after the user's tap. The plan's input ref is the photo id, so a retried worker
 * never files the same photo twice. Every record keeps the photo id as its source (rule 5); AI values
 * are marked inferred (rule 4).
 */
@Singleton
class CaptureFiler @Inject constructor(
    private val executor: Executor,
    private val media: MediaRepository,
) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<Suggestion>>() {}.type

    /** Returns the asset updated with what was filed. The caller saves it. */
    suspend fun file(row: MediaAssetEntity, result: CaptureResult, attempt: String = ""): MediaAssetEntity {
        val base = row.copy(
            kind = result.kind.toStored(),
            title = result.title.ifBlank { row.title },
            description = result.description,
        )
        return when (result.kind) {
            CaptureKind.CLOTHING -> fileClothing(base, result, attempt)
            CaptureKind.FOOD -> fileFood(base, result, attempt)
            CaptureKind.RECEIPT -> fileReceipt(base, result, attempt)
            CaptureKind.PERSON -> filePerson(base, result.person, attempt = attempt)
            CaptureKind.OTHER -> base.copy(analysisState = AnalysisState.DONE)
        }
    }

    private suspend fun run(row: MediaAssetEntity, type: String, args: Map<String, Any?>, approved: Set<Int> = emptySet(), ref: String = "capture:${row.id}"): ActionResult =
        executor.execute(
            ActionPlan(inputRef = ref, actions = listOf(ProposedAction(type, args + mapOf("assetId" to row.id, "userId" to row.userId)))),
            approved,
        ).first()

    /** The action linked the record on the stored row; keep that link and the new fields. */
    private suspend fun merged(row: MediaAssetEntity, ok: Boolean, failure: MediaAssetEntity = row): MediaAssetEntity {
        if (!ok) return failure
        val stored = media.get(row.id)
        return row.copy(linkedType = stored?.linkedType ?: row.linkedType, linkedId = stored?.linkedId ?: row.linkedId)
    }

    private suspend fun fileClothing(row: MediaAssetEntity, r: CaptureResult, attempt: String): MediaAssetEntity {
        val g = r.garment ?: return row.needsInput("Name this garment")
        val res = run(row, "capture.file_garment", mapOf(
            "name" to g.name, "type" to g.type, "colors" to g.colors, "seasons" to g.seasons,
        ), ref = "capture:${row.id}$attempt")
        val ok = res.status == ActionStatus.APPLIED
        return merged(row.copy(title = g.name, analysisState = AnalysisState.DONE), ok, row.needsInput("Name this garment"))
    }

    private suspend fun fileFood(row: MediaAssetEntity, r: CaptureResult, attempt: String): MediaAssetEntity {
        if (r.food.isEmpty()) return row.needsInput("What food is this?")
        val items = r.food.map {
            mapOf("name" to it.name, "quantity" to it.quantity, "unit" to it.unit, "location" to it.location, "expiresOn" to it.expiresOn?.toString())
        }
        val res = run(row, "capture.file_food", mapOf("items" to items), ref = "capture:${row.id}$attempt")
        return merged(row.copy(analysisState = AnalysisState.DONE), res.status == ActionStatus.APPLIED, row.needsInput("Couldn't add this food"))
    }

    private suspend fun fileReceipt(row: MediaAssetEntity, r: CaptureResult, attempt: String): MediaAssetEntity {
        val g = r.receipt ?: return row.needsInput("Couldn't read this receipt")
        val lines = g.lines.map { mapOf("text" to it.text, "qty" to it.qty, "price" to it.price) }
        val res = run(row, "capture.file_receipt", mapOf(
            "merchant" to g.merchant, "total" to g.total, "currency" to g.currency, "date" to g.date?.toString(), "lines" to lines,
        ), ref = "capture:${row.id}$attempt")
        if (res.status != ActionStatus.APPLIED) return row.needsInput("Couldn't save this receipt")
        val done = merged(row.copy(analysisState = AnalysisState.DONE), true)

        // One tap each: what to add to the pantry, what to log as spending, and any subscription.
        val suggestions = mutableListOf<Suggestion>()
        val foods = g.lines.filter { it.food }.mapNotNull { l ->
            (l.name ?: l.text).trim().takeIf { it.isNotEmpty() }?.let { SuggestItem(it.take(60), l.qty) }
        }.distinctBy { it.name.lowercase() }
        if (foods.isNotEmpty()) suggestions += Suggestion(SUGGEST_STOCK, merchant = g.merchant, items = foods)
        if (g.total != null && g.total > 0.0) {
            val mostlyFood = g.lines.isNotEmpty() && g.lines.count { it.food } * 2 >= g.lines.size
            suggestions += Suggestion(
                SUGGEST_EXPENSE, merchant = g.merchant, amount = g.total,
                category = if (mostlyFood) "FOOD" else "SHOPPING",
                date = (g.date ?: dayOf(row.createdAt)).toString(),
            )
        }
        val sub = g.subscription
        val merchant = g.merchant
        if (sub != null && merchant != null) {
            val anchor = g.date ?: dayOf(row.createdAt)
            val renews = sub.nextRenewalOn ?: SubscriptionCharges.nextChargeDate(
                anchor, SubscriptionPeriod.fromWire(sub.period), LocalDate.now(),
            )
            suggestions += Suggestion(SUGGEST_SUBSCRIPTION, merchant, g.total, sub.period, renews.toString())
        }
        return if (suggestions.isEmpty()) done
        else done.copy(payload = gson.toJson(suggestions), analysisState = AnalysisState.NEEDS_INPUT)
    }

    /**
     * Files a photo under a person on a date. Needs the name from the user's own words.
     * With no name the asset waits for one instead of guessing.
     */
    suspend fun filePerson(row: MediaAssetEntity, guess: PersonGuess?, nameOverride: String? = null, attempt: String = ""): MediaAssetEntity {
        val name = (nameOverride ?: guess?.name)?.trim().orEmpty()
        if (name.isEmpty()) {
            return row.copy(
                kind = MediaKind.PERSON,
                payload = gson.toJson(listOf(Suggestion(type = SUGGEST_NAME))),
                analysisState = AnalysisState.NEEDS_INPUT,
            )
        }
        val day = guess?.on ?: dayOf(row.createdAt)
        val res = run(row, "capture.file_person", mapOf(
            "name" to name,
            "date" to day.toString(),
            "place" to guess?.place,
            "summary" to (guess?.memory ?: row.note.ifBlank { "Photo together" }),
            "memory" to guess?.memory,
        ), ref = "capture:${row.id}$attempt")
        if (res.status != ActionStatus.APPLIED) return row.needsInput("Couldn't file this photo")
        return merged(
            row.copy(kind = MediaKind.PERSON, title = row.title.ifBlank { name }, payload = null, analysisState = AnalysisState.DONE),
            true,
        )
    }

    /** The user tapped a suggestion: that tap is the approval the MEDIUM action needs. */
    suspend fun accept(row: MediaAssetEntity, type: String): MediaAssetEntity {
        val s = suggestionsOf(row).firstOrNull { it.type == type } ?: return row
        val ref = "capture-$type:${row.id}"
        val res = when (type) {
            SUGGEST_STOCK -> run(
                row, "capture.stock_from_receipt",
                mapOf("items" to s.items.map { mapOf("name" to it.name, "qty" to it.qty) }),
                approved = setOf(0), ref = ref,
            )
            SUGGEST_EXPENSE -> run(
                row, "capture.log_expense",
                mapOf("merchant" to s.merchant, "amount" to s.amount, "date" to s.date, "category" to s.category),
                approved = setOf(0), ref = ref,
            )
            SUGGEST_SUBSCRIPTION -> run(
                row, "capture.track_subscription",
                mapOf("merchant" to s.merchant, "amount" to s.amount, "period" to s.period, "renewsOn" to s.renewsOn),
                approved = setOf(0), ref = ref,
            )
            else -> return dismiss(row, type)
        }
        // Keep the suggestion if it did not apply, so the user can try again.
        return if (res.status == ActionStatus.APPLIED) dismiss(row, type) else row
    }

    /** Remove one suggestion. When none are left the photo is done. */
    fun dismiss(row: MediaAssetEntity, type: String): MediaAssetEntity {
        val left = suggestionsOf(row).filter { it.type != type }
        return if (left.isEmpty()) row.copy(payload = null, analysisState = AnalysisState.DONE)
        else row.copy(payload = gson.toJson(left))
    }

    /** Accepts either the current list or the single object older photos stored. */
    fun suggestionsOf(row: MediaAssetEntity): List<Suggestion> {
        val raw = row.payload ?: return emptyList()
        return runCatching<List<Suggestion>> {
            if (raw.trimStart().startsWith("[")) gson.fromJson(raw, listType) else listOf(gson.fromJson(raw, Suggestion::class.java))
        }.getOrDefault(emptyList())
    }

    private fun MediaAssetEntity.needsInput(why: String) =
        copy(description = description.ifBlank { why }, analysisState = AnalysisState.NEEDS_INPUT)

    private fun dayOf(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun CaptureKind.toStored() = when (this) {
        CaptureKind.RECEIPT -> MediaKind.RECEIPT
        CaptureKind.FOOD -> MediaKind.FOOD
        CaptureKind.CLOTHING -> MediaKind.CLOTHING
        CaptureKind.PERSON -> MediaKind.PERSON
        CaptureKind.OTHER -> MediaKind.OTHER
    }

    companion object {
        const val LINK_GARMENT = "wardrobe_item"
        const val LINK_STOCK = "stock_item"
        const val LINK_RECEIPT = "receipt"
        const val LINK_PERSON = "person"
        const val SUGGEST_SUBSCRIPTION = "subscription"
        const val SUGGEST_STOCK = "stock"
        const val SUGGEST_EXPENSE = "expense"
        const val SUGGEST_NAME = "person_name"
    }
}
