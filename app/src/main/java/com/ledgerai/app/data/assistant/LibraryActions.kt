package com.ledgerai.app.data.assistant

import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.achievements.AchievementEntity
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayEntity
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.dates.TripEntity
import com.ledgerai.app.data.dates.TripRepository
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishEntity
import com.ledgerai.app.data.wishes.WishStatus
import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.assistant.argBool
import com.ledgerai.app.domain.assistant.argDate
import com.ledgerai.app.domain.assistant.argInt
import com.ledgerai.app.domain.assistant.argLong
import com.ledgerai.app.domain.assistant.argNum
import com.ledgerai.app.domain.assistant.argStr
import com.ledgerai.app.domain.assistant.argStrings
import com.ledgerai.app.domain.assistant.argTime
import com.ledgerai.app.domain.assistant.has
import com.ledgerai.app.domain.dates.BirthdayLadder
import com.ledgerai.app.domain.dates.LadderSwitches
import com.ledgerai.app.domain.dates.Trips
import com.ledgerai.app.domain.inventory.ItemConfidence
import com.ledgerai.app.domain.inventory.ItemKind
import com.ledgerai.app.domain.inventory.StockItem
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.service.ReminderEngine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Add, edit and delete for the Library and the records around it. Used by the section AI bars and
 * the Home chat. Risk is fixed here, never by the model: adding a private record is LOW; editing or
 * removing one is MEDIUM; anything that touches money is HIGH and always waits for a tap.
 * The signed-in user is applied by the executor path, not taken from the model.
 *
 * Not here on purpose: passwords and locked Vault folders (never typed to an AI), app time, and
 * adding expenses, bills or debts (the voice pipeline already confirms those with its own cards).
 */
@Singleton
class LibraryActions @Inject constructor(
    private val session: UserSession,
    private val people: PeopleRepository,
    private val jobs: JobRepository,
    private val inventory: InventoryRepository,
    private val wardrobe: WardrobeRepository,
    private val birthdays: BirthdayRepository,
    private val birthdayDao: BirthdayDao,
    private val trips: TripRepository,
    private val tripDao: TripDao,
    private val achievements: AchievementDao,
    private val wishes: WishDao,
    private val notes: NoteRepository,
    private val calendar: CalendarRepository,
    private val transactions: TransactionRepository,
    private val bills: BillRepository,
    private val debts: DebtRepository,
    private val engine: ReminderEngine,
) {
    fun specs(): List<ActionSpec> = listOf(
        personAdd(), personEdit(), personDelete(),
        jobAdd(), jobEdit(), jobDelete(),
        pantryAdd(), pantryEdit(), pantryDelete(),
        shoppingAdd(), shoppingEdit(), shoppingCheck(), shoppingDelete(),
        garmentAdd(), garmentEdit(), garmentDelete(),
        birthdayAdd(), birthdayEdit(), birthdayDelete(),
        tripAdd(), tripEdit(), tripDelete(),
        achievementAdd(), achievementEdit(), achievementDelete(),
        wishAdd(), wishEdit(), wishDelete(),
        noteAdd(), noteEdit(), noteAppend(), noteDelete(),
        taskAdd(), eventAdd(), taskEdit(), taskComplete(), taskDelete(),
        transactionEdit(), transactionDelete(),
        billEdit(), billDelete(),
        debtEdit(), debtPaid(), debtDelete(),
    )

    private companion object {
        /** The server accepts only yyyy-MM here. */
        val WANTED_BY = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
    }

    // --- helpers ------------------------------------------------------------------------------

    private suspend fun userOrNull(): String? = session.userInfo.first().userId.ifBlank { null }
    private suspend fun user(): String = userOrNull() ?: error("Sign in first")

    private fun spec(
        type: String,
        risk: Risk,
        description: String,
        argsHint: String,
        validate: (Map<String, Any?>) -> String? = { null },
        preview: suspend (Map<String, Any?>) -> String? = { null },
        apply: suspend (Map<String, Any?>) -> ActionOutcome,
    ) = ActionSpec(type, risk, description, argsHint, validate, apply, preview)

    private fun need(a: Map<String, Any?>, vararg keys: String): String? =
        keys.firstOrNull { a.argStr(it) == null }?.let { "Missing $it" }

    private fun needLong(a: Map<String, Any?>): String? = if (a.argLong("id") == null) "Missing id" else null

    private fun idLong(a: Map<String, Any?>): Long = a.argLong("id") ?: error("Missing id")
    private fun idStr(a: Map<String, Any?>): String = a.argStr("id") ?: error("Missing id")

    /** "amount 5, merchant Starbucks": the fields an edit sets, for the confirm line. */
    private fun changes(a: Map<String, Any?>, vararg keys: String): String =
        keys.filter { a.has(it) }.joinToString(", ") { "$it ${a[it].toString().removeSuffix(".0")}" }.ifEmpty { "no changes" }

    private fun money(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else "%.2f".format(v)

    // --- lookups (a stale or invented id fails with a message, it never touches another record) ---

    private suspend fun person(u: String, id: String) = people.findActive(u, id) ?: error("That person is gone")
    private suspend fun job(id: Long) = jobs.getById(id) ?: error("That job is gone")
    private suspend fun item(id: Long): StockItem = inventory.listItems().firstOrNull { it.id == id } ?: error("That item is gone")
    private suspend fun line(id: Long) = inventory.listLists().flatMap { inventory.listLines(it.id) }.firstOrNull { it.id == id }
        ?: error("That shopping item is gone")
    private suspend fun garment(u: String, id: Long) = wardrobe.listGarments(u).firstOrNull { it.id == id } ?: error("That garment is gone")
    private suspend fun birthday(id: String): BirthdayEntity = birthdayDao.get(id)?.takeIf { it.deletedAt == null } ?: error("That birthday is gone")
    private suspend fun trip(id: String): TripEntity = tripDao.get(id)?.takeIf { it.deletedAt == null } ?: error("That trip is gone")
    private suspend fun achievement(id: String): AchievementEntity = achievements.get(id)?.takeIf { it.deletedAt == null } ?: error("That achievement is gone")
    private suspend fun wish(id: String): WishEntity = wishes.get(id)?.takeIf { it.deletedAt == null } ?: error("That wish is gone")
    private suspend fun note(id: Long): NoteItem = notes.observeNotes().first().firstOrNull { it.id == id } ?: error("That note is gone")
    private suspend fun event(id: Long): CalendarEvent = calendar.getById(id) ?: error("That task or event is gone")
    private suspend fun tx(id: Long): Transaction = transactions.getAllTransactions().first().firstOrNull { it.id == id } ?: error("That transaction is gone")
    private suspend fun bill(id: Long): Bill = bills.getAllBills().first().firstOrNull { it.id == id } ?: error("That bill is gone")
    private suspend fun debt(id: Long): Debt = debts.getAllDebts().first().firstOrNull { it.id == id } ?: error("That debt is gone")

    private suspend fun birthdayName(row: BirthdayEntity): String =
        birthdays.names(row.userId)[row.personId] ?: "Someone"

    private fun switches(row: BirthdayEntity) = LadderSwitches(
        month1 = row.r1mo, week2 = row.r2w, week1 = row.r1w, day3 = row.r3d, day1 = row.r1d,
        hour5 = row.r5h, min30 = row.r30m, min10 = row.r10m, atTime = row.rAt,
    )

    // --- People ---------------------------------------------------------------------------------

    private fun personAdd() = spec(
        "lib.person.add", Risk.LOW, "Add a person to People.", "name,org,role,notes",
        validate = { need(it, "name") },
        preview = { "Add ${it.argStr("name")} to People" },
    ) { a ->
        val u = user()
        val name = a.argStr("name")!!
        val existing = people.listPeople(u).firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) ActionOutcome("$name is already in People")
        else {
            val p = people.addPerson(u, name, a.argStr("org"), a.argStr("role"), a.argStr("notes").orEmpty())
            ActionOutcome("Added $name to People") { people.deletePerson(u, p.id) }
        }
    }

    private fun personEdit() = spec(
        "lib.person.edit", Risk.MEDIUM,
        "Change a person's name, org, role or notes. For notes send the full new text (old text kept, new added).",
        "id,name,org,role,notes",
        validate = { need(it, "id") },
        preview = { a -> person(user(), idStr(a)).let { "Edit ${it.name}: ${changes(a, "name", "org", "role", "notes")}" } },
    ) { a ->
        val u = user()
        val old = person(u, idStr(a))
        val saved = people.updatePerson(
            u, old.id,
            name = a.argStr("name") ?: old.name,
            org = if (a.has("org")) a.argStr("org") else old.org,
            role = if (a.has("role")) a.argStr("role") else old.role,
            notes = if (a.has("notes")) a.argStr("notes").orEmpty() else old.notes,
        )
        check(saved) { "Could not save that person" }
        ActionOutcome("Updated ${old.name}") { people.updatePerson(u, old.id, old.name, old.org, old.role, old.notes) }
    }

    private fun personDelete() = spec(
        "lib.person.delete", Risk.MEDIUM, "Remove a person from People.", "id",
        validate = { need(it, "id") },
        preview = { a -> "Delete ${person(user(), idStr(a)).name} from People" },
    ) { a ->
        val u = user()
        val p = person(u, idStr(a))
        people.deletePerson(u, p.id)
        ActionOutcome("Removed ${p.name} from People")
    }

    // --- Jobs -----------------------------------------------------------------------------------

    private fun jobStatus(raw: String?): JobApplicationStatus? =
        JobApplicationStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    private fun jobAdd() = spec(
        "lib.job.add", Risk.LOW, "Track a job application.",
        "company,title,status(APPLIED|SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN),appliedOn,followUpOn,url,contact,location,notes",
        validate = { if (it.argStr("company") == null && it.argStr("title") == null) "Missing company or title" else null },
        preview = { a -> "Track job: ${a.argStr("title") ?: ""} ${a.argStr("company")?.let { "at $it" } ?: ""}".trim() },
    ) { a ->
        val company = a.argStr("company").orEmpty()
        val title = a.argStr("title") ?: company
        val id = jobs.save(
            JobApplication(
                company = company, title = title,
                url = a.argStr("url").orEmpty(), notes = a.argStr("notes").orEmpty(),
                contact = a.argStr("contact").orEmpty(), location = a.argStr("location").orEmpty(),
                status = jobStatus(a.argStr("status")) ?: JobApplicationStatus.APPLIED,
                appliedOn = a.argDate("appliedOn") ?: LocalDate.now(),
                followUpOn = a.argDate("followUpOn"),
            )
        )
        check(id != 0L) { "Job was not saved" }
        ActionOutcome("Tracking $title") { jobs.delete(id) }
    }

    private fun jobEdit() = spec(
        "lib.job.edit", Risk.MEDIUM, "Change a tracked job: status, follow-up date, contact, location or notes.",
        "id,company,title,status,appliedOn,followUpOn,url,source,contact,location,notes,extraDates",
        validate = { needLong(it) },
        preview = { a -> job(idLong(a)).let { "Edit job ${it.title}: ${changes(a, "company", "title", "status", "appliedOn", "followUpOn", "url", "source", "contact", "location", "notes", "extraDates")}" } },
    ) { a ->
        val old = job(idLong(a))
        val next = old.copy(
            company = a.argStr("company") ?: old.company,
            title = a.argStr("title") ?: old.title,
            status = jobStatus(a.argStr("status")) ?: old.status,
            followUpOn = if (a.has("followUpOn")) a.argDate("followUpOn") else old.followUpOn,
            appliedOn = a.argDate("appliedOn") ?: old.appliedOn,
            url = if (a.has("url")) a.argStr("url").orEmpty() else old.url,
            source = if (a.has("source")) a.argStr("source").orEmpty() else old.source,
            extraDates = if (a.has("extraDates")) a.argStr("extraDates").orEmpty() else old.extraDates,
            contact = if (a.has("contact")) a.argStr("contact").orEmpty() else old.contact,
            location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
            notes = if (a.has("notes")) a.argStr("notes").orEmpty() else old.notes,
        )
        jobs.save(next)
        ActionOutcome("Updated ${old.title}") { jobs.save(old) }
    }

    private fun jobDelete() = spec(
        "lib.job.delete", Risk.MEDIUM, "Remove a tracked job.", "id",
        validate = { needLong(it) },
        preview = { a -> "Delete job ${job(idLong(a)).title}" },
    ) { a ->
        val j = job(idLong(a))
        jobs.delete(j.id)
        ActionOutcome("Removed ${j.title}")
    }

    // --- Pantry and shopping --------------------------------------------------------------------

    private fun pantryAdd() = spec(
        "lib.pantry.add", Risk.LOW,
        "Add something the user has at home. Leave quantity out when they did not say how much.",
        "name,quantity,unit,location,expiresOn,kind(FOOD|SUPPLY|CLOTHING|OTHER)",
        validate = { need(it, "name") },
        preview = { "Add ${it.argStr("name")} to the pantry" },
    ) { a ->
        val name = a.argStr("name")!!
        val existing = inventory.listItems().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) ActionOutcome("$name is already in the pantry")
        else {
            val kind = ItemKind.entries.firstOrNull { it.name.equals(a.argStr("kind"), ignoreCase = true) } ?: ItemKind.FOOD
            val id = inventory.addItem(
                name = name, quantity = a.argNum("quantity"), ownerUserId = userOrNull(),
                unit = a.argStr("unit").orEmpty(), location = a.argStr("location").orEmpty(),
                expiresOn = a.argDate("expiresOn"), kind = kind, sourceType = "chat",
                confidence = ItemConfidence.CONFIRMED,
            )
            check(id != 0L) { "Item was not saved" }
            ActionOutcome("Added $name to the pantry") { inventory.softDeleteItem(id) }
        }
    }

    private fun pantryEdit() = spec(
        "lib.pantry.edit", Risk.MEDIUM,
        "Change any field of a pantry item. quantity is the new total, not the change.",
        "id,name,category,quantity,unit,location,expiresOn,kind(FOOD|SUPPLY|CLOTHING|OTHER)",
        validate = { needLong(it) },
        preview = { a -> item(idLong(a)).let { "Edit ${it.name}: ${changes(a, "name", "category", "quantity", "unit", "location", "expiresOn", "kind")}" } },
    ) { a ->
        val old = item(idLong(a))
        suspend fun write(i: StockItem) =
            inventory.updateItem(i.id, i.name, i.category, i.quantity, i.unit, i.location, i.expiresOn, i.kind)
        write(
            old.copy(
                name = a.argStr("name") ?: old.name,
                category = if (a.has("category")) a.argStr("category").orEmpty() else old.category,
                quantity = if (a.has("quantity")) a.argNum("quantity")?.takeIf { it >= 0 } else old.quantity,
                unit = if (a.has("unit")) a.argStr("unit").orEmpty() else old.unit,
                location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
                expiresOn = if (a.has("expiresOn")) a.argDate("expiresOn") else old.expiresOn,
                kind = ItemKind.entries.firstOrNull { it.name.equals(a.argStr("kind"), true) } ?: old.kind,
            )
        )
        ActionOutcome("Updated ${old.name}") { write(old) }
    }

    private fun pantryDelete() = spec(
        "lib.pantry.delete", Risk.MEDIUM, "Remove an item from the pantry.", "id",
        validate = { needLong(it) },
        preview = { a -> "Remove ${item(idLong(a)).name} from the pantry" },
    ) { a ->
        val i = item(idLong(a))
        inventory.softDeleteItem(i.id)
        ActionOutcome("Removed ${i.name} from the pantry")
    }

    private fun shoppingAdd() = spec(
        "lib.shopping.add", Risk.LOW, "Add something to the shopping list. 'list' is optional; use an existing list name.",
        "item,qty,list",
        validate = { need(it, "item") },
        preview = { "Add ${it.argStr("item")} to the shopping list" },
    ) { a ->
        val name = a.argStr("item")!!
        val lists = inventory.listLists()
        val listId = a.argStr("list")?.let { inventory.ensureList(it, userId = userOrNull()) }
            ?: lists.firstOrNull { it.name.equals("Shopping", true) || it.name.equals("Groceries", true) }?.id
            ?: lists.firstOrNull()?.id
            ?: inventory.ensureList("Shopping", userId = userOrNull())
        val already = inventory.listLines(listId).any { it.itemName.equals(name, true) && it.status != com.ledgerai.app.domain.inventory.ShoppingStatus.CHECKED }
        if (already) ActionOutcome("$name is already on the list")
        else {
            val id = inventory.addShoppingLine(listId, name, a.argNum("qty"), userOrNull())
            check(id != 0L) { "Shopping item was not saved" }
            ActionOutcome("Added $name to the shopping list") { inventory.softDeleteLine(id) }
        }
    }

    private fun shoppingEdit() = spec(
        "lib.shopping.edit", Risk.MEDIUM, "Rename a shopping list item or change how many to buy.", "id,item,qty",
        validate = { needLong(it) },
        preview = { a -> line(idLong(a)).let { "Edit ${it.itemName} on the list: ${changes(a, "item", "qty")}" } },
    ) { a ->
        val old = line(idLong(a))
        inventory.updateLine(old.id, a.argStr("item") ?: old.itemName, if (a.has("qty")) a.argNum("qty") else old.qty)
        ActionOutcome("Updated ${old.itemName}") { inventory.updateLine(old.id, old.itemName, old.qty) }
    }

    private fun shoppingCheck() = spec(
        "lib.shopping.check", Risk.LOW, "Tick a shopping list item as bought.", "id",
        validate = { needLong(it) },
        preview = { a -> "Tick off ${line(idLong(a)).itemName}" },
    ) { a ->
        val l = line(idLong(a))
        inventory.checkOff(l.id, userOrNull())
        ActionOutcome("Ticked off ${l.itemName}")
    }

    private fun shoppingDelete() = spec(
        "lib.shopping.delete", Risk.MEDIUM, "Remove an item from the shopping list.", "id",
        validate = { needLong(it) },
        preview = { a -> "Remove ${line(idLong(a)).itemName} from the shopping list" },
    ) { a ->
        val l = line(idLong(a))
        inventory.softDeleteLine(l.id)
        ActionOutcome("Removed ${l.itemName} from the shopping list")
    }

    // --- Wardrobe -------------------------------------------------------------------------------

    private fun garmentAdd() = spec(
        "lib.garment.add", Risk.LOW, "Add a piece of clothing to the wardrobe.", "name,type,colors[],seasons[]",
        validate = { need(it, "name") },
        preview = { "Add ${it.argStr("name")} to the wardrobe" },
    ) { a ->
        val u = user()
        val id = wardrobe.addGarment(u, a.argStr("name")!!, a.argStr("type").orEmpty(), a.argStrings("colors"), a.argStrings("seasons"))
        check(id != 0L) { "Garment was not saved" }
        ActionOutcome("Added ${a.argStr("name")} to the wardrobe") { wardrobe.softDeleteGarment(u, id) }
    }

    private fun garmentEdit() = spec(
        "lib.garment.edit", Risk.MEDIUM, "Change a piece of clothing: name, type, colors, seasons or laundry status.",
        "id,name,type,colors[],seasons[],laundryStatus",
        validate = { needLong(it) },
        preview = { a -> garment(user(), idLong(a)).let { "Edit ${it.name}: ${changes(a, "name", "type", "colors", "seasons", "laundryStatus")}" } },
    ) { a ->
        val u = user()
        val old = garment(u, idLong(a))
        val saved = wardrobe.updateGarment(
            u, old.id, a.argStr("name") ?: old.name, a.argStr("type") ?: old.type,
            if (a.has("colors")) a.argStrings("colors") else old.colors,
            if (a.has("seasons")) a.argStrings("seasons") else old.seasons,
            a.argStr("laundryStatus") ?: old.laundryStatus,
        )
        check(saved) { "Could not save that garment" }
        ActionOutcome("Updated ${old.name}") { wardrobe.updateGarment(u, old.id, old.name, old.type, old.colors, old.seasons, old.laundryStatus) }
    }

    private fun garmentDelete() = spec(
        "lib.garment.delete", Risk.MEDIUM, "Remove a piece of clothing from the wardrobe.", "id",
        validate = { needLong(it) },
        preview = { a -> "Remove ${garment(user(), idLong(a)).name} from the wardrobe" },
    ) { a ->
        val u = user()
        val g = garment(u, idLong(a))
        wardrobe.softDeleteGarment(u, g.id)
        ActionOutcome("Removed ${g.name} from the wardrobe")
    }

    // --- Birthdays ------------------------------------------------------------------------------

    private fun birthdayAdd() = spec(
        "lib.birthday.add", Risk.LOW, "Save someone's birthday. Month 1-12, day 1-31. Year only if the user said it.",
        "name,month,day,year",
        validate = { need(it, "name") ?: if (it.argInt("month") == null || it.argInt("day") == null) "Missing month or day" else null },
        preview = { "Save ${it.argStr("name")}'s birthday (${it.argInt("day")}/${it.argInt("month")})" },
    ) { a ->
        val owner = userOrNull()
        val name = a.argStr("name")!!
        val known = birthdayDao.listActive().any { b -> birthdays.names(b.userId)[b.personId].equals(name, ignoreCase = true) }
        if (known) ActionOutcome("$name already has a birthday saved")
        else {
            val id = birthdays.save(
                null, owner, name, a.argInt("month")!!, a.argInt("day")!!, a.argInt("year"),
                BirthdayLadder.DEFAULT_CELEBRATE_MINUTE, LadderSwitches(), "",
            )
            engine.rebuildAndArm()
            ActionOutcome("Saved $name's birthday") { birthdays.delete(id); engine.rebuildAndArm() }
        }
    }

    private fun birthdayEdit() = spec(
        "lib.birthday.edit", Risk.MEDIUM,
        "Change a saved birthday: the person's name, month, day, year, note, the time of day it fires (HH:mm), or turn reminders on/off (month1, week2, week1, day3, day1, hour5, min30, min10, atTime = true|false).",
        "id,name,month,day,year,note,time,month1,week2,week1,day3,day1,hour5,min30,min10,atTime",
        validate = { need(it, "id") },
        preview = { a -> birthday(idStr(a)).let { "Edit ${birthdayName(it)}'s birthday: ${changes(a, "name", "month", "day", "year", "note", "time", "month1", "week2", "week1", "day3", "day1", "hour5", "min30", "min10", "atTime")}" } },
    ) { a ->
        val old = birthday(idStr(a))
        val oldName = birthdayName(old)
        val owner = old.userId ?: com.ledgerai.app.data.dates.LOCAL_OWNER
        val newName = a.argStr("name") ?: oldName
        if (!newName.equals(oldName, ignoreCase = false)) {
            // The name lives on the person, so this renames them everywhere.
            val p = people.findActive(owner, old.personId) ?: error("That person is gone")
            check(people.updatePerson(owner, p.id, newName, p.org, p.role, p.notes)) { "Could not rename" }
        }
        val sw = old.let { switches(it) }
        val next = LadderSwitches(
            month1 = a.argBool("month1") ?: sw.month1, week2 = a.argBool("week2") ?: sw.week2, week1 = a.argBool("week1") ?: sw.week1,
            day3 = a.argBool("day3") ?: sw.day3, day1 = a.argBool("day1") ?: sw.day1, hour5 = a.argBool("hour5") ?: sw.hour5,
            min30 = a.argBool("min30") ?: sw.min30, min10 = a.argBool("min10") ?: sw.min10, atTime = a.argBool("atTime") ?: sw.atTime,
        )
        val minute = a.argTime("time")?.let { it.hour * 60 + it.minute } ?: old.celebrateMinute
        birthdays.save(
            old.id, old.userId, newName,
            a.argInt("month") ?: old.month, a.argInt("day") ?: old.day,
            if (a.has("year")) a.argInt("year") else old.birthYear,
            minute, next, if (a.has("note")) a.argStr("note").orEmpty() else old.note,
        )
        engine.rebuildAndArm()
        ActionOutcome("Updated $newName's birthday") {
            if (newName != oldName) people.findActive(owner, old.personId)?.let { p -> people.updatePerson(owner, p.id, oldName, p.org, p.role, p.notes) }
            birthdays.save(old.id, old.userId, oldName, old.month, old.day, old.birthYear, old.celebrateMinute, sw, old.note)
            engine.rebuildAndArm()
        }
    }

    private fun birthdayDelete() = spec(
        "lib.birthday.delete", Risk.MEDIUM, "Remove a saved birthday.", "id",
        validate = { need(it, "id") },
        preview = { a -> "Delete ${birthdayName(birthday(idStr(a)))}'s birthday" },
    ) { a ->
        val b = birthday(idStr(a))
        val name = birthdayName(b)
        birthdays.delete(b.id)
        engine.rebuildAndArm()
        ActionOutcome("Removed $name's birthday")
    }

    // --- Trips ----------------------------------------------------------------------------------

    private fun tripAdd() = spec(
        "lib.trip.add", Risk.LOW, "Save a trip. Dates are yyyy-MM-dd; both are required.", "place,start,end,name,notes",
        validate = {
            need(it, "place")
                ?: if (it.argDate("start") == null || it.argDate("end") == null) "Missing start or end date"
                else if (!Trips.isValid(it.argDate("start")!!, it.argDate("end")!!)) "End date must be on or after the start date"
                else null
        },
        preview = { "Save a trip to ${it.argStr("place")} (${it.argStr("start")} to ${it.argStr("end")})" },
    ) { a ->
        val place = a.argStr("place")!!
        val id = trips.save(
            null, userOrNull(), a.argStr("name") ?: "Trip to $place", place,
            a.argDate("start")!!, a.argDate("end")!!, a.argStr("notes").orEmpty(), true, true,
        )
        engine.rebuildAndArm()
        ActionOutcome("Saved the trip to $place") { trips.delete(id); engine.rebuildAndArm() }
    }

    private fun tripEdit() = spec(
        "lib.trip.edit", Risk.MEDIUM, "Change a trip's name, place, dates, notes, or the week-before and day-before reminders (true|false).", "id,name,place,start,end,notes,remindWeek,remindDay",
        validate = { need(it, "id") },
        preview = { a -> trip(idStr(a)).let { "Edit trip ${it.name}: ${changes(a, "name", "place", "start", "end", "notes", "remindWeek", "remindDay")}" } },
    ) { a ->
        val old = trip(idStr(a))
        val start = a.argDate("start") ?: old.startDate
        val end = a.argDate("end") ?: old.endDate
        check(Trips.isValid(start, end)) { "End date must be on or after the start date" }
        trips.save(
            old.id, old.userId, a.argStr("name") ?: old.name, a.argStr("place") ?: old.place, start, end,
            if (a.has("notes")) a.argStr("notes").orEmpty().take(2000) else old.notes,
            a.argBool("remindWeek") ?: old.r1w, a.argBool("remindDay") ?: old.r1d,
        )
        engine.rebuildAndArm()
        ActionOutcome("Updated ${old.name}") {
            trips.save(old.id, old.userId, old.name, old.place, old.startDate, old.endDate, old.notes, old.r1w, old.r1d)
            engine.rebuildAndArm()
        }
    }

    private fun tripDelete() = spec(
        "lib.trip.delete", Risk.MEDIUM, "Remove a trip.", "id",
        validate = { need(it, "id") },
        preview = { a -> "Delete trip ${trip(idStr(a)).name}" },
    ) { a ->
        val t = trip(idStr(a))
        trips.delete(t.id)
        engine.rebuildAndArm()
        ActionOutcome("Removed ${t.name}")
    }

    // --- Achievements ---------------------------------------------------------------------------

    private fun achievementAdd() = spec(
        "lib.achievement.add", Risk.LOW, "Save something the user achieved. 'on' defaults to today.", "title,on,note",
        validate = { need(it, "title") },
        preview = { "Save achievement: ${it.argStr("title")}" },
    ) { a ->
        val id = UUID.randomUUID().toString()
        achievements.upsert(
            AchievementEntity(
                id, userOrNull(), a.argStr("title")!!.take(120), a.argDate("on") ?: LocalDate.now(),
                a.argStr("note").orEmpty().take(1000), null, System.currentTimeMillis(),
            )
        )
        ActionOutcome("Saved the achievement") { achievements.softDelete(id, System.currentTimeMillis()) }
    }

    private fun achievementEdit() = spec(
        "lib.achievement.edit", Risk.MEDIUM, "Change an achievement's title, date or note.", "id,title,on,note",
        validate = { need(it, "id") },
        preview = { a -> achievement(idStr(a)).let { "Edit achievement ${it.title}: ${changes(a, "title", "on", "note")}" } },
    ) { a ->
        val old = achievement(idStr(a))
        achievements.upsert(
            old.copy(
                title = a.argStr("title")?.take(120) ?: old.title,
                happenedOn = a.argDate("on") ?: old.happenedOn,
                note = if (a.has("note")) a.argStr("note").orEmpty().take(1000) else old.note,
                updatedAt = System.currentTimeMillis(),
            )
        )
        ActionOutcome("Updated ${old.title}") { achievements.upsert(old.copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun achievementDelete() = spec(
        "lib.achievement.delete", Risk.MEDIUM, "Remove an achievement.", "id",
        validate = { need(it, "id") },
        preview = { a -> "Delete achievement ${achievement(idStr(a)).title}" },
    ) { a ->
        val old = achievement(idStr(a))
        achievements.softDelete(old.id, System.currentTimeMillis())
        ActionOutcome("Removed ${old.title}")
    }

    // --- Wishes ---------------------------------------------------------------------------------

    private fun wishAdd() = spec(
        "lib.wish.add", Risk.LOW, "Save something the user wants. Leave amount out if no price was said.",
        "name,amount,note,link,priority(1-3),wantedBy(yyyy-MM)",
        validate = { need(it, "name") },
        preview = { "Add wish: ${it.argStr("name")}" },
    ) { a ->
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        wishes.upsert(
            WishEntity(
id = id, userId = userOrNull(), name = a.argStr("name")!!.take(120), amount = a.argNum("amount")?.takeIf { it >= 0 },
                priority = (a.argInt("priority") ?: 2).coerceIn(1, 3), link = a.argStr("link")?.take(2000),
                wantedBy = a.argStr("wantedBy")?.takeIf { WANTED_BY.matches(it) },
                note = a.argStr("note").orEmpty().take(1000), createdAt = now, updatedAt = now,
            )
        )
        ActionOutcome("Added ${a.argStr("name")} to Wishes") { wishes.softDelete(id, System.currentTimeMillis()) }
    }

    private fun wishEdit() = spec(
        "lib.wish.edit", Risk.MEDIUM,
        "Change any field of a wish: name, amount, note, link, priority 1-3, wantedBy (yyyy-MM), or status WANT or DROPPED (bought is marked in the app).",
        "id,name,amount,note,link,priority,wantedBy,status(WANT|DROPPED)",
        validate = { need(it, "id") },
        preview = { a -> wish(idStr(a)).let { "Edit wish ${it.name}: ${changes(a, "name", "amount", "note", "link", "priority", "wantedBy", "status")}" } },
    ) { a ->
        val old = wish(idStr(a))
        val status = a.argStr("status")?.uppercase()?.takeIf { it == WishStatus.WANT || it == WishStatus.DROPPED }
        wishes.upsert(
            old.copy(
                name = a.argStr("name")?.take(120) ?: old.name,
                amount = if (a.has("amount")) a.argNum("amount")?.takeIf { it >= 0 } else old.amount,
                note = if (a.has("note")) a.argStr("note").orEmpty().take(1000) else old.note,
                link = if (a.has("link")) a.argStr("link")?.take(2000) else old.link,
                wantedBy = if (a.has("wantedBy")) a.argStr("wantedBy")?.takeIf { WANTED_BY.matches(it) } else old.wantedBy,
                priority = a.argInt("priority")?.coerceIn(1, 3) ?: old.priority,
                status = status ?: old.status,
                updatedAt = System.currentTimeMillis(),
            )
        )
        ActionOutcome("Updated ${old.name}") { wishes.upsert(old.copy(updatedAt = System.currentTimeMillis())) }
    }

    private fun wishDelete() = spec(
        "lib.wish.delete", Risk.MEDIUM, "Remove a wish.", "id",
        validate = { need(it, "id") },
        preview = { a -> "Delete wish ${wish(idStr(a)).name}" },
    ) { a ->
        val old = wish(idStr(a))
        wishes.softDelete(old.id, System.currentTimeMillis())
        ActionOutcome("Removed ${old.name}")
    }

    // --- Notes (titles only are shown to the model; bodies are never sent) ---------------------

    private fun noteAdd() = spec(
        "lib.note.add", Risk.LOW, "Save a note.", "title,body,tags[]",
        validate = { if (it.argStr("title") == null && it.argStr("body") == null) "Missing title or body" else null },
        preview = { "Save note: ${it.argStr("title") ?: it.argStr("body")?.take(40)}" },
    ) { a ->
        val body = a.argStr("body").orEmpty()
        val title = a.argStr("title") ?: body.lineSequence().first().take(60)
        val id = notes.insert(NoteItem(title = title, body = body, tags = a.argStrings("tags")))
        check(id != 0L) { "Note was not saved" }
        ActionOutcome("Saved the note \"$title\"") { notes.delete(NoteItem(id = id, title = "")) }
    }

    private fun noteEdit() = spec(
        "lib.note.edit", Risk.MEDIUM, "Change a note: title, tags, location, or replace the whole text with 'body'. To add a line use lib.note.append.",
        "id,title,body,tags[],location",
        validate = { needLong(it) },
        preview = { a -> note(idLong(a)).let { "Edit note ${it.title}: ${changes(a, "title", "body", "tags", "location")}" } },
    ) { a ->
        val old = note(idLong(a))
        notes.update(
            old.copy(
                title = a.argStr("title") ?: old.title,
                body = if (a.has("body")) a.argStr("body").orEmpty() else old.body,
                tags = if (a.has("tags")) a.argStrings("tags") else old.tags,
                location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
            )
        )
        ActionOutcome("Updated ${old.title}") { notes.update(old) }
    }

    private fun noteAppend() = spec(
        "lib.note.append", Risk.MEDIUM, "Add a line to the end of an existing note.", "id,text",
        validate = { needLong(it) ?: need(it, "text") },
        preview = { a -> "Add to note ${note(idLong(a)).title}: ${a.argStr("text")?.take(60)}" },
    ) { a ->
        val old = note(idLong(a))
        notes.update(old.copy(body = old.body.trimEnd() + "\n" + a.argStr("text")!!))
        ActionOutcome("Added to ${old.title}") { notes.update(old) }
    }

    private fun noteDelete() = spec(
        "lib.note.delete", Risk.MEDIUM, "Delete a note.", "id",
        validate = { needLong(it) },
        preview = { a -> "Delete note ${note(idLong(a)).title}" },
    ) { a ->
        val old = note(idLong(a))
        notes.delete(old)
        ActionOutcome("Deleted ${old.title}")
    }

    // --- Tasks and events -----------------------------------------------------------------------

    private fun taskAdd() = spec(
        "lib.task.add", Risk.MEDIUM, "Create a to-do. Date (yyyy-MM-dd) and time (HH:mm) only if the user said them.",
        "title,date,time",
        validate = { need(it, "title") },
        preview = { "Add task: ${it.argStr("title")} ${it.argStr("date") ?: ""}".trim() },
    ) { a ->
        val dated = a.argDate("date") != null
        val start = (a.argDate("date") ?: LocalDate.now()).let { d -> a.argTime("time")?.let(d::atTime) ?: d.atStartOfDay() }
        val id = calendar.upsert(
            CalendarEvent(title = a.argStr("title")!!, startAt = start, endAt = start, hasDate = dated, kind = CalendarEventKind.TASK)
        )
        check(id != 0L) { "Task was not saved" }
        ActionOutcome("Added task ${a.argStr("title")}") { calendar.deleteById(id) }
    }

    private fun eventAdd() = spec(
        "lib.task.add_event", Risk.MEDIUM, "Create a calendar event on a date (yyyy-MM-dd), optional time (HH:mm) and place.",
        "title,date,time,place",
        validate = { need(it, "title") ?: if (it.argDate("date") == null) "Missing date" else null },
        preview = { "Add event: ${it.argStr("title")} on ${it.argStr("date")}" },
    ) { a ->
        val day = a.argDate("date")!!
        val time = a.argTime("time")
        val start = time?.let(day::atTime) ?: day.atStartOfDay()
        val id = calendar.upsert(
            CalendarEvent(
                title = a.argStr("title")!!, location = a.argStr("place").orEmpty(), startAt = start,
                endAt = if (time != null) start.plusHours(1) else start.plusDays(1),
                allDay = time == null, kind = CalendarEventKind.EVENT,
            )
        )
        check(id != 0L) { "Event was not saved" }
        engine.rebuildAndArm()
        ActionOutcome("Added event ${a.argStr("title")}") { calendar.deleteById(id) }
    }

    private fun taskEdit() = spec(
        "lib.task.edit", Risk.MEDIUM,
        "Change any field of a task or event: title, date (yyyy-MM-dd), time (HH:mm), endDate, endTime, allDay, place, notes, links.",
        "id,title,date,time,endDate,endTime,allDay,place,notes,links",
        validate = { needLong(it) },
        preview = { a -> event(idLong(a)).let { "Edit ${it.title}: ${changes(a, "title", "date", "time", "endDate", "endTime", "allDay", "place", "notes", "links")}" } },
    ) { a ->
        val old = event(idLong(a))
        val span = java.time.Duration.between(old.startAt, old.endAt)
        val moves = a.argDate("date") != null || a.argTime("time") != null
        val start = if (moves) (a.argDate("date") ?: old.startAt.toLocalDate()).atTime(a.argTime("time") ?: old.startAt.toLocalTime()) else old.startAt
        val end = if (a.argDate("endDate") != null || a.argTime("endTime") != null)
            (a.argDate("endDate") ?: start.toLocalDate()).atTime(a.argTime("endTime") ?: start.toLocalTime())
        else start.plus(span)
        check(!end.isBefore(start)) { "The end must not be before the start" }
        calendar.upsert(
            old.copy(
                title = a.argStr("title") ?: old.title,
                location = if (a.has("place")) a.argStr("place").orEmpty() else old.location,
                notes = if (a.has("notes")) a.argStr("notes").orEmpty() else old.notes,
                links = if (a.has("links")) a.argStr("links").orEmpty() else old.links,
                startAt = start, endAt = end,
                hasDate = old.hasDate || a.argDate("date") != null,
                allDay = a.argBool("allDay") ?: (if (a.argTime("time") != null) false else old.allDay),
            )
        )
        ActionOutcome("Updated ${old.title}") { calendar.upsert(old) }
    }

    private fun taskComplete() = spec(
        "lib.task.complete", Risk.LOW, "Mark a task done.", "id",
        validate = { needLong(it) },
        preview = { a -> "Mark ${event(idLong(a)).title} done" },
    ) { a ->
        val t = event(idLong(a))
        calendar.setCompleted(t.id, true)
        ActionOutcome("Marked ${t.title} done") { calendar.setCompleted(t.id, false) }
    }

    private fun taskDelete() = spec(
        "lib.task.delete", Risk.MEDIUM, "Delete a task or event. A repeating one is deleted with all its repeats.", "id",
        validate = { needLong(it) },
        preview = { a -> event(idLong(a)).let { "Delete ${it.title}${if (it.recurrence != null) " and all its repeats" else ""}" } },
    ) { a ->
        val t = event(idLong(a))
        calendar.deleteById(t.id)
        ActionOutcome("Deleted ${t.title}")
    }

    // --- Money: always HIGH, always waits for a tap ---------------------------------------------

    private fun transactionEdit() = spec(
        "lib.transaction.edit", Risk.HIGH, "Correct a transaction: amount, merchant, category, date or note.",
        "id,amount,type(EXPENSE|INCOME),merchant,category,date,note,location,currency,recurring",
        validate = { needLong(it) },
        preview = { a -> tx(idLong(a)).let { "Edit ${it.merchant.ifBlank { it.category.displayName }} ${money(it.amount)} on ${it.date}: ${changes(a, "amount", "type", "merchant", "category", "date", "note", "location", "currency", "recurring")}" } },
    ) { a ->
        val old = tx(idLong(a))
        val amount = a.argNum("amount")
        check(amount == null || amount > 0) { "Amount must be above zero" }
        val category = a.argStr("category")?.let { raw ->
            TransactionCategory.entries.firstOrNull { it.name.equals(raw, true) || it.displayName.equals(raw, true) }
        }
        transactions.update(
            old.copy(
                amount = amount ?: old.amount,
                merchant = a.argStr("merchant") ?: old.merchant,
                category = category ?: old.category,
                date = a.argDate("date") ?: old.date,
                note = if (a.has("note")) a.argStr("note").orEmpty() else old.note,
                type = com.ledgerai.app.domain.model.TransactionType.entries.firstOrNull { it.name.equals(a.argStr("type"), true) } ?: old.type,
                location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
                currency = a.argStr("currency")?.uppercase()?.take(3) ?: old.currency,
                isRecurring = a.argBool("recurring") ?: old.isRecurring,
            )
        )
        ActionOutcome("Updated the transaction") { transactions.update(old) }
    }

    private fun transactionDelete() = spec(
        "lib.transaction.delete", Risk.HIGH, "Delete a transaction.", "id",
        validate = { needLong(it) },
        preview = { a -> tx(idLong(a)).let { "Delete ${it.merchant.ifBlank { it.category.displayName }} ${money(it.amount)} on ${it.date}" } },
    ) { a ->
        val old = tx(idLong(a))
        transactions.delete(old)
        ActionOutcome("Deleted ${old.merchant.ifBlank { old.category.displayName }} ${money(old.amount)}")
    }

    private fun billEdit() = spec(
        "lib.bill.edit", Risk.HIGH, "Change a bill: amount, next due date (yyyy-MM-dd), frequency, or active flag.",
        "id,name,amount,nextDueDate,frequency(WEEKLY|MONTHLY|QUARTERLY|YEARLY),category,note,location,currency,active",
        validate = { needLong(it) },
        preview = { a -> bill(idLong(a)).let { "Edit bill ${it.name}: ${changes(a, "name", "amount", "nextDueDate", "frequency", "category", "note", "location", "currency", "active")}" } },
    ) { a ->
        val old = bill(idLong(a))
        val amount = a.argNum("amount")
        check(amount == null || amount > 0) { "Amount must be above zero" }
        bills.update(
            old.copy(
                name = a.argStr("name") ?: old.name,
                amount = amount ?: old.amount,
                nextDueDate = a.argDate("nextDueDate") ?: old.nextDueDate,
                frequency = BillFrequency.entries.firstOrNull { it.name.equals(a.argStr("frequency"), true) } ?: old.frequency,
                isActive = a.argBool("active") ?: old.isActive,
                category = a.argStr("category")?.let { raw -> TransactionCategory.entries.firstOrNull { it.name.equals(raw, true) || it.displayName.equals(raw, true) } } ?: old.category,
                note = if (a.has("note")) a.argStr("note").orEmpty() else old.note,
                location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
                currency = a.argStr("currency")?.uppercase()?.take(3) ?: old.currency,
            )
        )
        ActionOutcome("Updated ${old.name}") { bills.update(old) }
    }

    private fun billDelete() = spec(
        "lib.bill.delete", Risk.HIGH, "Delete a bill.", "id",
        validate = { needLong(it) },
        preview = { a -> bill(idLong(a)).let { "Delete bill ${it.name} ${money(it.amount)}" } },
    ) { a ->
        val old = bill(idLong(a))
        bills.delete(old)
        ActionOutcome("Deleted ${old.name}")
    }

    private fun debtEdit() = spec(
        "lib.debt.edit", Risk.HIGH,
        "Change any field of a debt: person (friendName), amount, direction (I_OWE|THEY_OWE), dateLent and dueDate (yyyy-MM-dd), phone, email, location, note, currency, paid (true|false).",
        "id,friendName,amount,direction,dateLent,dueDate,phone,email,location,note,currency,paid",
        validate = { needLong(it) },
        preview = { a -> debt(idLong(a)).let { "Edit debt with ${it.friendName}: ${changes(a, "friendName", "amount", "direction", "dateLent", "dueDate", "phone", "email", "location", "note", "currency", "paid")}" } },
    ) { a ->
        val old = debt(idLong(a))
        val amount = a.argNum("amount")
        check(amount == null || amount > 0) { "Amount must be above zero" }
        debts.update(
            old.copy(
                amount = amount ?: old.amount,
                dueDate = if (a.has("dueDate")) a.argDate("dueDate") else old.dueDate,
                note = if (a.has("note")) a.argStr("note").orEmpty() else old.note,
                friendName = a.argStr("friendName") ?: old.friendName,
                direction = com.ledgerai.app.domain.model.DebtDirection.entries.firstOrNull { it.name.equals(a.argStr("direction"), true) } ?: old.direction,
                dateLent = a.argDate("dateLent") ?: old.dateLent,
                phone = if (a.has("phone")) a.argStr("phone").orEmpty() else old.phone,
                email = if (a.has("email")) a.argStr("email").orEmpty() else old.email,
                location = if (a.has("location")) a.argStr("location").orEmpty() else old.location,
                currency = a.argStr("currency")?.uppercase()?.take(3) ?: old.currency,
                isPaid = a.argBool("paid") ?: old.isPaid,
            )
        )
        ActionOutcome("Updated the debt with ${old.friendName}") { debts.update(old) }
    }

    private fun debtPaid() = spec(
        "lib.debt.paid", Risk.HIGH, "Mark a debt as paid back in full.", "id",
        validate = { needLong(it) },
        preview = { a -> debt(idLong(a)).let { "Mark the debt with ${it.friendName} (${money(it.amount)}) as paid" } },
    ) { a ->
        val old = debt(idLong(a))
        debts.markAsPaid(old.id)
        ActionOutcome("Marked the debt with ${old.friendName} as paid") { debts.update(old) }
    }

    private fun debtDelete() = spec(
        "lib.debt.delete", Risk.HIGH, "Delete a debt.", "id",
        validate = { needLong(it) },
        preview = { a -> debt(idLong(a)).let { "Delete the debt with ${it.friendName} (${money(it.amount)})" } },
    ) { a ->
        val old = debt(idLong(a))
        debts.delete(old)
        ActionOutcome("Deleted the debt with ${old.friendName}")
    }
}
