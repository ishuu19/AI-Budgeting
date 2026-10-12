package com.ledgerai.app.data.clips

import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.memory.MemoryKind
import com.ledgerai.app.domain.memory.MemoryStatus
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Filing a clip. Risk is fixed here. Money actions are high and create a record only after
 * an explicit confirm. The clip stays, and the new record is linked from it.
 */
@Singleton
class ClipActions @Inject constructor(
    private val clips: ClipDao,
    private val notes: NoteRepository,
    private val calendar: CalendarRepository,
    private val jobs: JobRepository,
    private val inventory: InventoryRepository,
    private val people: PeopleRepository,
    private val memories: MemoryRepository,
    private val transactions: TransactionRepository,
    private val bills: BillRepository,
    private val debts: DebtRepository,
) {
    /** Removes the record this clip was filed as. The caller deletes the clip itself. */
    suspend fun deleteFiled(row: ClipEntity) {
        val id = row.filedId ?: return
        when (row.filedType) {
            "task", "calendar_event" -> id.toLongOrNull()?.let { calendar.deleteById(it) }
            "note" -> id.toLongOrNull()?.let { notes.delete(NoteItem(id = it, title = "")) }
            "job" -> id.toLongOrNull()?.let { jobs.delete(it) }
            "item" -> id.toLongOrNull()?.let { inventory.softDeleteLine(it) }
            "memory" -> memories.softDelete(row.userId.orEmpty(), id)
            "bill" -> id.toLongOrNull()?.let { bills.delete(Bill(id = it, name = "", amount = 0.0, nextDueDate = LocalDate.now())) }
            "transaction" -> id.toLongOrNull()?.let {
                transactions.delete(Transaction(id = it, amount = 0.0, type = TransactionType.EXPENSE, category = TransactionCategory.OTHER))
            }
            "debt" -> id.toLongOrNull()?.let {
                debts.delete(Debt(id = it, friendName = "", amount = 0.0, direction = DebtDirection.I_OWE))
            }
        }
    }

    fun specs(): List<ActionSpec> = listOf(
        note(), shopping(), task(), event(), job(), memory(), place(), bill(), transaction(), debt(),
    )

    private fun Map<String, Any?>.str(k: String): String? = (this[k] as? String)?.trim()?.ifEmpty { null }
    private fun Map<String, Any?>.num(k: String): Double? = (this[k] as? Number)?.toDouble()
        ?: (this[k] as? String)?.toDoubleOrNull()

    private fun Map<String, Any?>.confirmed(): Boolean = str("confirmed") == "true"

    private fun needs(args: Map<String, Any?>, vararg keys: String): String? =
        keys.firstOrNull { args.str(it) == null }?.let { "Missing $it" }

    private suspend fun clip(args: Map<String, Any?>): ClipEntity {
        val id = args.str("clipId") ?: error("Missing clipId")
        return clips.get(id) ?: error("Clip is gone")
    }

    private suspend fun mark(row: ClipEntity, type: String, filedId: String, confirmed: Boolean) {
        val now = System.currentTimeMillis()
        val json = row.aiJson?.let { raw ->
            if (confirmed) raw.replace("\"inferred\":true", "\"inferred\":false") else raw
        }
        clips.upsert(
            row.copy(
                status = "filed",
                filedType = type,
                filedId = filedId,
                aiState = "done",
                aiJson = json,
                updatedAt = now,
            )
        )
    }

    private suspend fun unmark(clipId: String) {
        val row = clips.get(clipId) ?: return
        val status = if (row.noteId != null) "in_note" else "inbox"
        clips.upsert(
            row.copy(status = status, filedType = null, filedId = null, updatedAt = System.currentTimeMillis())
        )
    }

    private fun whenAt(args: Map<String, Any?>): LocalDateTime {
        val date = args.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val time = args.str("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return if (time != null) date.atTime(time) else date.atStartOfDay()
    }

    private fun note() = ActionSpec(
        type = "clip.file_note",
        risk = Risk.LOW,
        description = "Keep a clip as a note, or add tags to the note it is already in.",
        argsHint = "clipId,title,tags",
        validate = { needs(it, "clipId") },
        apply = { a ->
            val row = clip(a)
            val tags = (a["tags"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val id = row.noteId ?: notes.insert(
                NoteItem(title = a.str("title") ?: row.text.lineSequence().first().take(60), body = row.text, tags = tags)
            )
            if (row.noteId != null && tags.isNotEmpty()) {
                // Tags only. The note body is already the clip, and editing it does not rewrite the clip.
            }
            mark(row.copy(noteId = id), "note", id.toString(), a.confirmed())
            ActionOutcome("Kept the clip in a note") { unmark(row.id) }
        },
    )

    private fun shopping() = ActionSpec(
        type = "clip.file_shopping",
        risk = Risk.LOW,
        description = "Add the clip's item to the shopping list.",
        argsHint = "clipId,userId,item",
        validate = { needs(it, "clipId", "item") },
        apply = { a ->
            val row = clip(a)
            val item = a.str("item")!!
            require(row.text.contains(item, ignoreCase = true)) { "That item is not in the clip" }
            val listId = inventory.ensureList("Groceries", userId = a.str("userId"))
            val lineId = inventory.addShoppingLine(listId, item, qty = null, addedBy = a.str("userId"))
            check(lineId != 0L) { "Shopping item was not saved" }
            mark(row, "item", lineId.toString(), a.confirmed())
            ActionOutcome("Added $item to shopping") {
                inventory.softDeleteLine(lineId)
                unmark(row.id)
            }
        },
    )

    private fun task() = ActionSpec(
        type = "clip.file_task",
        risk = Risk.MEDIUM,
        description = "Create a task from a clip. Needs one tap.",
        argsHint = "clipId,title,date,time",
        validate = { needs(it, "clipId", "title") },
        apply = { a ->
            val row = clip(a)
            val start = whenAt(a)
            val dated = a.str("date") != null
            val id = calendar.upsert(
                CalendarEvent(
                    title = a.str("title")!!,
                    notes = row.text,
                    startAt = start,
                    endAt = start,
                    hasDate = dated,
                    kind = CalendarEventKind.TASK,
                )
            )
            check(id != 0L) { "Task was not saved" }
            mark(row, "task", id.toString(), a.confirmed())
            ActionOutcome("Added task ${a.str("title")}") {
                calendar.deleteById(id)
                unmark(row.id)
            }
        },
    )

    private fun event() = ActionSpec(
        type = "clip.file_event",
        risk = Risk.MEDIUM,
        description = "Create a calendar event from a clip. Needs one tap.",
        argsHint = "clipId,title,date,time,place",
        validate = { needs(it, "clipId", "title") },
        apply = { a ->
            val row = clip(a)
            val start = whenAt(a)
            val end = if (a.str("time") != null) start.plusHours(1) else start.plusDays(1)
            val id = calendar.upsert(
                CalendarEvent(
                    title = a.str("title")!!,
                    notes = row.text,
                    location = a.str("place").orEmpty(),
                    startAt = start,
                    endAt = end,
                    allDay = a.str("time") == null,
                    kind = CalendarEventKind.EVENT,
                )
            )
            check(id != 0L) { "Event was not saved" }
            mark(row, "calendar_event", id.toString(), a.confirmed())
            ActionOutcome("Added event ${a.str("title")}") {
                calendar.deleteById(id)
                unmark(row.id)
            }
        },
    )

    private fun job() = ActionSpec(
        type = "clip.file_job",
        risk = Risk.MEDIUM,
        description = "Track a job application from a clip. Needs one tap.",
        argsHint = "clipId,company,role",
        validate = { needs(it, "clipId") },
        apply = { a ->
            val row = clip(a)
            val company = a.str("company").orEmpty()
            val role = a.str("role") ?: a.str("title").orEmpty()
            require(company.isNotBlank() || role.isNotBlank()) { "Missing company" }
            val id = jobs.save(
                JobApplication(
                    company = company,
                    title = role.ifBlank { company },
                    url = row.url.orEmpty(),
                    notes = row.text,
                    appliedOn = a.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                    status = JobApplicationStatus.APPLIED,
                )
            )
            check(id != 0L) { "Job was not saved" }
            mark(row, "job", id.toString(), a.confirmed())
            ActionOutcome("Tracking ${role.ifBlank { company }}") {
                jobs.delete(id)
                unmark(row.id)
            }
        },
    )

    private fun memory() = ActionSpec(
        type = "clip.file_memory",
        risk = Risk.MEDIUM,
        description = "Save a memory about a person named in the clip. Needs one tap.",
        argsHint = "clipId,userId,person,title",
        validate = { needs(it, "clipId", "userId", "person") },
        apply = { a ->
            val row = clip(a)
            val name = a.str("person")!!
            require(row.text.contains(name, ignoreCase = true)) { "That name is not in the clip" }
            val userId = a.str("userId")!!
            val person = people.listPeople(userId).firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: people.addPerson(userId, name, org = null, role = null, notes = "")
            val status = if (a.confirmed()) MemoryStatus.CONFIRMED else MemoryStatus.INFERRED
            val saved = memories.addMemory(
                userId, person.id, a.str("title") ?: row.text.take(280),
                MemoryKind.FACT, status, "clip", row.id,
            ).getOrThrow()
            mark(row, "memory", saved.id, a.confirmed())
            ActionOutcome("Remembered this about $name") {
                memories.softDelete(userId, saved.id)
                unmark(row.id)
            }
        },
    )

    private fun place() = ActionSpec(
        type = "clip.file_place",
        risk = Risk.LOW,
        description = "Save a place from a clip onto a note, with a map link.",
        argsHint = "clipId,place,title",
        validate = { needs(it, "clipId", "place") },
        apply = { a ->
            val row = clip(a)
            val place = a.str("place")!!
            require(row.text.contains(place, ignoreCase = true)) { "That place is not in the clip" }
            val link = "https://maps.google.com/?q=" + URLEncoder.encode(place, "UTF-8")
            val noteId = row.noteId ?: notes.insert(
                NoteItem(title = a.str("title") ?: place.take(60), body = row.text + "\n\n" + link)
            )
            if (row.noteId != null) {
                val existing = notes.observeNotes().first().firstOrNull { it.id == noteId }
                if (existing != null && !existing.body.contains(link)) {
                    notes.update(existing.copy(body = existing.body.trimEnd() + "\n\n" + link))
                }
            }
            mark(row.copy(noteId = noteId), "note", noteId.toString(), a.confirmed())
            ActionOutcome("Saved $place") { unmark(row.id) }
        },
    )

    private fun bill() = ActionSpec(
        type = "clip.file_bill",
        risk = Risk.HIGH,
        description = "Draft a bill from a clip. Never runs on its own.",
        argsHint = "clipId,title,amount,currency,date",
        validate = { needs(it, "clipId", "title") ?: if (it.num("amount") == null) "Missing amount" else null },
        apply = { a ->
            val row = clip(a)
            val amount = a.num("amount")!!
            val id = bills.insert(
                Bill(
                    name = a.str("title")!!,
                    amount = amount,
                    nextDueDate = a.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                    note = row.text,
                    currency = a.str("currency") ?: "USD",
                    frequency = BillFrequency.MONTHLY,
                )
            )
            check(id != 0L) { "Bill was not saved" }
            mark(row, "bill", id.toString(), true)
            ActionOutcome("Saved bill ${a.str("title")}") {
                bills.delete(Bill(id = id, name = "", amount = 0.0, nextDueDate = LocalDate.now()))
                unmark(row.id)
            }
        },
    )

    private fun transaction() = ActionSpec(
        type = "clip.file_transaction",
        risk = Risk.HIGH,
        description = "Draft a transaction from a clip. Never runs on its own.",
        argsHint = "clipId,title,amount,currency,date",
        validate = { needs(it, "clipId") ?: if (it.num("amount") == null) "Missing amount" else null },
        apply = { a ->
            val row = clip(a)
            val amount = a.num("amount")!!
            val title = a.str("title").orEmpty()
            val id = transactions.insert(
                Transaction(
                    amount = amount,
                    type = TransactionType.EXPENSE,
                    category = TransactionCategory.OTHER,
                    merchant = title.takeIf { row.text.contains(it, ignoreCase = true) }.orEmpty(),
                    note = row.text,
                    date = a.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                    currency = a.str("currency") ?: "USD",
                    userId = a.str("userId"),
                )
            )
            check(id != 0L) { "Transaction was not saved" }
            mark(row, "transaction", id.toString(), true)
            ActionOutcome("Saved a transaction draft") {
                transactions.delete(
                    Transaction(id = id, amount = 0.0, type = TransactionType.EXPENSE, category = TransactionCategory.OTHER)
                )
                unmark(row.id)
            }
        },
    )

    private fun debt() = ActionSpec(
        type = "clip.file_debt",
        risk = Risk.HIGH,
        description = "Draft a debt from a clip. Never runs on its own.",
        argsHint = "clipId,person,amount,currency",
        validate = { needs(it, "clipId", "person") ?: if (it.num("amount") == null) "Missing amount" else null },
        apply = { a ->
            val row = clip(a)
            val name = a.str("person")!!
            require(row.text.contains(name, ignoreCase = true)) { "That name is not in the clip" }
            val owe = Regex("""(?i)\bi owe\b""").containsMatchIn(row.text)
            val id = debts.insert(
                Debt(
                    friendName = name,
                    amount = a.num("amount")!!,
                    direction = if (owe) DebtDirection.I_OWE else DebtDirection.THEY_OWE,
                    note = row.text,
                    currency = a.str("currency") ?: "USD",
                    userId = a.str("userId"),
                )
            )
            check(id != 0L) { "Debt was not saved" }
            mark(row, "debt", id.toString(), true)
            ActionOutcome("Saved a debt draft") {
                debts.delete(Debt(id = id, friendName = "", amount = 0.0, direction = DebtDirection.I_OWE))
                unmark(row.id)
            }
        },
    )
}
