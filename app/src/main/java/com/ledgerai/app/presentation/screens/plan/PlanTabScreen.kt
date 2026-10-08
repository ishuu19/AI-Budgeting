package com.ledgerai.app.presentation.screens.plan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.PlanBlockStatus
import com.ledgerai.app.domain.model.RecurrenceDeleteScope
import com.ledgerai.app.presentation.components.AddKind
import com.ledgerai.app.presentation.components.CalendarEventDetailSheet
import com.ledgerai.app.presentation.components.HabitSessionSheet
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LTabPage
import com.ledgerai.app.presentation.components.PlanAddSheet
import com.ledgerai.app.presentation.components.PlanBlockSheet
import com.ledgerai.app.presentation.components.RecurrenceDeleteSheet
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.OpenItem
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.presentation.screens.calendar.CalendarScreen
import com.ledgerai.app.presentation.screens.jobs.JobsScreen
import com.ledgerai.app.presentation.screens.lifelog.LifeLogScreen
import com.ledgerai.app.presentation.screens.tasks.TasksScreen
import java.time.LocalDate

/** Plan tab: Calendar, Tasks, Log, Jobs. Owns the Add sheet and every item sheet for Calendar and Tasks. */
@Composable
fun PlanTabScreen(
    seg: PlanSeg,
    onSeg: (PlanSeg) -> Unit,
    links: AppLinks,
    open: OpenItem? = null,
    onOpened: () -> Unit = {}
) {
    val vm: PlanViewModel = hiltViewModel()
    var adding by rememberSaveable { mutableStateOf(false) }
    var addKind by rememberSaveable { mutableStateOf(AddKind.Event) }
    var addDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var itemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editEventId by rememberSaveable { mutableLongStateOf(0L) }
    var editHabitId by rememberSaveable { mutableLongStateOf(0L) }
    var deleteEventId by rememberSaveable { mutableLongStateOf(0L) }
    var deleteHabitId by rememberSaveable { mutableLongStateOf(0L) }
    var jobId by rememberSaveable { mutableStateOf<Long?>(null) }
    val study by vm.study.collectAsState()
    val agenda by vm.agenda.collectAsState()

    LaunchedEffect(open?.nonce) {
        val o = open ?: return@LaunchedEffect
        when (o.kind) {
            OpenKind.Event -> {
                itemKey = AgendaKeys.event(o.id, null)
                onOpened()
            }
            OpenKind.Job -> jobId = o.id
            else -> onOpened()
        }
    }

    val startAdd: (AddKind, LocalDate) -> Unit = { kind, day ->
        addKind = kind
        addDay = day.toEpochDay()
        adding = true
    }

    LTabPage(
        title = "Plan",
        action = {
            IconButton(onClick = links.search) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = L.Box)
            }
        },
        segments = { LSegments(PlanSeg.entries, seg, { it.label }, onSeg) }
    ) {
        when (seg) {
            PlanSeg.Calendar -> CalendarScreen(vm, onOpen = { itemKey = it }, onAdd = { startAdd(AddKind.Event, it) })
            PlanSeg.Tasks -> TasksScreen(vm, onOpen = { itemKey = it }, onAdd = { startAdd(AddKind.Task, it) })
            PlanSeg.Log -> LifeLogScreen()
            PlanSeg.Jobs -> JobsScreen(openId = jobId, onOpened = { jobId = null; onOpened() })
        }
    }

    // Add sheet
    if (adding) {
        PlanAddSheet(
            defaultDate = LocalDate.ofEpochDay(addDay),
            defaultKind = addKind,
            onDismiss = { adding = false },
            onSaveEvent = { event, leaveBy ->
                vm.saveEvent(event, leaveBy)
                adding = false
            },
            onFindTime = { topic, hours, deadline ->
                vm.findTime(topic, hours, deadline)
                adding = false
            },
            onSaveHabit = { vm.saveHabit(it) }
        )
    }

    // Study proposals
    study?.let { s ->
        StudyTimesSheet(study = s, onDismiss = vm::clearStudy, onConfirm = vm::confirmStudy)
    }

    // Item sheet
    val key = itemKey
    val item by produceState<AgendaItem?>(null, key, agenda.items) {
        value = key?.let { vm.resolve(it) }
        if (key != null && value == null) itemKey = null
    }
    item?.takeIf { it.key == key }?.let { current ->
        val close = { itemKey = null }
        val currentEvent = current.event
        val currentBlock = current.block
        val currentHabit = current.habit
        when {
            currentEvent != null -> {
                CalendarEventDetailSheet(
                    event = currentEvent,
                    onDismiss = close,
                    onEdit = { editEventId = currentEvent.masterId; close() },
                    onChangeDate = { vm.reschedule(currentEvent, it); close() },
                    onToggleDone = { vm.setDone(currentEvent, !currentEvent.isCompleted); close() },
                    onToggleEnabled = { vm.setAlarmEnabled(currentEvent, it) },
                    onDelete = { scope -> vm.deleteEvent(currentEvent, scope); close() }
                )
            }
            currentBlock != null -> {
                PlanBlockSheet(
                    block = currentBlock,
                    onDismiss = close,
                    onStart = { links.focus(currentBlock.id, currentBlock.topic.ifBlank { currentBlock.title }); close() },
                    onToggleDone = {
                        vm.setBlockDone(currentBlock, currentBlock.status != PlanBlockStatus.DONE)
                        close()
                    },
                    onDelete = { vm.deleteBlock(currentBlock.id); close() }
                )
            }
            currentHabit != null -> {
                HabitSessionSheet(
                    habit = currentHabit,
                    date = current.date,
                    done = current.done,
                    onDismiss = close,
                    onStart = { links.focus(-currentHabit.id, currentHabit.title); close() },
                    onComplete = { vm.completeHabit(currentHabit, current.date); close() },
                    onEdit = { editHabitId = currentHabit.id; close() },
                    onDelete = { vm.deleteHabit(currentHabit.id); close() }
                )
            }
        }
    }

    // Edit an event
    if (editEventId > 0L) {
        val loaded by produceState<Pair<CalendarEvent, Boolean>?>(null, editEventId) {
            val master = vm.loadMaster(editEventId)
            if (master == null) editEventId = 0L else value = master to vm.isLeaveByEnabled(master.id)
        }
        loaded?.let { (master, leaveBy) ->
            PlanAddSheet(
                defaultDate = master.startAt.toLocalDate(),
                existing = master,
                initialLeaveBy = leaveBy,
                onDismiss = { editEventId = 0L },
                onSaveEvent = { event, lb ->
                    vm.saveEvent(event, lb)
                    editEventId = 0L
                },
                onFindTime = { _, _, _ -> },
                onSaveHabit = { Result.success(Unit) },
                onDelete = { deleteEventId = master.id; editEventId = 0L }
            )
        }
    }

    // Edit a habit
    if (editHabitId > 0L) {
        val habit by produceState<Habit?>(null, editHabitId) {
            val h = vm.loadHabit(editHabitId)
            if (h == null) editHabitId = 0L else value = h
        }
        habit?.let { h ->
            PlanAddSheet(
                defaultDate = LocalDate.now(),
                existingHabit = h,
                onDismiss = { editHabitId = 0L },
                onSaveEvent = { _, _ -> },
                onFindTime = { _, _, _ -> },
                onSaveHabit = { vm.saveHabit(it) },
                onDelete = { deleteHabitId = h.id; editHabitId = 0L }
            )
        }
    }

    if (deleteHabitId > 0L) {
        LConfirmDelete(
            onConfirm = { vm.deleteHabit(deleteHabitId); deleteHabitId = 0L },
            onDismiss = { deleteHabitId = 0L }
        )
    }

    // Delete from the edit sheet
    if (deleteEventId > 0L) {
        val target by produceState<CalendarEvent?>(null, deleteEventId) {
            val master = vm.loadMaster(deleteEventId)
            if (master == null) deleteEventId = 0L else value = master
        }
        target?.let { master ->
            if (master.isRecurring) {
                RecurrenceDeleteSheet(
                    title = master.title,
                    isRecurring = true,
                    onDismiss = { deleteEventId = 0L },
                    onThisOnly = { vm.deleteEvent(master, RecurrenceDeleteScope.THIS); deleteEventId = 0L },
                    onThisAndFuture = { vm.deleteEvent(master, RecurrenceDeleteScope.THIS_AND_FUTURE); deleteEventId = 0L },
                    onAll = { vm.deleteEvent(master, RecurrenceDeleteScope.ALL); deleteEventId = 0L }
                )
            } else {
                LConfirmDelete(
                    onConfirm = { vm.deleteEvent(master, RecurrenceDeleteScope.ALL); deleteEventId = 0L },
                    onDismiss = { deleteEventId = 0L }
                )
            }
        }
    }
}
