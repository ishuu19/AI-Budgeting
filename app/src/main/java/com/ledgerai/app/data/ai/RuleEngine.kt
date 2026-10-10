package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.TransactionCategory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException

/** Which path produced the entry the user sees. */
enum class IntentSource(val label: String) { ON_DEVICE("On device"), RULES("Rules"), AI("AI") }

/** A field the rules found concrete evidence for. Cloud answers never override a field with evidence. */
enum class Evidence { AMOUNT, VERB, MERCHANT, CATEGORY, DATE, TIME, TITLE, KIND, DIRECTION, NAME, FREQUENCY, DUE, BODY, COMPANY, ROLE, STATUS, QUERY }

/**
 * One parsed item with a 0..100 confidence built from concrete evidence.
 * [strongKind] is true when an explicit keyword, verb or phrase named the kind.
 */
class ScoredIntent(val intent: ParsedIntent, val points: Int, val evidence: Set<Evidence>, val strongKind: Boolean) {
    val confidence: Float get() = points / 100f
}

class RuleResult(val items: List<ScoredIntent>) {
    /** Every item cleared the threshold, so no cloud call is needed. */
    val confident: Boolean
        get() = items.isNotEmpty() && items.all { it.intent !is ParsedIntent.Unmatched && it.points >= RuleEngine.THRESHOLD_POINTS }

    val intents: List<ParsedIntent> get() = items.map { it.intent.withConfidence(it.confidence) }
}

/** Rule parse plus a confidence per item. Wraps [QuickParse.parseVoiceIntents]. */
object RuleEngine {
    const val THRESHOLD_POINTS = 70
    const val THRESHOLD = 0.7f

    fun evaluate(
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): RuleResult {
        val learned = LearnedRules.match(transcript, today, now)
        val base = QuickParse.parseVoiceIntents(transcript, today, now)
        val first = base.firstOrNull()
        val useLearned = learned != null && (first == null || first is ParsedIntent.Unmatched)
        val items = if (useLearned) listOf(learned!!) else base
        return RuleResult(items.map { score(it) })
    }

    fun score(intent: ParsedIntent): ScoredIntent {
        val text = intent.rawTranscript
        val lower = text.lowercase()
        val ev = mutableSetOf<Evidence>()
        var pts = 0
        var strong = false
        fun add(e: Evidence, p: Int) { ev += e; pts += p }
        when (intent) {
            is ParsedIntent.Transaction -> {
                if ((intent.amount ?: 0.0) > 0.0) add(Evidence.AMOUNT, 35)
                val currency = QuickParse.MONEY_WORDS.containsMatchIn(lower)
                if (QuickParse.EXPENSE_VERB.containsMatchIn(lower) || QuickParse.INCOME_RE.containsMatchIn(lower) || currency) {
                    add(Evidence.VERB, 15); strong = true
                }
                val hit = QuickParse.merchantHit(text)
                val known = hit != null || QuickParse.keywordCategory(text) != null
                if (hit != null) add(Evidence.MERCHANT, 30) else if (intent.merchant.isNotBlank()) pts += 12
                if (known) add(Evidence.CATEGORY, 20)
                if (known && Evidence.AMOUNT in ev) pts += 15
                if (DateRules.hasDateAnchor(text)) add(Evidence.DATE, 5)
            }
            is ParsedIntent.Event -> {
                val kindPts = eventKindPoints(intent, lower)
                strong = kindPts >= 30
                if (kindPts > 0) add(Evidence.KIND, kindPts)
                val clock = DateRules.hasClock(text)
                if (clock) add(Evidence.TIME, 30)
                if (DateRules.hasDateAnchor(text)) add(Evidence.DATE, if (clock) 20 else 20)
                val title = intent.title.trim()
                if (title.isNotEmpty() && !title.equals(text.trim(), true) && title.any { it.isLetter() }) add(Evidence.TITLE, 20)
                if (intent.repeat != null) pts += 5
                if (intent.kind == CalendarEventKind.ALARM && !clock) pts = minOf(pts, 55)
            }
            is ParsedIntent.Note -> {
                strong = QuickParse.NOTE_START.containsMatchIn(lower) || QuickParse.NOTE_ANY.containsMatchIn(lower) || lower.contains("remember to")
                if (strong) add(Evidence.KIND, 50)
                if (intent.body.isNotBlank()) add(Evidence.BODY, 20)
                if (intent.title.isNotBlank() && intent.body.length < text.length) add(Evidence.TITLE, 20)
            }
            is ParsedIntent.Bill -> {
                if (intent.amount > 0.0) add(Evidence.AMOUNT, 30)
                if (QuickParse.BILL_WORD.containsMatchIn(lower) || QuickParse.DUE_WORD.containsMatchIn(lower) || QuickParse.hasBillFrequency(lower)) {
                    add(Evidence.KIND, 20); strong = true
                }
                if (intent.name.isNotBlank() && intent.name != "Bill") add(Evidence.NAME, 15)
                if (QuickParse.merchantHit(text) != null || QuickParse.isKnownBill(text)) pts += 10
                if (DateRules.hasDateAnchor(text)) add(Evidence.DUE, 15)
                if (QuickParse.hasBillFrequency(lower)) add(Evidence.FREQUENCY, 10)
                if (QuickParse.keywordCategory(text) != null) ev += Evidence.CATEGORY
            }
            is ParsedIntent.Debt -> {
                if (intent.amount > 0.0) add(Evidence.AMOUNT, 30)
                if (QuickParse.DEBT_I_OWE.containsMatchIn(lower) || QuickParse.DEBT_THEY_OWE.containsMatchIn(lower)) {
                    add(Evidence.DIRECTION, 25); strong = true
                }
                if (intent.friendName.isNotBlank() && intent.friendName != "Friend") add(Evidence.NAME, 25)
                if (intent.dueDate != null) add(Evidence.DUE, 10)
            }
            is ParsedIntent.Goal -> {
                if (intent.targetAmount > 0.0) add(Evidence.AMOUNT, 30)
                if (QuickParse.GOAL_HINT.containsMatchIn(lower)) { add(Evidence.KIND, 25); strong = true }
                if (intent.name.isNotBlank() && intent.name != "Goal") add(Evidence.NAME, 25)
            }
            is ParsedIntent.Budget -> {
                if (intent.limit > 0.0) add(Evidence.AMOUNT, 30)
                if (QuickParse.BUDGET_WORD.containsMatchIn(lower) || QuickParse.LIMIT_WORD.containsMatchIn(lower)) { add(Evidence.KIND, 25); strong = true }
                if (intent.category != TransactionCategory.OTHER) add(Evidence.CATEGORY, 30)
            }
            is ParsedIntent.Job -> {
                if (intent.company.isNotBlank() && !intent.company.equals("Company", true)) add(Evidence.COMPANY, 30)
                if (QuickParse.JOB_STRONG.containsMatchIn(lower) || lower.contains("job")) { add(Evidence.KIND, 25); strong = true }
                if (intent.title.isNotBlank() && !intent.title.equals("Role", true) && !intent.title.equals("Interview", true)) add(Evidence.ROLE, 20)
                if (intent.status != JobApplicationStatus.APPLIED || intent.appliedSpoken || lower.contains("interview")) add(Evidence.STATUS, 15)
                if (intent.followUpOn != null || intent.extraDates.isNotBlank()) add(Evidence.DATE, 10)
            }
            is ParsedIntent.Adjust -> {
                add(Evidence.KIND, 40); strong = true
                if (intent.query.isNotBlank()) add(Evidence.QUERY, 30)
                if (intent.kindHint.isNotBlank()) pts += 10
                if (!intent.remove) pts = if (intent.replacement.isNotBlank()) pts + 20 else minOf(pts, 60)
            }
            is ParsedIntent.Unmatched -> Unit
        }
        return ScoredIntent(intent, pts.coerceIn(0, 100), ev, strong)
    }

    private fun eventKindPoints(e: ParsedIntent.Event, lower: String): Int = when {
        QuickParse.TASK_WORD.containsMatchIn(lower) -> 50
        QuickParse.ALARM_WORD.containsMatchIn(lower) || QuickParse.REMIND_WORD.containsMatchIn(lower) ||
            QuickParse.STRONG_EVENT.containsMatchIn(lower) || QuickParse.ROUTINE_WORD.containsMatchIn(lower) ||
            QuickParse.DEADLINE_WORD.containsMatchIn(lower) -> 30
        e.kind == CalendarEventKind.TASK && QuickParse.startsWithTaskVerb(lower) -> 30
        QuickParse.WEAK_EVENT.containsMatchIn(lower) || QuickParse.SOFT_TASK.containsMatchIn(lower) -> 20
        e.repeat != null -> 15
        else -> 0
    }
}

fun ParsedIntent.withConfidence(c: Float): ParsedIntent = when (this) {
    is ParsedIntent.Transaction -> copy(confidence = c)
    is ParsedIntent.Event -> copy(confidence = c)
    is ParsedIntent.Note -> copy(confidence = c)
    is ParsedIntent.Bill -> copy(confidence = c)
    is ParsedIntent.Debt -> copy(confidence = c)
    is ParsedIntent.Goal -> copy(confidence = c)
    is ParsedIntent.Job -> copy(confidence = c)
    is ParsedIntent.Adjust -> copy(confidence = c)
    is ParsedIntent.Budget -> copy(confidence = c)
    is ParsedIntent.Unmatched -> copy(confidence = c)
}

/** Combines a cloud answer with the rule parse: rule fields with evidence stay, the cloud fills the rest. */
object RuleMerge {

    fun merge(rules: List<ScoredIntent>, cloud: List<ParsedIntent>): List<ParsedIntent> {
        if (cloud.isEmpty()) return rules.map { it.intent }
        if (rules.size != cloud.size) return cloud
        return rules.zip(cloud).map { (r, c) -> one(r, c) }
    }

    private fun one(r: ScoredIntent, c: ParsedIntent): ParsedIntent {
        val ri = r.intent
        if (ri is ParsedIntent.Unmatched) return c
        if (c is ParsedIntent.Unmatched) return ri
        if (ri::class != c::class) return if (r.strongKind) ri else c
        val ev = r.evidence
        val raw = ri.rawTranscript
        return when (ri) {
            is ParsedIntent.Transaction -> {
                c as ParsedIntent.Transaction
                ri.copy(
                    amount = if (Evidence.AMOUNT in ev) ri.amount else c.amount ?: ri.amount,
                    merchant = if (Evidence.MERCHANT in ev) ri.merchant else c.merchant.ifBlank { ri.merchant },
                    category = if (Evidence.CATEGORY in ev) ri.category else if (c.category != TransactionCategory.OTHER) c.category else ri.category,
                    type = if (Evidence.VERB in ev) ri.type else c.type,
                    date = if (Evidence.DATE in ev) ri.date else c.date,
                    note = if (ri.note.isBlank() || ri.note == raw.trim()) c.note.takeIf { it.isNotBlank() && it != raw.trim() } ?: ri.note else ri.note,
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Event -> {
                c as ParsedIntent.Event
                val start = when {
                    Evidence.TIME in ev -> ri.startAt
                    Evidence.DATE in ev -> LocalDateTime.of(ri.startAt.toLocalDate(), c.startAt.toLocalTime())
                    else -> c.startAt
                }
                ri.copy(
                    title = if (Evidence.TITLE in ev) ri.title else c.title.ifBlank { ri.title },
                    notes = c.notes.ifBlank { ri.notes },
                    startAt = start,
                    endAt = if (Evidence.TIME in ev && ri.endAt != null) ri.endAt else c.endAt?.takeIf { it.isAfter(start) } ?: ri.endAt?.takeIf { it.isAfter(start) },
                    kind = if (r.strongKind) ri.kind else c.kind,
                    repeat = ri.repeat ?: c.repeat,
                    reminders = ri.reminders.ifEmpty { c.reminders },
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Note -> {
                c as ParsedIntent.Note
                ri.copy(
                    title = if (Evidence.TITLE in ev) ri.title else c.title.ifBlank { ri.title },
                    body = if (Evidence.BODY in ev) ri.body else c.body.ifBlank { ri.body },
                    tags = ri.tags.ifEmpty { c.tags },
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Bill -> {
                c as ParsedIntent.Bill
                ri.copy(
                    name = if (Evidence.NAME in ev) ri.name else c.name.ifBlank { ri.name },
                    amount = if (Evidence.AMOUNT in ev) ri.amount else c.amount,
                    frequency = if (Evidence.FREQUENCY in ev) ri.frequency else c.frequency,
                    nextDueDate = if (Evidence.DUE in ev) ri.nextDueDate else c.nextDueDate,
                    category = if (Evidence.CATEGORY in ev) ri.category else c.category,
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Debt -> {
                c as ParsedIntent.Debt
                ri.copy(
                    friendName = if (Evidence.NAME in ev) ri.friendName else c.friendName.ifBlank { ri.friendName },
                    amount = if (Evidence.AMOUNT in ev) ri.amount else c.amount,
                    direction = if (Evidence.DIRECTION in ev) ri.direction else c.direction,
                    dueDate = ri.dueDate ?: c.dueDate,
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Goal -> {
                c as ParsedIntent.Goal
                ri.copy(
                    name = if (Evidence.NAME in ev) ri.name else c.name.ifBlank { ri.name },
                    targetAmount = if (Evidence.AMOUNT in ev) ri.targetAmount else c.targetAmount,
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Budget -> {
                c as ParsedIntent.Budget
                ri.copy(
                    category = if (Evidence.CATEGORY in ev) ri.category else c.category,
                    limit = if (Evidence.AMOUNT in ev) ri.limit else c.limit,
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Job -> {
                c as ParsedIntent.Job
                ri.copy(
                    company = if (Evidence.COMPANY in ev) ri.company else c.company.ifBlank { ri.company },
                    title = if (Evidence.ROLE in ev) ri.title else c.title.ifBlank { ri.title },
                    status = if (Evidence.STATUS in ev) ri.status else c.status,
                    appliedOn = if (ri.appliedSpoken) ri.appliedOn else c.appliedOn,
                    followUpOn = ri.followUpOn ?: c.followUpOn,
                    location = ri.location.ifBlank { c.location },
                    source = ri.source.ifBlank { c.source },
                    url = ri.url.ifBlank { c.url },
                    extraDates = ri.extraDates.ifBlank { c.extraDates },
                    confidence = maxOf(ri.confidence, c.confidence),
                )
            }
            is ParsedIntent.Adjust -> {
                c as ParsedIntent.Adjust
                ri.copy(
                    query = ri.query.ifBlank { c.query },
                    kindHint = ri.kindHint.ifBlank { c.kindHint },
                    replacement = ri.replacement.ifBlank { c.replacement },
                )
            }
            is ParsedIntent.Unmatched -> c
        }
    }
}

/** What [VoiceIntentRouter] returns: the items, and which path produced them. */
class RoutedIntents(val items: List<ParsedIntent>, val source: IntentSource, val cloudCalled: Boolean)

/**
 * On-phone model first. Rules fill gaps and keep fields they have evidence for.
 * The cloud runs only when the phone model has no answer and the rules are unsure.
 */
object VoiceIntentRouter {

    suspend fun route(
        transcript: String,
        cloudEnabled: Boolean,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
        cloud: suspend () -> List<ParsedIntent>,
        local: suspend () -> List<ParsedIntent> = { emptyList() },
    ): RoutedIntents {
        val onDevice = try {
            local().filter { it !is ParsedIntent.Unmatched }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val rules = RuleEngine.evaluate(transcript, today, now)
        if (onDevice.isNotEmpty()) {
            val merged = if (rules.items.any { it.intent !is ParsedIntent.Unmatched }) {
                RuleMerge.merge(rules.items, onDevice)
            } else {
                onDevice
            }
            return RoutedIntents(merged, IntentSource.ON_DEVICE, cloudCalled = false)
        }
        if (rules.confident || !cloudEnabled) return RoutedIntents(rules.intents, IntentSource.RULES, cloudCalled = false)
        val answer = try {
            cloud()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        if (answer.isEmpty()) return RoutedIntents(rules.intents, IntentSource.RULES, cloudCalled = true)
        return RoutedIntents(RuleMerge.merge(rules.items, answer), IntentSource.AI, cloudCalled = true)
    }
}
