package com.ledgerai.app.data.repository

import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.ai.VoiceRuleLearner
import com.ledgerai.app.data.ai.PlaceMatch
import com.ledgerai.app.data.ai.QuickParse
import com.ledgerai.app.data.ai.VoiceResultKind
import com.ledgerai.app.data.ai.resultKind
import com.ledgerai.app.data.local.room.BillDao
import com.ledgerai.app.data.local.room.BudgetDao
import com.ledgerai.app.data.local.room.DebtDao
import com.ledgerai.app.data.local.room.GoalDao
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.TransactionDao
import com.ledgerai.app.data.local.room.VoiceHistoryDao
import com.ledgerai.app.data.local.room.VoiceHistoryEntity
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One row of the editable voice history. */
data class VoiceHistoryItem(
    val id: Long,
    val transcript: String,
    val kind: VoiceResultKind,
    val linkedItemId: Long?,
    val summary: String,
    val createdAt: LocalDateTime
)

/** What a confirmed card became. [undo] removes the item again and drops its history row. */
class SavedVoiceItem(
    val kind: VoiceResultKind,
    val itemId: Long,
    val historyId: Long,
    val summary: String,
    val undo: suspend () -> Unit
)

/**
 * Saves confirmed voice results into the right store and keeps the voice history.
 * Every save writes (or updates) one `voice_history` row linked to the new item id.
 */
@Singleton
class VoiceCaptureRepository @Inject constructor(
    private val historyDao: VoiceHistoryDao,
    private val transactions: TransactionRepository,
    private val transactionDao: TransactionDao,
    private val calendar: CalendarRepository,
    private val notes: NoteRepository,
    private val noteDao: NoteDao,
    private val bills: BillRepository,
    private val billDao: BillDao,
    private val debts: DebtRepository,
    private val debtDao: DebtDao,
    private val goals: GoalRepository,
    private val goalDao: GoalDao,
    private val budgets: BudgetRepository,
    private val budgetDao: BudgetDao,
    private val jobs: JobRepository,
    private val ruleLearner: VoiceRuleLearner,
) {

    fun observeHistory(limit: Int = 100): Flow<List<VoiceHistoryItem>> =
        historyDao.observe(limit).map { rows -> rows.map { it.toItem() } }

    /**
     * Saves [intent]. Returns null when it cannot be saved (missing amount or title).
     * With [history] the existing row is updated instead of a new one added.
     * [remindersEdited] false means a new event with no reminders gets the standard defaults.
     */
    suspend fun save(
        intent: ParsedIntent,
        transcript: String,
        priorHistory: VoiceHistoryItem? = null,
        remindersEdited: Boolean = false
    ): SavedVoiceItem? {
        val history = priorHistory ?: historyDao.latestUnlinked(transcript.trim())?.toItem()
        val kind = intent.resultKind()
        var restoreBudget: (suspend () -> Unit)? = null
        val (itemId, summary) = when (intent) {
            is ParsedIntent.Transaction -> {
                val amount = intent.amount?.takeIf { it > 0.0 } ?: return null
                val id = transactions.insert(
                    Transaction(
                        amount = amount,
                        type = intent.type,
                        category = intent.category,
                        merchant = intent.merchant,
                        note = intent.note.ifBlank { if (intent.merchant.isBlank()) transcript else "" },
                        date = intent.date,
                        createdAt = LocalDateTime.now()
                    )
                )
                id to intent.merchant.ifBlank { intent.category.displayName }
            }
            is ParsedIntent.Event -> {
                if (intent.title.isBlank() && intent.kind != CalendarEventKind.ALARM) return null
                val event = intent.toCalendarEvent()
                val title = event.title.ifBlank { "Alarm" }
                val id = calendar.upsert(
                    event.copy(title = title),
                    withDefaultReminders = !remindersEdited && intent.kind != CalendarEventKind.ROUTINE
                )
                id to title
            }
            is ParsedIntent.Note -> {
                if (intent.title.isBlank() && intent.body.isBlank()) return null
                val title = intent.title.trim().ifBlank { "Untitled" }
                val id = notes.insert(
                    NoteItem(
                        title = title,
                        body = intent.body.trim(),
                        tags = intent.tags.map { it.trim() }.filter { it.isNotEmpty() }
                    )
                )
                id to title
            }
            is ParsedIntent.Bill -> {
                val name = intent.name.trim()
                if (name.isBlank() || intent.amount <= 0.0) return null
                val id = bills.insert(
                    Bill(
                        name = name,
                        amount = intent.amount,
                        frequency = intent.frequency,
                        nextDueDate = intent.nextDueDate,
                        category = intent.category
                    )
                )
                id to name
            }
            is ParsedIntent.Debt -> {
                val name = intent.friendName.trim()
                if (name.isBlank() || intent.amount <= 0.0) return null
                val id = debts.insert(
                    Debt(
                        friendName = name,
                        amount = intent.amount,
                        direction = intent.direction,
                        dueDate = intent.dueDate
                    )
                )
                id to name
            }
            is ParsedIntent.Goal -> {
                val name = intent.name.trim()
                if (name.isBlank() || intent.targetAmount <= 0.0) return null
                val id = goals.insert(Goal(name = name, targetAmount = intent.targetAmount))
                id to name
            }
            is ParsedIntent.Job -> {
                val company = intent.company.trim()
                if (company.isBlank()) return null
                val id = jobs.save(
                    JobApplication(
                        company = company,
                        title = intent.title.trim().ifBlank { "Role" },
                        status = intent.status,
                        appliedOn = intent.appliedOn,
                        followUpOn = intent.followUpOn,
                        notes = intent.notes.trim(),
                        source = intent.source.trim(),
                        url = intent.url.trim(),
                        location = intent.location.trim(),
                        extraDates = intent.extraDates.trim()
                    )
                )
                id to company
            }
            is ParsedIntent.Adjust -> return applyAdjust(intent, transcript, history)
            is ParsedIntent.Budget -> {
                if (intent.limit <= 0.0) return null
                val today = LocalDate.now()
                val existing = budgets.getBudgetForCategory(intent.category, today.monthValue, today.year)
                val id = if (existing != null) {
                    budgets.update(existing.copy(monthlyLimit = intent.limit))
                    restoreBudget = { budgets.update(existing) }
                    existing.id
                } else {
                    budgets.insert(
                        Budget(
                            category = intent.category,
                            monthlyLimit = intent.limit,
                            month = today.monthValue,
                            year = today.year
                        )
                    )
                }
                id to intent.category.displayName
            }
            is ParsedIntent.Unmatched -> return null
        }

        val now = System.currentTimeMillis()
        val historyId = if (history != null) {
            historyDao.update(
                VoiceHistoryEntity(
                    id = history.id,
                    transcript = transcript,
                    resultKind = kind.name,
                    linkedItemId = itemId,
                    resultSummary = summary,
                    createdAt = history.createdAt.toEpochMs()
                )
            )
            history.id
        } else {
            historyDao.insert(
                VoiceHistoryEntity(
                    transcript = transcript,
                    resultKind = kind.name,
                    linkedItemId = itemId,
                    resultSummary = summary,
                    createdAt = now
                )
            )
        }
        val undo: suspend () -> Unit = {
            val restore = restoreBudget
            if (restore != null) restore() else deleteLinked(kind, itemId)
            if (history != null) {
                historyDao.update(history.toEntity())
            } else {
                historyDao.softDelete(historyId, System.currentTimeMillis())
            }
        }
        runCatching { ruleLearner.refresh() }
        return SavedVoiceItem(kind, itemId, historyId, summary, undo)
    }

    private suspend fun applyAdjust(
        intent: ParsedIntent.Adjust,
        transcript: String,
        history: VoiceHistoryItem?
    ): SavedVoiceItem? {
        val job = if (intent.kindHint != "event" && intent.kindHint != "spend") {
            jobs.observeAll().first()
                .maxByOrNull { wordHits("${it.company} ${it.title} ${it.source} ${it.location}", intent.query) }
                ?.takeIf { wordHits("${it.company} ${it.title}", intent.query) > 0 }
        } else null
        if (job != null) {
            return if (intent.remove) {
                jobs.delete(job.id)
                rememberAdjust(transcript, history, "Removed ${job.company}", job.id) {
                    jobs.save(job.copy(id = 0))
                }
            } else {
                val spoken = intent.replacement.ifBlank { intent.rawTranscript }
                val parsed = QuickParse.parseVoiceIntent(spoken)
                val updated = if (parsed is ParsedIntent.Job) {
                    job.copy(
                        title = parsed.title.takeUnless { it.equals("Role", true) || it.equals("Interview", true) } ?: job.title,
                        source = parsed.source.ifBlank { job.source },
                        url = parsed.url.ifBlank { job.url },
                        location = PlaceMatch.snap(
                            parsed.location.ifBlank { job.location },
                            jobs.observeAll().first().map { it.location }
                        ),
                        extraDates = parsed.extraDates.ifBlank { job.extraDates },
                        appliedOn = if (parsed.appliedSpoken) parsed.appliedOn else job.appliedOn,
                        followUpOn = parsed.followUpOn ?: job.followUpOn,
                        status = parsed.status
                    )
                } else {
                    job.copy(title = spoken.trim().ifBlank { job.title })
                }
                jobs.save(updated)
                rememberAdjust(transcript, history, "Updated ${updated.company}", updated.id) {
                    jobs.save(job)
                }
            }
        }
        if (intent.kindHint != "job" && intent.kindHint != "spend") {
            val today = LocalDate.now()
            val event = calendar.listRange(today.minusDays(60), today.plusDays(120))
                .maxByOrNull { wordHits(it.title, intent.query) }
                ?.takeIf { wordHits(it.title, intent.query) > 0 }
            if (event != null && intent.remove) {
                calendar.deleteById(event.id)
                return rememberAdjust(transcript, history, "Removed ${event.title}", event.id) {
                    calendar.upsert(event.copy(id = 0))
                }
            }
        }
        if (intent.kindHint != "job" && intent.kindHint != "event" && intent.remove) {
            val tx = transactions.getAllTransactions().first()
                .maxByOrNull { wordHits("${it.merchant} ${it.note}", intent.query) }
                ?.takeIf { wordHits("${it.merchant} ${it.note}", intent.query) > 0 }
            if (tx != null) {
                transactions.delete(tx)
                return rememberAdjust(transcript, history, "Removed ${tx.merchant.ifBlank { tx.note }}", tx.id) {
                    transactions.insert(tx.copy(id = 0))
                }
            }
        }
        return null
    }

    private fun wordHits(hay: String, query: String): Int {
        val skip = setOf("the", "job", "role", "and", "for", "at")
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 && it !in skip }
        if (words.isEmpty()) return if (query.isNotBlank() && hay.contains(query.trim(), true)) 1 else 0
        val text = hay.lowercase()
        return words.count { text.contains(it) }
    }

    private suspend fun rememberAdjust(
        transcript: String,
        history: VoiceHistoryItem?,
        summary: String,
        itemId: Long,
        undo: suspend () -> Unit
    ): SavedVoiceItem {
        val now = System.currentTimeMillis()
        val historyId = if (history != null) {
            historyDao.update(
                history.toEntity().copy(
                    transcript = transcript,
                    resultKind = VoiceResultKind.Edit.name,
                    linkedItemId = itemId,
                    resultSummary = summary
                )
            )
            history.id
        } else {
            historyDao.insert(
                VoiceHistoryEntity(
                    transcript = transcript,
                    resultKind = VoiceResultKind.Edit.name,
                    linkedItemId = itemId,
                    resultSummary = summary,
                    createdAt = now
                )
            )
        }
        return SavedVoiceItem(VoiceResultKind.Edit, itemId, historyId, summary) {
            undo()
            if (history == null) historyDao.softDelete(historyId, System.currentTimeMillis())
        }
    }

    /** Keeps every spoken or typed line, even before the user confirms a card. */
    suspend fun recordHeard(transcript: String) {
        val text = transcript.trim()
        if (text.isEmpty()) return
        if (historyDao.latestUnlinked(text) != null) return
        historyDao.insert(
            VoiceHistoryEntity(
                transcript = text,
                resultKind = VoiceResultKind.Unsorted.name,
                resultSummary = "heard",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** Records a capture that produced no item (nothing recognised, or the user kept only the words). */
    suspend fun recordUnsorted(transcript: String, history: VoiceHistoryItem? = null): Long {
        val text = transcript.trim()
        if (text.isEmpty()) return 0L
        return if (history != null) {
            historyDao.update(
                history.toEntity().copy(
                    transcript = text,
                    resultKind = VoiceResultKind.Unsorted.name,
                    linkedItemId = null,
                    resultSummary = ""
                )
            )
            history.id
        } else {
            historyDao.insert(
                VoiceHistoryEntity(
                    transcript = text,
                    resultKind = VoiceResultKind.Unsorted.name,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Edits transcript and label only. The linked item is not touched. */
    suspend fun updateHistory(item: VoiceHistoryItem, transcript: String, kind: VoiceResultKind) {
        val text = transcript.trim()
        if (text.isEmpty()) return
        historyDao.update(item.toEntity().copy(transcript = text, resultKind = kind.name))
    }

    /** Removes a history row, and the item it created when [deleteItem] is set. */
    suspend fun deleteHistory(item: VoiceHistoryItem, deleteItem: Boolean) {
        if (deleteItem) item.linkedItemId?.let { deleteLinked(item.kind, it) }
        historyDao.softDelete(item.id, System.currentTimeMillis())
    }

    /** Soft-deletes the item a history row points to. */
    suspend fun deleteLinked(kind: VoiceResultKind, itemId: Long) {
        if (itemId <= 0L) return
        val now = System.currentTimeMillis()
        when (kind) {
            VoiceResultKind.Spend, VoiceResultKind.Income -> transactionDao.softDelete(itemId, now, now)
            VoiceResultKind.Task, VoiceResultKind.Reminder, VoiceResultKind.Event, VoiceResultKind.Exam,
            VoiceResultKind.Routine, VoiceResultKind.Alarm -> calendar.deleteById(itemId)
            VoiceResultKind.Budget -> budgetDao.softDelete(itemId, now, now)
            VoiceResultKind.Bill -> billDao.softDelete(itemId, now, now)
            VoiceResultKind.Debt -> debtDao.softDelete(itemId, now, now)
            VoiceResultKind.Goal -> goalDao.softDelete(itemId, now, now)
            VoiceResultKind.Note -> noteDao.softDelete(itemId, now, now)
            VoiceResultKind.Job -> jobs.delete(itemId)
            VoiceResultKind.Edit, VoiceResultKind.Unsorted -> Unit
        }
    }

    private fun VoiceHistoryEntity.toItem() = VoiceHistoryItem(
        id = id,
        transcript = transcript,
        kind = VoiceResultKind.fromName(resultKind),
        linkedItemId = linkedItemId,
        summary = resultSummary,
        createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault())
    )

    private fun VoiceHistoryItem.toEntity() = VoiceHistoryEntity(
        id = id,
        transcript = transcript,
        resultKind = kind.name,
        linkedItemId = linkedItemId,
        resultSummary = summary,
        createdAt = createdAt.toEpochMs()
    )

    private fun LocalDateTime.toEpochMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
