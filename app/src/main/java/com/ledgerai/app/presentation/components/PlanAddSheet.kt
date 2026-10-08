package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.HabitCategory
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.schedule.AlarmDays
import com.ledgerai.app.domain.schedule.EventReminderRules
import com.ledgerai.app.domain.schedule.allBeforeEventOptions
import com.ledgerai.app.domain.schedule.defaultEventReminders
import com.ledgerai.app.domain.schedule.labelForMinutesBefore
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.service.AlarmToneHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** What the one Add sheet can create. */
enum class AddKind(val label: String) {
    Event("Event"), Task("Task"), Exam("Exam"), Class("Class"),
    Alarm("Alarm"), Routine("Routine"), Study("Study"), Habit("Habit");

    val calendarKind: CalendarEventKind?
        get() = when (this) {
            Event -> CalendarEventKind.EVENT
            Task -> CalendarEventKind.TASK
            Exam -> CalendarEventKind.EXAM
            Class -> CalendarEventKind.CLASS
            Alarm -> CalendarEventKind.ALARM
            Routine -> CalendarEventKind.ROUTINE
            else -> null
        }

    companion object {
        fun of(kind: CalendarEventKind): AddKind = when (kind) {
            CalendarEventKind.EVENT, CalendarEventKind.PERSONAL -> Event
            CalendarEventKind.TASK -> Task
            CalendarEventKind.EXAM -> Exam
            CalendarEventKind.CLASS -> Class
            CalendarEventKind.ALARM -> Alarm
            CalendarEventKind.ROUTINE -> Routine
        }
    }
}

private val ErrorInk = Color(0xFFB3261E)

private val DateSaver = Saver<LocalDate, Long>(save = { it.toEpochDay() }, restore = { LocalDate.ofEpochDay(it) })
private val OptDateSaver = Saver<LocalDate?, Long>(
    save = { it?.toEpochDay() ?: Long.MIN_VALUE },
    restore = { if (it == Long.MIN_VALUE) null else LocalDate.ofEpochDay(it) }
)
private val DateSetSaver = Saver<Set<LocalDate>, ArrayList<Long>>(
    save = { s -> ArrayList(s.map { it.toEpochDay() }) },
    restore = { l -> l.map { LocalDate.ofEpochDay(it) }.toSet() }
)
private val IntSetSaver = Saver<Set<Int>, ArrayList<Int>>(save = { ArrayList(it) }, restore = { it.toSet() })
private val IntListSaver = Saver<List<Int>, ArrayList<Int>>(save = { ArrayList(it) }, restore = { it.toList() })

@Composable
private fun rememberDate(initial: LocalDate): MutableState<LocalDate> =
    rememberSaveable(stateSaver = DateSaver) { mutableStateOf(initial) }

@Composable
private fun rememberOptDate(initial: LocalDate?): MutableState<LocalDate?> =
    rememberSaveable(stateSaver = OptDateSaver) { mutableStateOf(initial) }

private fun minutesOf(t: LocalTime): Int = t.hour * 60 + t.minute
private fun timeOf(min: Int): LocalTime = LocalTime.of((min / 60).coerceIn(0, 23), (min % 60).coerceIn(0, 59))

private val HourChoices = listOf(1, 2, 3, 4, 5, 6, 8, 10)
private val DurationChoices = listOf(15, 30, 45, 60, 90)

/** Monday-first weekday chips: label, ISO day (1..7), alarm bit. */
private val WeekChips: List<Triple<String, Int, Int>> = DayOfWeek.entries.map {
    Triple(it.getDisplayName(TextStyle.SHORT, Locale.getDefault()), it.value, AlarmDays.bit(it))
}

private fun fullDay(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.FULL, Locale.getDefault())

/**
 * The one Add sheet. Choose the kind first, then only that kind's fields show.
 * Editing hides the kind chips. [existing] must be the master event, not an expanded occurrence.
 */
@Composable
fun PlanAddSheet(
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSaveEvent: (CalendarEvent, leaveByEnabled: Boolean) -> Unit,
    onFindTime: (topic: String, hours: Double, deadline: LocalDate?) -> Unit,
    onSaveHabit: suspend (Habit) -> Result<Unit>,
    existing: CalendarEvent? = null,
    existingHabit: Habit? = null,
    draft: CalendarEvent? = null,
    defaultKind: AddKind = AddKind.Event,
    initialLeaveBy: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    val seed = existing ?: draft
    val editing = existing != null || existingHabit != null
    val initialKind = when {
        existingHabit != null -> AddKind.Habit
        seed != null -> AddKind.of(seed.kind)
        else -> defaultKind
    }
    var kindName by rememberSaveable { mutableStateOf(initialKind.name) }
    val kind = AddKind.valueOf(kindName)
    val isAlarm = kind == AddKind.Alarm
    val isTask = kind == AddKind.Task

    var title by rememberSaveable { mutableStateOf(existingHabit?.title ?: seed?.title.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(seed?.notes.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(seed?.location.orEmpty()) }
    var links by rememberSaveable { mutableStateOf(seed?.links.orEmpty()) }
    var date by rememberDate(seed?.startAt?.toLocalDate() ?: defaultDate)
    var hasDate by rememberSaveable { mutableStateOf(seed?.hasDate ?: true) }
    // For tasks, allDay means "no time".
    var noTime by rememberSaveable { mutableStateOf(seed?.allDay ?: (initialKind == AddKind.Task)) }
    var allDay by rememberSaveable { mutableStateOf(seed?.allDay ?: false) }
    var startMin by rememberSaveable {
        mutableStateOf(
            existingHabit?.let { minutesOf(it.startTime) }
                ?: seed?.startAt?.let { minutesOf(it.toLocalTime()) }
                ?: if (initialKind == AddKind.Alarm) minutesOf(LocalTime.now().plusMinutes(1)) else 9 * 60
        )
    }
    var endMin by rememberSaveable {
        mutableStateOf(
            seed?.takeIf { it.endAt.isAfter(it.startAt) }?.let { minutesOf(it.endAt.toLocalTime()) }
                ?: ((startMin + 60) % (24 * 60))
        )
    }
    var alarmDays by rememberSaveable { mutableStateOf(seed?.alarmRepeatDays ?: 0) }
    var toneUri by rememberSaveable {
        mutableStateOf(seed?.alarmToneUri ?: AlarmToneHelper.builtInTones.first().uriString)
    }
    var frequencyName by rememberSaveable {
        mutableStateOf(
            (seed?.recurrence?.frequency?.takeIf { initialKind != AddKind.Alarm }
                ?: if (initialKind == AddKind.Routine) RecurrenceFrequency.DAILY else RecurrenceFrequency.NONE).name
        )
    }
    val frequency = RecurrenceFrequency.valueOf(frequencyName)
    var interval by rememberSaveable { mutableStateOf(seed?.recurrence?.interval ?: 1) }
    var weekDays by rememberSaveable(stateSaver = IntSetSaver) {
        mutableStateOf(
            seed?.recurrence?.weekDays?.ifEmpty { null }
                ?: setOf((seed?.startAt?.toLocalDate() ?: defaultDate).dayOfWeek.value)
        )
    }
    var specificDates by rememberSaveable(stateSaver = DateSetSaver) {
        mutableStateOf(seed?.recurrence?.specificDates ?: emptySet())
    }
    var untilDate by rememberOptDate(seed?.recurrence?.until)
    val seedReminders = seed?.reminders.orEmpty()
    var reminderOffsets by rememberSaveable(stateSaver = IntListSaver) {
        mutableStateOf(
            when {
                seed != null && (existing != null || seedReminders.isNotEmpty()) ->
                    seedReminders.mapNotNull { it.offsetMinutes }
                else -> defaultEventReminders().mapNotNull { it.offsetMinutes }
            }
        )
    }
    var leaveBy by rememberSaveable { mutableStateOf(initialLeaveBy) }

    // Study
    var hours by rememberSaveable { mutableStateOf(2) }
    var deadline by rememberOptDate(null)

    // Habit
    var habitDays by rememberSaveable { mutableStateOf(existingHabit?.daysMask ?: 0) }
    var habitMinutes by rememberSaveable { mutableStateOf(existingHabit?.durationMinutes ?: 30) }
    var nudges by rememberSaveable { mutableStateOf(existingHabit?.nudgeEnabled ?: true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val canRemind = kind.calendarKind?.let { EventReminderRules.supportsReminders(it) } == true
    val timed = when {
        isTask -> hasDate && !noTime
        else -> !allDay
    }
    val hasEnd = kind == AddKind.Event || kind == AddKind.Exam || kind == AddKind.Class || kind == AddKind.Routine

    fun buildEvent(): CalendarEvent {
        val calKind = kind.calendarKind ?: CalendarEventKind.EVENT
        val allDayFlag = when {
            isAlarm -> false
            isTask -> noTime
            else -> allDay
        }
        val startTime = if (allDayFlag) LocalTime.MIDNIGHT else timeOf(startMin)
        val start = LocalDateTime.of(date, startTime)
        val end = when {
            !hasEnd -> start
            allDayFlag -> LocalDateTime.of(date, LocalTime.of(23, 59))
            else -> LocalDateTime.of(date, timeOf(endMin)).let { if (it.isAfter(start)) it else start.plusHours(1) }
        }
        val recurrence = when {
            isAlarm -> null
            frequency == RecurrenceFrequency.NONE -> EventRecurrence(excludedDates = seed?.recurrence?.excludedDates.orEmpty())
            frequency == RecurrenceFrequency.SPECIFIC_DATES -> EventRecurrence(
                frequency = frequency,
                specificDates = specificDates.ifEmpty { setOf(date) },
                until = untilDate
            )
            else -> EventRecurrence(
                frequency = frequency,
                interval = interval.coerceAtLeast(1),
                weekDays = if (frequency == RecurrenceFrequency.WEEKLY) weekDays else emptySet(),
                until = untilDate,
                excludedDates = seed?.recurrence?.excludedDates.orEmpty()
            )
        }
        val offsets = EventReminderRules.cap(reminderOffsets.distinct())
        val byOffset = seedReminders.associateBy { it.offsetMinutes }
        val custom = seedReminders.filter { r -> r.offsetMinutes == null || allBeforeEventOptions().none { it.second.toInt() == r.offsetMinutes } }
        val reminders: List<EventReminder> = if (!canRemind || (isTask && !hasDate)) {
            emptyList()
        } else {
            EventReminderRules.cap(
                offsets.filter { o -> allBeforeEventOptions().any { it.second.toInt() == o } }.map { o ->
                    byOffset[o] ?: EventReminder(label = labelForMinutesBefore(o.toLong()), offsetMinutes = o)
                } + custom
            )
        }
        return CalendarEvent(
            id = existing?.id ?: 0L,
            remoteId = existing?.remoteId,
            title = title.trim().ifBlank { if (isAlarm) "Alarm" else title.trim() },
            notes = if (isAlarm) "" else notes.trim(),
            location = if (isAlarm || isTask) "" else location.trim(),
            links = if (isAlarm) "" else links.trim(),
            startAt = start,
            endAt = end,
            allDay = allDayFlag,
            hasDate = if (isTask) hasDate else true,
            kind = calKind,
            isCompleted = existing?.isCompleted ?: false,
            completedAt = existing?.completedAt,
            isEnabled = existing?.isEnabled ?: true,
            alarmToneUri = if (isAlarm) toneUri else existing?.alarmToneUri,
            alarmRepeatDays = if (isAlarm) alarmDays else 0,
            recurrence = recurrence,
            reminders = reminders
        )
    }

    fun buildHabit(): Habit {
        val t = title.trim()
        val category = when {
            t.contains("gym", true) || t.contains("run", true) -> HabitCategory.EXERCISE
            t.contains("read", true) -> HabitCategory.READING
            t.contains("pray", true) -> HabitCategory.PRAYER
            else -> existingHabit?.category ?: HabitCategory.CUSTOM
        }
        return Habit(
            id = existingHabit?.id ?: 0L,
            title = t,
            category = category,
            daysMask = habitDays,
            startTime = timeOf(startMin),
            durationMinutes = habitMinutes,
            nudgeEnabled = nudges,
            quietOverride = existingHabit?.quietOverride ?: false
        )
    }

    LSheet(
        title = if (editing) "Edit ${kind.label.lowercase()}" else "Add",
        onDismiss = onDismiss,
        primary = when {
            saving -> "Saving"
            kind == AddKind.Study -> "Find time"
            else -> "Save"
        },
        onPrimary = {
            when (kind) {
                AddKind.Study -> onFindTime(title.trim(), hours.toDouble(), deadline)
                AddKind.Habit -> scope.launch {
                    saving = true
                    error = null
                    onSaveHabit(buildHabit())
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: "Could not save" }
                    saving = false
                }
                else -> onSaveEvent(buildEvent(), leaveBy && kind == AddKind.Event)
            }
        },
        primaryEnabled = !saving && (isAlarm || title.isNotBlank()),
        secondary = if (editing && onDelete != null) "Delete" else null,
        onSecondary = { onDelete?.invoke() }
    ) {
        if (!editing) {
            LKindChips(AddKind.entries, kind, { it.label }, { kindName = it.name; error = null })
        }
        LField(
            title,
            { title = it },
            when (kind) {
                AddKind.Alarm -> "Label"
                AddKind.Study -> "Topic"
                AddKind.Habit -> "Name"
                else -> "Title"
            }
        )

        when (kind) {
            AddKind.Study -> {
                SectionLabel("Hours")
                ChipsRow {
                    HourChoices.forEach { h -> LChip("$h h", selected = hours == h, onClick = { hours = h }) }
                }
                SectionLabel("Deadline")
                ChipsRow {
                    LChip("None", selected = deadline == null, onClick = { deadline = null })
                    DatePickChip(
                        deadline ?: defaultDate.plusDays(7),
                        selected = deadline != null,
                        onDate = { deadline = it },
                        label = deadline?.format(dateFmt) ?: "Pick date"
                    )
                }
            }

            AddKind.Habit -> {
                SectionLabel("Time")
                ChipsRow {
                    TimePickChip(timeOf(startMin), { startMin = minutesOf(it) })
                    DurationChoices.forEach { m ->
                        LChip("$m min", selected = habitMinutes == m, onClick = { habitMinutes = m })
                    }
                }
                SectionLabel("Days")
                ChipsRow {
                    LChip("Every day", selected = habitDays == 0, onClick = { habitDays = 0 })
                    WeekChips.forEach { (label, iso, bit) ->
                        LChip(
                            label,
                            selected = habitDays and bit != 0,
                            onClick = { habitDays = habitDays xor bit },
                            modifier = Modifier.semantics { contentDescription = fullDay(iso) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nudges", style = MaterialTheme.typography.titleSmall, color = L.Ink)
                    Switch(
                        checked = nudges,
                        onCheckedChange = { nudges = it },
                        modifier = Modifier.semantics { contentDescription = "Nudges" }
                    )
                }
            }

            else -> {
                // When
                if (isTask) {
                    ChipsRow {
                        LChip("No date", selected = !hasDate, onClick = { hasDate = false })
                        DatePickChip(
                            date,
                            selected = hasDate,
                            onDate = { date = it; hasDate = true },
                            label = if (hasDate) date.format(dateFmt) else "Pick date"
                        )
                        if (hasDate) LChip("No time", selected = noTime, onClick = { noTime = !noTime })
                    }
                } else {
                    ChipsRow {
                        DatePickChip(date, selected = true, onDate = { date = it }, label = date.format(dateFmt))
                        if (hasEnd) LChip("All day", selected = allDay, onClick = { allDay = !allDay })
                    }
                }
                if (timed) {
                    ChipsRow {
                        TimePickChip(
                            timeOf(startMin),
                            { t ->
                                val length = (endMin - startMin).let { if (it <= 0) 60 else it }
                                startMin = minutesOf(t)
                                endMin = (startMin + length) % (24 * 60)
                            },
                            label = if (hasEnd) "From ${timeOf(startMin).format(timeFmt)}" else null
                        )
                        if (hasEnd) {
                            TimePickChip(
                                timeOf(endMin),
                                { endMin = minutesOf(it) },
                                label = "To ${timeOf(endMin).format(timeFmt)}"
                            )
                        }
                    }
                }

                if (isAlarm) {
                    SectionLabel("Repeat")
                    ChipsRow {
                        LChip("Once", selected = alarmDays == 0, onClick = { alarmDays = 0 })
                        WeekChips.forEach { (label, iso, bit) ->
                            LChip(
                                label,
                                selected = alarmDays and bit != 0,
                                onClick = { alarmDays = alarmDays xor bit },
                                modifier = Modifier.semantics { contentDescription = fullDay(iso) }
                            )
                        }
                    }
                    ToneChips(toneUri) { toneUri = it }
                } else {
                    if (isTask) {
                        LField(links, { links = it }, "Links", singleLine = false)
                    } else {
                        LPlaceField(location, { location = it }, links, { links = it }, "Place")
                    }
                    LField(notes, { notes = it }, "Notes", singleLine = false)
                    if (kind == AddKind.Event) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Leave by", style = MaterialTheme.typography.titleSmall, color = L.Ink)
                            Switch(
                                checked = leaveBy,
                                onCheckedChange = { leaveBy = it },
                                modifier = Modifier.semantics { contentDescription = "Leave by" }
                            )
                        }
                    }

                    SectionLabel("Repeat")
                    ChipsRow {
                        RecurrenceFrequency.entries.forEach { f ->
                            val label = when (f) {
                                RecurrenceFrequency.NONE -> "Once"
                                RecurrenceFrequency.DAILY -> "Daily"
                                RecurrenceFrequency.WEEKLY -> "Weekly"
                                RecurrenceFrequency.MONTHLY -> "Monthly"
                                RecurrenceFrequency.YEARLY -> "Yearly"
                                RecurrenceFrequency.SPECIFIC_DATES -> "Pick dates"
                            }
                            LChip(label, selected = frequency == f, onClick = { frequencyName = f.name })
                        }
                    }
                    if (frequency != RecurrenceFrequency.NONE && frequency != RecurrenceFrequency.SPECIFIC_DATES) {
                        ChipsRow {
                            Text(
                                "Every",
                                style = MaterialTheme.typography.bodyMedium,
                                color = L.InkMuted,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            (1..6).forEach { n -> LChip("$n", selected = interval == n, onClick = { interval = n }) }
                        }
                    }
                    if (frequency == RecurrenceFrequency.WEEKLY) {
                        ChipsRow {
                            WeekChips.forEach { (label, iso, _) ->
                                LChip(
                                    label,
                                    selected = iso in weekDays,
                                    onClick = { weekDays = if (iso in weekDays) weekDays - iso else weekDays + iso },
                                    modifier = Modifier.semantics { contentDescription = fullDay(iso) }
                                )
                            }
                        }
                    }
                    if (frequency == RecurrenceFrequency.SPECIFIC_DATES) {
                        ChipsRow {
                            specificDates.sorted().forEach { d ->
                                LChip(
                                    d.format(dateFmt),
                                    selected = true,
                                    onClick = { specificDates = specificDates - d },
                                    modifier = Modifier.semantics { contentDescription = "Remove ${d.format(dateFmt)}" }
                                )
                            }
                            DatePickChip(date, selected = false, onDate = { specificDates = specificDates + it }, label = "Add date")
                        }
                    }
                    if (frequency != RecurrenceFrequency.NONE) {
                        ChipsRow {
                            DatePickChip(
                                untilDate ?: date,
                                selected = untilDate != null,
                                onDate = { untilDate = it },
                                label = untilDate?.format(dateFmt)?.let { "Until $it" } ?: "No end"
                            )
                            if (untilDate != null) LChip("Clear", selected = false, onClick = { untilDate = null })
                        }
                    }

                    if (canRemind && (!isTask || hasDate)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SectionLabel("Reminders")
                            Text(
                                "${reminderOffsets.size}/$MAX_REMINDERS_PER_EVENT",
                                style = MaterialTheme.typography.labelMedium,
                                color = L.InkMuted
                            )
                        }
                        ReminderChips(reminderOffsets) { reminderOffsets = it }
                    }
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ErrorInk) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = L.Ink,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun ReminderChips(offsets: List<Int>, onChange: (List<Int>) -> Unit) {
    val options = remember { allBeforeEventOptions() }
    ChipsRow {
        options.forEach { (label, minutes) ->
            val offset = minutes.toInt()
            val on = offset in offsets
            LChip(
                label,
                selected = on,
                onClick = {
                    when {
                        on -> onChange(offsets - offset)
                        offsets.size < MAX_REMINDERS_PER_EVENT -> onChange(offsets + offset)
                    }
                }
            )
        }
    }
}
