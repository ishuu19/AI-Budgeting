package com.ledgerai.app.data.capture

import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.people.InteractionRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.receipts.ReceiptRepository
import com.ledgerai.app.data.subscriptions.SubscriptionRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ReceiptLine
import com.ledgerai.app.domain.receipts.ValueConfidence
import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The capture actions the executor may run. Args are plain strings, numbers and lists so the audit
 * trail can store them. Each action that creates a record also links it back to the photo it came from.
 * Risk is set here, never by the model: private reversible records are LOW; a recurring charge is MEDIUM.
 */
@Singleton
class CaptureActions @Inject constructor(
    private val media: MediaRepository,
    private val wardrobe: WardrobeRepository,
    private val inventory: InventoryRepository,
    private val receipts: ReceiptRepository,
    private val people: PeopleRepository,
    private val memories: MemoryRepository,
    private val interactions: InteractionRepository,
    private val subscriptions: SubscriptionRepository,
    private val transactions: com.ledgerai.app.data.repository.TransactionRepository,
) {
    fun specs(): List<ActionSpec> = listOf(garment(), food(), receipt(), person(), subscription(), stockFromReceipt(), logExpense())

    private suspend fun link(assetId: String, type: String, id: String?) {
        val row = media.get(assetId) ?: return
        media.save(row.copy(linkedType = type, linkedId = id))
    }

    private fun Map<String, Any?>.str(k: String): String? = (this[k] as? String)?.trim()?.ifEmpty { null }
    private fun Map<String, Any?>.num(k: String): Double? = (this[k] as? Number)?.toDouble()
    private fun Map<String, Any?>.date(k: String): LocalDate? = str(k)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.strings(k: String): List<String> = (this[k] as? List<String>).orEmpty()

    private fun needs(args: Map<String, Any?>, vararg keys: String): String? =
        keys.firstOrNull { args.str(it) == null }?.let { "Missing $it" }

    private fun garment() = ActionSpec(
        type = "capture.file_garment",
        risk = Risk.LOW,
        description = "Add a photographed garment to the wardrobe.",
        argsHint = "assetId,userId,name,type,colors[],seasons[]",
        validate = { needs(it, "assetId", "userId", "name") },
        apply = { a ->
            val id = wardrobe.addGarment(
                userId = a.str("userId")!!, name = a.str("name")!!, type = a.str("type").orEmpty(),
                colors = a.strings("colors"), seasons = a.strings("seasons"), photoPath = a.str("assetId"),
            )
            check(id != 0L) { "Garment was not saved" }
            link(a.str("assetId")!!, CaptureFiler.LINK_GARMENT, id.toString())
            ActionOutcome("Added ${a.str("name")} to your wardrobe") {
                wardrobe.softDeleteGarment(a.str("userId")!!, id)
            }
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun food() = ActionSpec(
        type = "capture.file_food",
        risk = Risk.LOW,
        description = "Add foods seen in a photo to the pantry as inferred stock.",
        argsHint = "assetId,userId,items[{name,quantity,unit,location,expiresOn}]",
        validate = { needs(it, "assetId", "userId").takeIf { e -> e != null } ?: if ((it["items"] as? List<*>).isNullOrEmpty()) "No items" else null },
        apply = { a ->
            val existing = inventory.listItems().map { it.name.lowercase() }.toSet()
            var first: Long? = null
            var added = 0
            for (item in (a["items"] as List<Map<String, Any?>>)) {
                val name = item.str("name") ?: continue
                // A known item keeps the count the user already has; a photo never overwrites it.
                if (name.lowercase() in existing) continue
                val id = inventory.addItem(
                    name = name, quantity = item.num("quantity"), ownerUserId = a.str("userId"),
                    unit = item.str("unit").orEmpty(), location = item.str("location").orEmpty(),
                    expiresOn = item.date("expiresOn"), kind = ItemKind.FOOD, sourceType = "photo",
                    sourceId = a.str("assetId"), confidence = ItemConfidence.INFERRED,
                )
                if (first == null && id != 0L) first = id
                added++
            }
            link(a.str("assetId")!!, CaptureFiler.LINK_STOCK, first?.toString())
            ActionOutcome("Added $added item(s) to your pantry")
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun receipt() = ActionSpec(
        type = "capture.file_receipt",
        risk = Risk.LOW,
        description = "Save a photographed receipt as an unconfirmed draft. Creates no transaction.",
        argsHint = "assetId,userId,merchant,total,currency,date,lines[{text,qty,price}]",
        validate = { needs(it, "assetId", "userId") },
        apply = { a ->
            val saved = receipts.save(
                Receipt(
                    userId = a.str("userId"),
                    merchant = a.str("merchant"),
                    merchantConfidence = a.str("merchant")?.let { ValueConfidence.INFERRED },
                    purchasedOn = a.date("date"),
                    purchasedOnConfidence = a.date("date")?.let { ValueConfidence.INFERRED },
                    total = a.num("total"),
                    totalConfidence = a.num("total")?.let { ValueConfidence.INFERRED },
                    currency = a.str("currency"),
                    documentId = a.str("assetId"),
                    lines = (a["lines"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { l ->
                        l.str("text")?.let { ReceiptLine(rawText = it, qty = l.num("qty"), unitPrice = l.num("price"), confidence = ValueConfidence.INFERRED) }
                    },
                )
            )
            link(a.str("assetId")!!, CaptureFiler.LINK_RECEIPT, saved.id.toString())
            ActionOutcome("Saved a receipt draft to review")
        },
    )

    private fun person() = ActionSpec(
        type = "capture.file_person",
        risk = Risk.LOW,
        description = "File a photo under a named person on a date, with an optional memory.",
        argsHint = "assetId,userId,name,date,place,summary,memory",
        validate = { needs(it, "assetId", "userId", "name", "date", "summary") },
        apply = { a ->
            val userId = a.str("userId")!!
            val name = a.str("name")!!
            val person = people.listPeople(userId).firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: people.addPerson(userId, name, org = null, role = null, notes = "")
            interactions.addInteraction(userId, person.id, a.date("date")!!, a.str("place"), a.str("summary")!!, a.str("assetId"))
            a.str("memory")?.let {
                memories.addMemory(userId, person.id, it, MemoryKind.FACT, MemoryStatus.INFERRED, "photo", a.str("assetId"))
            }
            link(a.str("assetId")!!, CaptureFiler.LINK_PERSON, person.id)
            ActionOutcome("Filed the photo under $name")
        },
    )

    @Suppress("UNCHECKED_CAST")
    private fun stockFromReceipt() = ActionSpec(
        type = "capture.stock_from_receipt",
        risk = Risk.MEDIUM,
        description = "Add the food items on a receipt to the pantry. Needs the user's approval.",
        argsHint = "assetId,userId,items[{name,qty}]",
        validate = { needs(it, "assetId", "userId") ?: if ((it["items"] as? List<*>).isNullOrEmpty()) "No items" else null },
        apply = { a ->
            val existing = inventory.listItems().map { it.name.lowercase() }.toSet()
            var added = 0
            for (item in (a["items"] as List<Map<String, Any?>>)) {
                val name = item.str("name") ?: continue
                if (name.lowercase() in existing) continue
                // Quantity stays unknown unless the receipt gave one; a receipt never overwrites a count.
                inventory.addItem(
                    name = name, quantity = item.num("qty"), ownerUserId = a.str("userId"), kind = ItemKind.FOOD,
                    sourceType = "receipt", sourceId = a.str("assetId"), confidence = ItemConfidence.INFERRED,
                )
                added++
            }
            ActionOutcome("Added $added item(s) from the receipt to your pantry")
        },
    )

    private fun logExpense() = ActionSpec(
        type = "capture.log_expense",
        risk = Risk.MEDIUM,
        description = "Log the receipt total as an expense. Needs the user's approval.",
        argsHint = "assetId,userId,merchant,amount,date,category",
        validate = { needs(it, "assetId", "userId") ?: if ((it.num("amount") ?: 0.0) <= 0.0) "Missing amount" else null },
        apply = { a ->
            val category = a.str("category")?.let { runCatching { com.ledgerai.app.domain.model.TransactionCategory.valueOf(it) }.getOrNull() }
                ?: com.ledgerai.app.domain.model.TransactionCategory.OTHER
            val tx = com.ledgerai.app.domain.model.Transaction(
                amount = a.num("amount")!!,
                type = com.ledgerai.app.domain.model.TransactionType.EXPENSE,
                category = category,
                merchant = a.str("merchant").orEmpty(),
                note = "From a receipt photo",
                date = a.date("date") ?: LocalDate.now(),
            )
            val id = transactions.insert(tx)
            ActionOutcome("Logged the expense") { transactions.delete(tx.copy(id = id)) }
        },
    )

    private fun subscription() = ActionSpec(
        type = "capture.track_subscription",
        risk = Risk.MEDIUM,
        description = "Start tracking a recurring charge. Needs the user's approval.",
        argsHint = "assetId,userId,merchant,amount,period,renewsOn",
        validate = { needs(it, "assetId", "userId", "merchant", "period", "renewsOn") },
        apply = { a ->
            subscriptions.add(
                a.str("userId"),
                NewSubscription(
                    merchant = a.str("merchant")!!,
                    amount = a.num("amount")?.let { SubscriptionAmount.Known(it) } ?: SubscriptionAmount.Unknown,
                    period = SubscriptionPeriod.fromWire(a.str("period")!!),
                    nextRenewalOn = LocalDate.parse(a.str("renewsOn")!!),
                    sourceId = a.str("assetId"),
                ),
            )
            ActionOutcome("Tracking ${a.str("merchant")}")
        },
    )
}
