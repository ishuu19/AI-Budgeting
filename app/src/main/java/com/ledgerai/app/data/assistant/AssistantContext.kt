package com.ledgerai.app.data.assistant

import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.dates.BirthdayDao
import com.ledgerai.app.data.dates.BirthdayRepository
import com.ledgerai.app.data.dates.TripDao
import com.ledgerai.app.data.household.HouseholdRepository
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.domain.assistant.AssistantArea
import com.ledgerai.app.domain.assistant.contextSection
import com.ledgerai.app.domain.assistant.matching
import com.ledgerai.app.domain.assistant.oneLine
import com.ledgerai.app.domain.inventory.ShoppingStatus
import com.ledgerai.app.domain.inventory.StockQuantity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the assistant is allowed to read for one call. A seam so the assistant is testable without a database. */
interface AssistantFacts {
    /** [keywords] narrows each section to records that mention one of them; empty means no filter. */
    suspend fun build(areas: Set<AssistantArea>, userId: String, keywords: List<String> = emptyList()): String
}

/**
 * Reads the signed-in user's own data into compact text for the model: one line per record with its
 * id, so an edit or delete can name the exact record. Read-only. Passwords, locked Vault folders,
 * and locked notes are never included. Note text is searched on the phone only when keywords are given, and only a short snippet is sent. Unknown values are written as "unknown", never guessed.
 */
@Singleton
class AssistantContext @Inject constructor(
    private val people: PeopleRepository,
    private val memories: MemoryRepository,
    private val jobs: JobRepository,
    private val inventory: InventoryRepository,
    private val wardrobe: WardrobeRepository,
    private val birthdays: BirthdayRepository,
    private val birthdayDao: BirthdayDao,
    private val tripDao: TripDao,
    private val achievements: AchievementDao,
    private val wishes: WishDao,
    private val notes: NoteRepository,
    private val calendar: CalendarRepository,
    private val transactions: TransactionRepository,
    private val bills: BillRepository,
    private val debts: DebtRepository,
    private val households: HouseholdRepository,
    private val appUsage: com.ledgerai.app.data.appusage.AppUsageDao,
) : AssistantFacts {
    override suspend fun build(areas: Set<AssistantArea>, userId: String, keywords: List<String>): String {
        val parts = ArrayList<String>()
        val budget = (MAX_CHARS / areas.size.coerceAtLeast(1)).coerceAtLeast(MIN_SECTION_CHARS)
        for (area in AssistantArea.entries) {
            if (area !in areas) continue
            // One broken source must not stop the others, but the model is told it is missing.
            val text = runCatching { section(area, userId, keywords.map { it.trim() }.filter { it.length >= 2 }) }.getOrElse { "${area.label}: could not be read right now" }
            parts += fit(text, budget)
        }
        return parts.joinToString("\n\n")
    }

    /** Cuts a section at a line break so the whole prompt stays under what the proxy accepts. */
    private fun fit(section: String, budget: Int): String {
        if (section.length <= budget) return section
        val cut = section.lastIndexOf('\n', budget).takeIf { it > 0 } ?: budget
        return section.substring(0, cut) + "\n(more records exist but are not shown)"
    }

    private suspend fun section(area: AssistantArea, userId: String, kw: List<String>): String = when (area) {
        AssistantArea.PERSON -> peopleSection(userId, kw)
        AssistantArea.JOB -> sec(kw, "Jobs", jobs.observeAll().first().map {
            "[${it.id}] ${it.company} | ${it.title} | ${it.status.name} | applied ${it.appliedOn}" +
                (it.followUpOn?.let { d -> " | follow up $d" } ?: "") +
                (if (it.contact.isNotBlank()) " | contact ${oneLine(it.contact, 40)}" else "") +
                (if (it.location.isNotBlank()) " | location ${oneLine(it.location, 40)}" else "") +
                (if (it.url.isNotBlank()) " | url ${oneLine(it.url, 60)}" else "") +
                (if (it.source.isNotBlank()) " | source ${oneLine(it.source, 30)}" else "") +
                (if (it.extraDates.isNotBlank()) " | other dates: ${oneLine(it.extraDates, 60)}" else "") +
                (if (it.notes.isNotBlank()) " | notes: ${oneLine(it.notes, 80)}" else "")
        }, 40)
        AssistantArea.PANTRY -> sec(kw, "Pantry (items at home)", inventory.listItems().map {
            "[${it.id}] ${it.name} | " + (it.quantity?.let { q -> "${num(q)} ${it.unit}".trim() } ?: "quantity unknown") +
                (if (it.location.isNotBlank()) " | ${it.location}" else "") +
                (if (it.category.isNotBlank()) " | category ${it.category}" else "") + " | ${it.kind.name.lowercase()}" +
                (it.expiresOn?.let { d -> " | expires $d" } ?: "") +
                (if (StockQuantity.isLowOrOut(it.quantity)) " | LOW" else "")
        }, 150)
        AssistantArea.SHOPPING -> shoppingSection(kw)
        AssistantArea.GARMENT -> sec(kw, "Wardrobe", wardrobe.listGarments(userId).map {
            "[${it.id}] ${it.name} | ${it.type} | ${it.colors.joinToString("/")} | " +
                "${it.seasons.joinToString("/").ifBlank { "seasons unknown" }} | laundry ${it.laundryStatus}"
        }, 120)
        AssistantArea.BIRTHDAY -> {
            val rows = birthdayDao.listActive()
            val names = rows.map { it.userId }.distinct().flatMap { birthdays.names(it).entries }.associate { it.key to it.value }
            sec(kw, "Birthdays", rows.sortedWith(compareBy({ it.month }, { it.day })).map {
                "[${it.id}] ${names[it.personId] ?: "Someone"} | ${it.day} ${Month.of(it.month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}" +
                    (it.birthYear?.let { y -> " $y" } ?: "") +
                    " | at %02d:%02d".format(it.celebrateMinute / 60, it.celebrateMinute % 60) +
                    (if (it.note.isNotBlank()) " | note: ${oneLine(it.note, 50)}" else "")
            }, 80)
        }
        AssistantArea.TRIP -> sec(kw, "Trips", tripDao.listActive().sortedBy { it.startDate }.map {
            "[${it.id}] ${it.name} | ${it.place} | ${it.startDate} to ${it.endDate}" +
                (if (it.notes.isNotBlank()) " | ${oneLine(it.notes, 60)}" else "")
        }, 40)
        AssistantArea.ACHIEVEMENT -> sec(kw, "Achievements", achievements.observeActive().first().map {
            "[${it.id}] ${it.title} | ${it.happenedOn}" + (if (it.note.isNotBlank()) " | ${oneLine(it.note, 60)}" else "")
        }, 40)
        AssistantArea.WISH -> wishSection(kw)
        AssistantArea.NOTE -> noteSection(kw)
        AssistantArea.APPUSAGE -> appUsageSection()
        AssistantArea.TASK -> calendarSection(kw)
        AssistantArea.TRANSACTION -> transactionsSection(kw)
        AssistantArea.BILL -> sec(kw, "Bills (active)", bills.getActiveBills().first().sortedBy { it.nextDueDate }.map {
            "[${it.id}] ${it.name} | ${num(it.amount)} ${it.currency} | ${it.frequency.name} | ${it.category.displayName} | next due ${it.nextDueDate}" +
                (if (it.location.isNotBlank()) " | place ${oneLine(it.location, 30)}" else "") +
                (if (it.note.isNotBlank()) " | ${oneLine(it.note, 40)}" else "")
        }, 40)
        AssistantArea.DEBT -> sec(kw, "Debts (not yet paid)", debts.getActiveDebts().first().map {
            "[${it.id}] ${it.friendName} | ${if (it.direction.name == "I_OWE") "I owe" else "they owe me"} | ${num(it.amount)} ${it.currency}" +
                " | lent ${it.dateLent}" + (it.dueDate?.let { d -> " | due $d" } ?: "") +
                (if (it.phone.isNotBlank()) " | phone ${it.phone}" else "") +
                (if (it.email.isNotBlank()) " | email ${it.email}" else "") +
                (if (it.location.isNotBlank()) " | place ${oneLine(it.location, 30)}" else "") +
                (if (it.note.isNotBlank()) " | ${oneLine(it.note, 40)}" else "")
        }, 40)
        AssistantArea.HOUSEHOLD -> householdSection(userId, kw)
    }

    /** A section, narrowed to the records that mention a keyword when there are keywords. */
    private fun sec(kw: List<String>, title: String, lines: List<String>, cap: Int): String {
        if (kw.isEmpty()) return contextSection(title, lines, cap)
        val hit = matching(lines, kw)
        val words = kw.joinToString("/")
        // Words like "notes" or "list" match nothing; then the whole section is shown rather than nothing.
        return if (hit.isEmpty()) contextSection("$title (nothing matched $words, so everything is shown)", lines, cap)
        else contextSection("$title, ${hit.size} of ${lines.size} match $words", hit, cap)
    }

    private suspend fun wishSection(kw: List<String>): String {
        val today = LocalDate.now()
        val money = "Money this month so far: income ${num(transactions.getTotalIncomeForMonth(today.year, today.monthValue))}, " +
            "spent ${num(transactions.getTotalExpensesForMonth(today.year, today.monthValue))}."
        return money + "\n" + sec(kw, "Wishes", wishes.observeActive().first().map {
            "[${it.id}] ${it.name} | " + (it.amount?.let { a -> num(a) } ?: "price unknown") + " | ${it.status} | priority ${it.priority}" +
                (it.wantedBy?.let { w -> " | wanted by $w" } ?: "") +
                (it.link?.let { l -> " | link ${oneLine(l, 50)}" } ?: "") +
                (if (it.note.isNotBlank()) " | ${oneLine(it.note, 60)}" else "")
        }, 40)
    }

    /** Titles and tags always; with keywords the text is searched on the phone and only a short snippet is sent. */
    private suspend fun noteSection(kw: List<String>): String {
        val all = notes.observeNotes().first()
        fun line(n: com.ledgerai.app.domain.model.NoteItem, snippet: String?) =
            "[${n.id}] ${oneLine(n.title, 60)}" + (if (n.tags.isNotEmpty()) " | tags ${n.tags.joinToString(",")}" else "") +
                (if (n.location.isNotBlank()) " | place ${oneLine(n.location, 40)}" else "") +
                (snippet?.let { " | text: $it" } ?: "")
        if (kw.isEmpty()) return contextSection("Notes (titles only; the text is not shown)", all.map { line(it, null) }, 60)
        val hits = all.mapNotNull { n ->
            val inMeta = matching(listOf(n.title + " " + n.tags.joinToString(" ")), kw).isNotEmpty()
            val at = kw.firstNotNullOfOrNull { k -> n.body.indexOf(k, ignoreCase = true).takeIf { it >= 0 } }
            when {
                at != null -> line(n, oneLine(n.body.substring((at - 40).coerceAtLeast(0)), 110))
                inMeta -> line(n, null)
                else -> null
            }
        }
        val words = kw.joinToString("/")
        return if (hits.isEmpty()) contextSection("Notes (none mention $words; ${all.size} notes in total, titles only)", all.map { line(it, null) }, 40)
        else contextSection("Notes, ${hits.size} of ${all.size} mention $words", hits, 30)
    }

    private suspend fun appUsageSection(): String {
        val today = LocalDate.now()
        fun fmt(ms: Long) = (ms / 3_600_000).let { h -> if (h > 0) "${h}h " else "" } + "${(ms / 60_000) % 60}m"
        val lines = ArrayList<String>()
        for ((label, days) in listOf("Today" to 0L, "Last 7 days" to 6L, "Last 30 days" to 29L)) {
            val top = appUsage.observeTotals(today.minusDays(days), today).first().take(8)
            lines += if (top.isEmpty()) "$label: no data"
            else "$label: " + top.joinToString("; ") { "${it.label} ${fmt(it.foregroundMs)}" }
        }
        return "App time (screen time per app on this phone, most used first)\n" + lines.joinToString("\n")
    }

    private fun num(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else "%.2f".format(v)

    private suspend fun peopleSection(userId: String, kw: List<String>): String {
        val list = people.listPeople(userId).sortedBy { it.name.lowercase() }
        val lines = ArrayList<String>()
        for ((i, p) in list.withIndex()) {
            val block = StringBuilder("[${p.id}] ${p.name}" + (p.org?.let { " | $it" } ?: "") + (p.role?.let { " | $it" } ?: "") +
                (if (p.notes.isNotBlank()) " | notes: ${oneLine(p.notes, 100)}" else ""))
            // A person and their memories are one block, so a keyword in a memory finds the person.
            // Without keywords only the first people get memories, so the prompt stays short.
            if (kw.isNotEmpty() || i < MEMORY_PEOPLE) {
                memories.listMemories(userId, p.id).take(if (kw.isEmpty()) 3 else 6)
                    .forEach { block.append("\n    memory: ${oneLine(it.text, 100)}") }
            }
            lines += block.toString()
        }
        return sec(kw, "People", lines, 160)
    }

    private suspend fun shoppingSection(kw: List<String>): String {
        val lines = ArrayList<String>()
        for (list in inventory.listLists()) {
            for (l in inventory.listLines(list.id)) {
                val done = l.status == ShoppingStatus.CHECKED
                lines += "[${l.id}] ${l.itemName}" + (l.qty?.let { " x${num(it)}" } ?: "") + " | list ${list.name}" +
                    (if (done) " | bought" else " | to buy")
            }
        }
        return sec(kw, "Shopping list", lines.sortedBy { it.endsWith("bought") }, 100)
    }

    private suspend fun calendarSection(kw: List<String>): String {
        val upcoming = calendar.listNextDays(14)
        val tasks = calendar.observeTasks().first().filter { !it.isCompleted }
        // A keyword search also reaches past events, which the plain view does not show.
        val found = if (kw.isEmpty()) emptyList() else calendar.observeAll().first().filter { e ->
            kw.any { e.title.contains(it, true) || e.notes.contains(it, true) || e.location.contains(it, true) }
        }.sortedByDescending { it.startAt }.take(40)
        val rows = (upcoming + tasks + found).distinctBy { it.id to it.startAt }
        return sec(kw, "Tasks and events (next 14 days, plus open tasks)", rows.sortedBy { it.startAt }.map {
            "[${it.id}] ${it.kind.name.lowercase()} | ${it.title} | " +
                (if (it.hasDate) it.startAt.toString().replace('T', ' ') else "no date") +
                (if (it.hasDate && it.endAt != it.startAt) " to ${it.endAt.toString().replace('T', ' ')}" else "") +
                (if (it.allDay) " | all day" else "") +
                (if (it.location.isNotBlank()) " | place ${oneLine(it.location, 40)}" else "") +
                (if (it.notes.isNotBlank()) " | notes: ${oneLine(it.notes, 60)}" else "") +
                (if (it.links.isNotBlank()) " | links ${oneLine(it.links, 50)}" else "") +
                (if (it.isCompleted) " | done" else "") + (if (it.recurrence != null) " | repeats" else "")
        }, 80)
    }

    private suspend fun transactionsSection(kw: List<String>): String {
        val today = LocalDate.now()
        val income = transactions.getTotalIncomeForMonth(today.year, today.monthValue)
        val spent = transactions.getTotalExpensesForMonth(today.year, today.monthValue)
        val head = "This month so far: income ${num(income)}, spent ${num(spent)}."
        // With keywords, look through every transaction, not only the newest.
        val source = if (kw.isEmpty()) transactions.getRecentTransactions(30).first()
        else transactions.getAllTransactions().first().sortedByDescending { it.date }
        val recent = source.map {
            "[${it.id}] ${it.date} | ${it.type.name.lowercase()} ${num(it.amount)} ${it.currency} | ${it.category.displayName}" +
                (if (it.merchant.isNotBlank()) " | ${oneLine(it.merchant, 40)}" else "") +
                (if (it.location.isNotBlank()) " | place ${oneLine(it.location, 30)}" else "") +
                (if (it.isRecurring) " | recurring" else "") +
                (if (it.note.isNotBlank()) " | ${oneLine(it.note, 40)}" else "")
        }
        return head + "\n" + sec(kw, "Transactions (newest first)", recent, 30)
    }

    private suspend fun householdSection(userId: String, kw: List<String>): String {
        val lines = households.listMine(userId).map { h ->
            val members = households.observeMembers(h.id).first().joinToString(", ") { it.displayName.ifBlank { "a member" } }
            "${h.name}: $members"
        }
        return sec(kw, "Households", lines, 10)
    }

    private companion object {
        const val MEMORY_PEOPLE = 30

        /** `ai-proxy` keeps 8000 characters of the user text, and the message itself comes last. */
        const val MAX_CHARS = 6000
        const val MIN_SECTION_CHARS = 1500
    }
}
