package com.ledgerai.app.presentation.screens.plan

import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.PlanBlockKind
import com.ledgerai.app.domain.model.PlanBlockStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** Kind chips on the Calendar segment. */
enum class PlanKind(val label: String) {
    All("All"), Events("Events"), Tasks("Tasks"), Classes("Classes"), Alarms("Alarms"), Study("Study"), Habits("Habits")
}

enum class AgendaSource { Event, Block, Habit }

/**
 * One row of the merged calendar: a calendar event occurrence, a saved plan block or a habit session.
 * [key] is stable and string-only so an open sheet survives rotation.
 */
data class AgendaItem(
    val key: String,
    val source: AgendaSource,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val point: Boolean,
    val kind: PlanKind,
    val label: String,
    val place: String = "",
    val done: Boolean = false,
    val enabled: Boolean = true,
    val reminders: Int = 0,
    val event: CalendarEvent? = null,
    val block: PlanBlock? = null,
    val habit: Habit? = null
) {
    val date: LocalDate get() = start.toLocalDate()
    val isTask: Boolean get() = event?.kind == CalendarEventKind.TASK
    val isAlarm: Boolean get() = event?.kind == CalendarEventKind.ALARM
}

fun PlanKind.matches(item: AgendaItem): Boolean = this == PlanKind.All || this == item.kind

object AgendaKeys {
    fun event(masterId: Long, date: LocalDate?): String = "e:$masterId:${date ?: "-"}"
    fun block(id: Long): String = "b:$id"
    fun habit(habitId: Long, date: LocalDate): String = "h:$habitId:$date"
}

private fun CalendarEventKind.planKind(): PlanKind = when (this) {
    CalendarEventKind.EVENT, CalendarEventKind.PERSONAL, CalendarEventKind.EXAM -> PlanKind.Events
    CalendarEventKind.TASK -> PlanKind.Tasks
    CalendarEventKind.CLASS, CalendarEventKind.ROUTINE -> PlanKind.Classes
    CalendarEventKind.ALARM -> PlanKind.Alarms
}

fun CalendarEvent.toAgendaItem(): AgendaItem {
    val point = kind == CalendarEventKind.TASK || kind == CalendarEventKind.ALARM
    return AgendaItem(
        key = AgendaKeys.event(masterId, instanceDate),
        source = AgendaSource.Event,
        title = title,
        start = startAt,
        end = endAt,
        allDay = allDay,
        point = point,
        kind = kind.planKind(),
        label = when (kind) {
            CalendarEventKind.EVENT, CalendarEventKind.PERSONAL -> "Event"
            CalendarEventKind.TASK -> "Task"
            CalendarEventKind.EXAM -> "Exam"
            CalendarEventKind.CLASS -> "Class"
            CalendarEventKind.ROUTINE -> "Routine"
            CalendarEventKind.ALARM -> "Alarm"
        },
        place = location,
        done = isCompleted,
        enabled = isEnabled,
        reminders = reminders.size,
        event = this
    )
}

fun PlanBlock.toAgendaItem(): AgendaItem {
    val study = kind == PlanBlockKind.STUDY
    return AgendaItem(
        key = AgendaKeys.block(id),
        source = AgendaSource.Block,
        title = title,
        start = startAt,
        end = endAt,
        allDay = false,
        point = false,
        kind = if (study) PlanKind.Study else PlanKind.Habits,
        label = if (study) "Study" else "Habit",
        done = status == PlanBlockStatus.DONE,
        block = this
    )
}

fun Habit.sessionOn(date: LocalDate, done: Boolean): AgendaItem {
    val start = date.atTime(startTime)
    return AgendaItem(
        key = AgendaKeys.habit(id, date),
        source = AgendaSource.Habit,
        title = title,
        start = start,
        end = start.plusMinutes(durationMinutes.toLong()),
        allDay = false,
        point = false,
        kind = PlanKind.Habits,
        label = "Habit",
        done = done,
        habit = this
    )
}

/** Dates on which [habit] has a session (mask Sun=1 ... Sat=64, 0 = every day). */
fun Habit.occursOn(date: LocalDate): Boolean =
    daysMask == 0 || (daysMask and (1 shl (date.dayOfWeek.value % 7))) != 0
