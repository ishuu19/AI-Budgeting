package com.ledgerai.app.presentation.screens.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.ai.QuickParse
import com.ledgerai.app.data.ai.VoiceResultKind
import com.ledgerai.app.data.ai.resultKind
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.schedule.labelForMinutesBefore
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.TimePickChip
import com.ledgerai.app.presentation.components.label
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DateTimeShort = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val TimeShort = DateTimeFormatter.ofPattern("h:mm a")
internal val DateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private fun ruleLabel(rule: String) = rule.lowercase().replaceFirstChar { it.uppercase() }

private val AlarmRepeats = listOf(0 to "Once", 62 to "Weekdays", 127 to "Daily")
private val DayBits = listOf(1 to "Sun", 2 to "Mon", 4 to "Tue", 8 to "Wed", 16 to "Thu", 32 to "Fri", 64 to "Sat")

private fun repeatLabel(mask: Int) = AlarmRepeats.firstOrNull { it.first == mask }?.second
    ?: DayBits.filter { mask and it.first != 0 }.joinToString(" ") { it.second }

private val ReminderOptions = listOf(0, 10, 60, 180, 1440)
private val DefaultReminderMask = (1 shl 1) or (1 shl 2) or (1 shl 3)

private fun reminderChip(minutes: Int) = when (minutes) {
    0 -> "At time"
    10 -> "10 min"
    60 -> "1 hour"
    180 -> "3 hours"
    else -> "1 day"
}

private val DurationOptions = listOf(15, 30, 45, 60, 90, 120, 180)

private fun durationLabel(minutes: Int) = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "${minutes / 60}.5 hours"
}

private fun typeLabel(type: TransactionType) = type.name.lowercase().replaceFirstChar { it.uppercase() }

/** Headline and one detail line for a card. */
internal fun summarize(intent: ParsedIntent): Pair<String, String?> = when (intent) {
    is ParsedIntent.Transaction -> Pair(
        if (intent.amount == null || intent.amount <= 0.0) "${typeLabel(intent.type)} · Add amount"
        else "${typeLabel(intent.type)} · ${money(intent.amount)}",
        listOf(intent.category.displayName, intent.merchant).filter { it.isNotBlank() }.joinToString(" · ")
    )
    is ParsedIntent.Event -> when (intent.kind) {
        CalendarEventKind.ALARM -> Pair(
            "Alarm · ${intent.startAt.format(TimeShort)}",
            "${intent.title} · ${repeatLabel(intent.alarmRepeatDays)}"
        )
        CalendarEventKind.ROUTINE -> Pair(
            intent.title,
            "${intent.startAt.format(TimeShort)} · ${intent.repeat?.frequency?.name?.let(::ruleLabel) ?: "Once"}"
        )
        else -> Pair(intent.title, intent.startAt.format(DateTimeShort))
    }
    is ParsedIntent.Note -> Pair(intent.title.ifBlank { "Untitled" }, intent.body.ifBlank { null })
    is ParsedIntent.Bill -> Pair(
        "${money(intent.amount)} · ${intent.name}",
        "${intent.frequency.displayName} · Due ${intent.nextDueDate.format(DateShort)}"
    )
    is ParsedIntent.Debt -> Pair(
        "${money(intent.amount)} · ${intent.friendName}",
        listOfNotNull(
            if (intent.direction == DebtDirection.I_OWE) "I owe" else "They owe",
            intent.dueDate?.let { "Due ${it.format(DateShort)}" }
        ).joinToString(" · ")
    )
    is ParsedIntent.Goal -> Pair(intent.name, "Target ${money(intent.targetAmount)}")
    is ParsedIntent.Budget -> Pair(
        intent.category.displayName,
        if (intent.limit > 0.0) "${money(intent.limit)} a month" else "Add limit"
    )
    is ParsedIntent.Job -> Pair(
        intent.company,
        listOfNotNull(intent.title, intent.followUpOn?.format(DateShort)).joinToString(" · ")
    )
    is ParsedIntent.Unmatched -> Pair("\"${intent.rawTranscript}\"", null)
}

internal fun canSave(intent: ParsedIntent): Boolean = when (intent) {
    is ParsedIntent.Transaction -> (intent.amount ?: 0.0) > 0.0
    is ParsedIntent.Event -> intent.title.isNotBlank() || intent.kind == CalendarEventKind.ALARM
    is ParsedIntent.Note -> intent.title.isNotBlank() || intent.body.isNotBlank()
    is ParsedIntent.Bill -> intent.name.isNotBlank() && intent.amount > 0.0
    is ParsedIntent.Debt -> intent.friendName.isNotBlank() && intent.amount > 0.0
    is ParsedIntent.Goal -> intent.name.isNotBlank() && intent.targetAmount > 0.0
    is ParsedIntent.Budget -> intent.limit > 0.0
    is ParsedIntent.Job -> intent.company.isNotBlank()
    is ParsedIntent.Unmatched -> intent.rawTranscript.isNotBlank()
}

/** Result label chip used on cards and history rows. */
@Composable
internal fun KindPill(kind: VoiceResultKind, onDark: Boolean = true) {
    Text(
        kind.label,
        style = MaterialTheme.typography.labelMedium,
        color = if (onDark) L.BoxDeep else L.OnBox,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (onDark) L.Gold else L.Box)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun CardAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(L.RadiusSm))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = L.Box)
    }
}

/**
 * One result to confirm. Header names the result, chips below change date, time, duration and category,
 * Edit opens the full sheet. Nothing is saved until Save.
 */
@Composable
internal fun ConfirmCard(
    card: VoiceCard,
    onChange: (ParsedIntent, remindersEdited: Boolean?) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intent = card.intent
    val unmatched = intent is ParsedIntent.Unmatched
    val kind = intent.resultKind()
    val (headline, sub) = summarize(intent)
    var editing by rememberSaveable(card.key) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(L.Radius))
            .border(1.5.dp, L.Box, RoundedCornerShape(L.Radius))
            .semantics(mergeDescendants = false) {}
    ) {
        Column(
            Modifier.fillMaxWidth().background(L.Box).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (unmatched) {
                Text("DIDN'T CATCH", style = MaterialTheme.typography.labelSmall, color = L.Gold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) { KindPill(kind) }
            }
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = L.OnBox,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(L.Page).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CardPickers(intent) { onChange(it, null) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    LButton(
                        if (unmatched) "Keep as note" else "Save",
                        onClick = onSave,
                        enabled = canSave(intent)
                    )
                }
                CardAction("Edit", onClick = { editing = true })
                CardAction("Discard", onClick = onDiscard)
            }
        }
    }

    if (editing) {
        val dismiss = { editing = false }
        when (intent) {
            is ParsedIntent.Transaction -> TransactionDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Event -> EventDraftSheet(intent, card.remindersEdited, dismiss) { updated, edited ->
                onChange(updated, edited)
                dismiss()
            }
            is ParsedIntent.Note -> NoteDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Bill -> BillDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Debt -> DebtDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Goal -> GoalDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Budget -> BudgetDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Job -> JobDraftSheet(intent, dismiss) { onChange(it, null); dismiss() }
            is ParsedIntent.Unmatched -> RedoSheet(
                initialTranscript = intent.rawTranscript,
                initialKind = null,
                onDismiss = dismiss,
                onApply = { text, picked ->
                    dismiss()
                    onChange(
                        if (picked != null) QuickParse.asKind(picked, text) else QuickParse.parseVoiceIntent(text),
                        false
                    )
                }
            )
        }
    }
}

/** Date, time, duration and category as pickers, right on the card. */
@Composable
private fun CardPickers(intent: ParsedIntent, onChange: (ParsedIntent) -> Unit) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    when (intent) {
        is ParsedIntent.Transaction -> {
            ChipsRow {
                DatePickChip(intent.date, selected = true, onDate = { onChange(intent.copy(date = it)) })
                LChip(intent.category.displayName, selected = true, onClick = { sheet = "category" })
            }
            if (sheet == "category") {
                CategorySheet(intent.category, { sheet = null }) { onChange(intent.copy(category = it)); sheet = null }
            }
        }
        is ParsedIntent.Event -> {
            val timed = intent.kind != CalendarEventKind.TASK && intent.kind != CalendarEventKind.ALARM
            ChipsRow {
                DatePickChip(
                    intent.startAt.toLocalDate(),
                    selected = true,
                    onDate = { onChange(intent.withStart(LocalDateTime.of(it, intent.startAt.toLocalTime()))) }
                )
                TimePickChip(
                    intent.startAt.toLocalTime(),
                    onTime = { onChange(intent.withStart(LocalDateTime.of(intent.startAt.toLocalDate(), it))) }
                )
                if (timed) {
                    val minutes = intent.durationMinutes()
                    LChip(durationLabel(minutes), selected = true, onClick = { sheet = "duration" })
                }
            }
            if (sheet == "duration") {
                PickSheet(
                    title = "Duration",
                    options = DurationOptions,
                    selected = intent.durationMinutes(),
                    label = ::durationLabel,
                    onDismiss = { sheet = null },
                    onPick = { m -> onChange(intent.copy(endAt = intent.startAt.plusMinutes(m.toLong()))); sheet = null }
                )
            }
        }
        is ParsedIntent.Bill -> {
            ChipsRow {
                DatePickChip(
                    intent.nextDueDate,
                    selected = true,
                    onDate = { onChange(intent.copy(nextDueDate = it)) },
                    label = "Due ${intent.nextDueDate.format(DateShort)}"
                )
                LChip(intent.category.displayName, selected = true, onClick = { sheet = "category" })
            }
            if (sheet == "category") {
                CategorySheet(intent.category, { sheet = null }) { onChange(intent.copy(category = it)); sheet = null }
            }
        }
        is ParsedIntent.Debt -> ChipsRow {
            DatePickChip(
                intent.dueDate ?: LocalDate.now(),
                selected = intent.dueDate != null,
                onDate = { onChange(intent.copy(dueDate = it)) },
                label = intent.dueDate?.let { "Due ${it.format(DateShort)}" } ?: "No due date"
            )
            if (intent.dueDate != null) LChip("Clear", selected = false, onClick = { onChange(intent.copy(dueDate = null)) })
        }
        is ParsedIntent.Budget -> {
            ChipsRow { LChip(intent.category.displayName, selected = true, onClick = { sheet = "category" }) }
            if (sheet == "category") {
                CategorySheet(intent.category, { sheet = null }) { onChange(intent.copy(category = it)); sheet = null }
            }
        }
        is ParsedIntent.Job -> ChipsRow {
            DatePickChip(
                intent.followUpOn ?: intent.appliedOn,
                selected = true,
                onDate = { onChange(intent.copy(followUpOn = it)) },
                label = "Follow up ${ (intent.followUpOn ?: intent.appliedOn).format(DateShort) }"
            )
        }
        else -> Unit
    }
}

private fun ParsedIntent.Event.durationMinutes(): Int =
    endAt?.let { Duration.between(startAt, it).toMinutes().toInt() }?.takeIf { it > 0 } ?: 60

/** Moves the event to [start] and keeps its length. */
private fun ParsedIntent.Event.withStart(start: LocalDateTime): ParsedIntent.Event {
    val length = endAt?.let { Duration.between(startAt, it) }
    val repeatDays = if (kind == CalendarEventKind.ROUTINE && repeat?.frequency == com.ledgerai.app.domain.model.RecurrenceFrequency.WEEKLY &&
        repeat.weekDays.size == 1
    ) {
        repeat.copy(weekDays = setOf(start.dayOfWeek.value))
    } else repeat
    return copy(startAt = start, endAt = length?.let { start.plus(it) }, repeat = repeatDays)
}

// --- pick sheets ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> PickSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit
) {
    LSheet(title = title, onDismiss = onDismiss, primary = "Done", onPrimary = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                LChip(label(option), selected = option == selected, onClick = { onPick(option) })
            }
        }
    }
}

@Composable
private fun CategorySheet(
    selected: TransactionCategory,
    onDismiss: () -> Unit,
    onPick: (TransactionCategory) -> Unit
) = PickSheet("Category", TransactionCategory.entries.toList(), selected, { it.displayName }, onDismiss, onPick)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: TransactionCategory, onPick: (TransactionCategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionCategory.entries.forEach { c ->
            LChip(c.displayName, selected = selected == c, onClick = { onPick(c) })
        }
    }
}

private val DecimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

private fun Double.asInput(): String =
    if (this == 0.0) "" else toBigDecimal().stripTrailingZeros().toPlainString()

private fun String.asAmount(): Double? = replace(',', '.').toDoubleOrNull()

// --- edit sheets: they change the card, they do not save ----------------------------------------

@Composable
private fun TransactionDraftSheet(
    parsed: ParsedIntent.Transaction,
    onDismiss: () -> Unit,
    onApply: (ParsedIntent.Transaction) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf((parsed.amount ?: 0.0).asInput()) }
    var merchant by rememberSaveable { mutableStateOf(parsed.merchant) }
    var note by rememberSaveable { mutableStateOf(parsed.note) }
    var type by rememberSaveable { mutableStateOf(parsed.type) }
    var category by rememberSaveable { mutableStateOf(parsed.category) }
    var date by rememberSaveable { mutableStateOf(parsed.date) }
    val value = amount.asAmount()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            onApply(parsed.copy(amount = value, type = type, category = category, merchant = merchant.trim(), note = note, date = date))
        },
        primaryEnabled = value != null && value > 0.0
    ) {
        ChipsRow {
            TransactionType.entries.forEach { t ->
                LChip(typeLabel(t), selected = type == t, onClick = { type = t })
            }
        }
        LField(value = amount, onValueChange = { amount = it }, label = "Amount", keyboardOptions = DecimalKeyboard)
        LField(value = merchant, onValueChange = { merchant = it }, label = "Merchant")
        CategoryChips(category) { category = it }
        ChipsRow { DatePickChip(date, selected = true, onDate = { date = it }) }
        LField(value = note, onValueChange = { note = it }, label = "Note", singleLine = false)
    }
}

@Composable
private fun EventDraftSheet(
    parsed: ParsedIntent.Event,
    remindersEdited: Boolean,
    onDismiss: () -> Unit,
    onApply: (ParsedIntent.Event, remindersEdited: Boolean) -> Unit
) {
    var kind by rememberSaveable { mutableStateOf(parsed.kind) }
    var title by rememberSaveable { mutableStateOf(if (parsed.kind == CalendarEventKind.ALARM && parsed.title == "Alarm") "" else parsed.title) }
    var notes by rememberSaveable { mutableStateOf(parsed.notes) }
    var date by rememberSaveable { mutableStateOf(parsed.startAt.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(parsed.startAt.toLocalTime()) }
    var minutes by rememberSaveable { mutableStateOf(parsed.durationMinutes()) }
    var alarmMask by rememberSaveable { mutableStateOf(parsed.alarmRepeatDays) }
    var custom by rememberSaveable { mutableStateOf(parsed.alarmRepeatDays !in AlarmRepeats.map { it.first }) }
    var rule by rememberSaveable {
        mutableStateOf(
            when {
                parsed.repeat?.frequency == com.ledgerai.app.domain.model.RecurrenceFrequency.DAILY -> "DAILY"
                parsed.repeat?.weekDays?.size == 5 -> "WEEKDAYS"
                parsed.repeat != null -> "WEEKLY"
                else -> "DAILY"
            }
        )
    }
    var touched by rememberSaveable { mutableStateOf(remindersEdited) }
    var remMask by rememberSaveable {
        mutableStateOf(
            if (!remindersEdited && parsed.reminders.isEmpty()) DefaultReminderMask
            else ReminderOptions.indices.filter { i -> parsed.reminders.any { it.offsetMinutes == ReminderOptions[i] } }
                .fold(0) { acc, i -> acc or (1 shl i) }
        )
    }

    val isAlarm = kind == CalendarEventKind.ALARM
    val timed = kind == CalendarEventKind.EVENT || kind == CalendarEventKind.EXAM || kind == CalendarEventKind.ROUTINE

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            val start = LocalDateTime.of(date, time)
            val reminders = if (touched && !isAlarm) {
                ReminderOptions.indices.filter { remMask and (1 shl it) != 0 }.map {
                    EventReminder(label = labelForMinutesBefore(ReminderOptions[it].toLong()), offsetMinutes = ReminderOptions[it])
                }
            } else parsed.reminders
            onApply(
                parsed.copy(
                    title = if (isAlarm) title.trim().ifBlank { "Alarm" } else title.trim(),
                    notes = notes,
                    kind = kind,
                    startAt = start,
                    endAt = if (timed) start.plusMinutes(minutes.toLong()) else null,
                    repeat = when {
                        isAlarm -> if (alarmMask == 0) null else QuickParse.repeatFromAlarmMask(alarmMask)
                        kind == CalendarEventKind.ROUTINE -> QuickParse.repeatFromRule(rule, date)
                        else -> null
                    },
                    reminders = reminders
                ),
                touched && !isAlarm
            )
        },
        primaryEnabled = isAlarm || title.isNotBlank()
    ) {
        ChipsRow {
            listOf(
                CalendarEventKind.TASK, CalendarEventKind.EVENT, CalendarEventKind.EXAM,
                CalendarEventKind.ALARM, CalendarEventKind.ROUTINE
            ).forEach { k -> LChip(k.label(), selected = kind == k, onClick = { kind = k }) }
        }
        LField(value = title, onValueChange = { title = it }, label = if (isAlarm) "Label" else "Title")
        ChipsRow {
            DatePickChip(date, selected = true, onDate = { date = it })
            TimePickChip(time, onTime = { time = it })
        }
        if (timed) {
            ChipsRow {
                DurationOptions.forEach { m -> LChip(durationLabel(m), selected = minutes == m, onClick = { minutes = m }) }
            }
        }
        if (isAlarm) {
            ChipsRow {
                AlarmRepeats.forEach { (mask, name) ->
                    LChip(name, selected = !custom && alarmMask == mask, onClick = { alarmMask = mask; custom = false })
                }
                LChip("Custom", selected = custom, onClick = { custom = true })
            }
            if (custom) {
                ChipsRow {
                    DayBits.forEach { (bit, name) ->
                        val on = alarmMask and bit != 0
                        LChip(name, selected = on, onClick = { alarmMask = alarmMask xor bit })
                    }
                }
            }
        }
        if (kind == CalendarEventKind.ROUTINE) {
            ChipsRow {
                listOf("DAILY", "WEEKDAYS", "WEEKLY").forEach { r -> LChip(ruleLabel(r), selected = rule == r, onClick = { rule = r }) }
            }
        }
        if (!isAlarm) {
            ChipsRow {
                ReminderOptions.forEachIndexed { i, m ->
                    val on = remMask and (1 shl i) != 0
                    LChip(reminderChip(m), selected = on, onClick = { remMask = remMask xor (1 shl i); touched = true })
                }
            }
            LField(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
        }
    }
}

@Composable
private fun NoteDraftSheet(parsed: ParsedIntent.Note, onDismiss: () -> Unit, onApply: (ParsedIntent.Note) -> Unit) {
    var title by rememberSaveable { mutableStateOf(parsed.title) }
    var body by rememberSaveable { mutableStateOf(parsed.body) }
    var tags by rememberSaveable { mutableStateOf(parsed.tags.joinToString(", ")) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            onApply(parsed.copy(title = title, body = body, tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
        },
        primaryEnabled = title.isNotBlank() || body.isNotBlank()
    ) {
        LField(value = title, onValueChange = { title = it }, label = "Title")
        LField(value = body, onValueChange = { body = it }, label = "Note", singleLine = false, minLines = 3)
        LField(value = tags, onValueChange = { tags = it }, label = "Tags")
    }
}

@Composable
private fun BillDraftSheet(parsed: ParsedIntent.Bill, onDismiss: () -> Unit, onApply: (ParsedIntent.Bill) -> Unit) {
    var name by rememberSaveable { mutableStateOf(parsed.name) }
    var amount by rememberSaveable { mutableStateOf(parsed.amount.asInput()) }
    var frequency by rememberSaveable { mutableStateOf(parsed.frequency) }
    var due by rememberSaveable { mutableStateOf(parsed.nextDueDate) }
    var category by rememberSaveable { mutableStateOf(parsed.category) }
    val value = amount.asAmount()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            if (value != null) onApply(parsed.copy(name = name.trim(), amount = value, frequency = frequency, nextDueDate = due, category = category))
        },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0
    ) {
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(value = amount, onValueChange = { amount = it }, label = "Amount", keyboardOptions = DecimalKeyboard)
        ChipsRow {
            BillFrequency.entries.forEach { f -> LChip(f.displayName, selected = frequency == f, onClick = { frequency = f }) }
        }
        CategoryChips(category) { category = it }
        ChipsRow { DatePickChip(due, selected = true, onDate = { due = it }, label = "Due ${due.format(DateShort)}") }
    }
}

@Composable
private fun DebtDraftSheet(parsed: ParsedIntent.Debt, onDismiss: () -> Unit, onApply: (ParsedIntent.Debt) -> Unit) {
    var name by rememberSaveable { mutableStateOf(parsed.friendName) }
    var amount by rememberSaveable { mutableStateOf(parsed.amount.asInput()) }
    var direction by rememberSaveable { mutableStateOf(parsed.direction) }
    var due by rememberSaveable { mutableStateOf(parsed.dueDate) }
    val value = amount.asAmount()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            if (value != null) onApply(parsed.copy(friendName = name.trim(), amount = value, direction = direction, dueDate = due))
        },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0
    ) {
        ChipsRow {
            LChip("They owe", selected = direction == DebtDirection.THEY_OWE, onClick = { direction = DebtDirection.THEY_OWE })
            LChip("I owe", selected = direction == DebtDirection.I_OWE, onClick = { direction = DebtDirection.I_OWE })
        }
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(value = amount, onValueChange = { amount = it }, label = "Amount", keyboardOptions = DecimalKeyboard)
        ChipsRow {
            DatePickChip(
                due ?: LocalDate.now(),
                selected = due != null,
                onDate = { due = it },
                label = due?.let { "Due ${it.format(DateShort)}" } ?: "No due date"
            )
            if (due != null) LChip("Clear", selected = false, onClick = { due = null })
        }
    }
}

@Composable
private fun JobDraftSheet(parsed: ParsedIntent.Job, onDismiss: () -> Unit, onApply: (ParsedIntent.Job) -> Unit) {
    var company by rememberSaveable { mutableStateOf(parsed.company) }
    var title by rememberSaveable { mutableStateOf(parsed.title) }
    var status by rememberSaveable { mutableStateOf(parsed.status.name) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = {
            onApply(
                parsed.copy(
                    company = company.trim(),
                    title = title.trim().ifBlank { "Role" },
                    status = JobApplicationStatus.valueOf(status)
                )
            )
        },
        primaryEnabled = company.isNotBlank()
    ) {
        LField(value = company, onValueChange = { company = it }, label = "Company")
        LField(value = title, onValueChange = { title = it }, label = "Role")
        ChipsRow {
            JobApplicationStatus.entries.forEach { s ->
                LChip(s.name.lowercase().replaceFirstChar { it.uppercase() }, selected = s.name == status, onClick = { status = s.name })
            }
        }
    }
}

@Composable
private fun GoalDraftSheet(parsed: ParsedIntent.Goal, onDismiss: () -> Unit, onApply: (ParsedIntent.Goal) -> Unit) {
    var name by rememberSaveable { mutableStateOf(parsed.name) }
    var amount by rememberSaveable { mutableStateOf(parsed.targetAmount.asInput()) }
    val value = amount.asAmount()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = { value?.let { onApply(parsed.copy(name = name.trim(), targetAmount = it)) } },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0
    ) {
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(value = amount, onValueChange = { amount = it }, label = "Target", keyboardOptions = DecimalKeyboard)
    }
}

@Composable
private fun BudgetDraftSheet(parsed: ParsedIntent.Budget, onDismiss: () -> Unit, onApply: (ParsedIntent.Budget) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(parsed.limit.asInput()) }
    var category by rememberSaveable { mutableStateOf(parsed.category) }
    val value = amount.asAmount()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = { value?.let { onApply(parsed.copy(category = category, limit = it)) } },
        primaryEnabled = value != null && value > 0.0
    ) {
        CategoryChips(category) { category = it }
        LField(value = amount, onValueChange = { amount = it }, label = "Monthly limit", keyboardOptions = DecimalKeyboard)
    }
}
