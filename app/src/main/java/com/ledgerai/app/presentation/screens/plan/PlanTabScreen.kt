package com.ledgerai.app.presentation.screens.plan

import com.ledgerai.app.presentation.components.LAiBar
import com.ledgerai.app.presentation.components.LSearchToolbar
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.LocalLinks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LSwipePages
import com.ledgerai.app.presentation.components.LTabPage
import com.ledgerai.app.presentation.screens.calendar.CalendarTools
import com.ledgerai.app.presentation.screens.calendar.DayFollowUp
import com.ledgerai.app.presentation.screens.jobs.JobsViewModel
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

/** Plan tab: Calendar, Tasks, Notes, Jobs (Log lives under More). Owns the Add sheet and every item sheet for Calendar and Tasks. */
@Composable
fun PlanTabScreen(
    seg: PlanSeg,
    onSeg: (PlanSeg) -> Unit,
    links: AppLinks,
    open: OpenItem? = null,
    onOpened: () -> Unit = {},
    agendaKey: String? = null,
    agendaNonce: Long = 0L,
    onAgendaOpened: () -> Unit = {},
) {
    val vm: PlanViewModel = hiltViewModel()
    val jobsVm: JobsViewModel = hiltViewModel()
    val jobsState by jobsVm.state.collectAsState()
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
    val calendarTools = remember { CalendarTools() }
    var query by rememberSaveable { mutableStateOf("") }
    var jobUtterance by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val pages = remember { PlanSeg.entries.filter { it != PlanSeg.Log } }

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

    LaunchedEffect(agendaNonce) {
        val key = agendaKey ?: return@LaunchedEffect
        if (agendaNonce == 0L) return@LaunchedEffect
        itemKey = key
        onAgendaOpened()
    }

    val startAdd: (AddKind, LocalDate) -> Unit = { kind, day ->
        addKind = kind
        addDay = day.toEpochDay()
        adding = true
    }

    LTabPage(
        title = "Plan",
        compact = true,
        tools = {
            LSearchToolbar(
                query = query,
                onQuery = { text -> if (seg == PlanSeg.Jobs) jobUtterance = text else query = text },
                searching = searching,
                onSearching = { searching = it },
                hint = when (seg) {
                    PlanSeg.Tasks -> "Search tasks"
                    PlanSeg.Notes -> "Search notes"
                    else -> "Search your calendar"
                },
                showSearch = seg == PlanSeg.Calendar || seg == PlanSeg.Tasks || seg == PlanSeg.Notes,
            ) {
                if (seg == PlanSeg.Calendar) {
                    LToolButton(Icons.Filled.AutoAwesome, "Suggest", calendarTools.suggest)
                    LToolButton(Icons.Filled.Upload, "Import", calendarTools.import)
                }
            }
        },
        segments = { LSegments(pages, seg, { it.label }, onSeg) },
        aiBar = {
            val links = LocalLinks.current
            LAiBar(
                hint = when (seg) {
                    PlanSeg.Tasks -> "Remind me to call the bank"
                    PlanSeg.Notes -> "Note an idea"
                    PlanSeg.Jobs -> "Applied to Google, follow up Friday"
                    else -> "Dentist Friday at 3"
                },
                onSend = { links.speak(it) },
                onMic = links.listen,
            )
        },
    ) {
        if (seg == PlanSeg.Log) {
            LifeLogScreen()
        } else {
            LSwipePages(pages, seg, onSeg) { page ->
                when (page) {
                    PlanSeg.Calendar -> CalendarScreen(
                        vm,
                        onOpen = { itemKey = it },
                        onAdd = { startAdd(AddKind.Event, it) },
                        tools = calendarTools,
                        query = query,
                        followUps = jobsState.jobs.mapNotNull { job ->
                            val day = job.followUpOn ?: return@mapNotNull null
                            DayFollowUp(job.id, job.company.ifBlank { job.title }, day)
                        },
                        onOpenJob = {
                            jobId = it
                            onSeg(PlanSeg.Jobs)
                        },
                    )
                    PlanSeg.Tasks -> TasksScreen(vm, onOpen = { itemKey = it }, query = query)
                    PlanSeg.Notes -> com.ledgerai.app.presentation.screens.notebook.NotebookScreen(
                        open = open?.takeIf { it.kind == OpenKind.Note },
                        onOpened = onOpened,
                        query = query,
                    )
                    PlanSeg.Jobs -> JobsScreen(
                        openId = jobId,
                        onOpened = { jobId = null; onOpened() },
                        utterance = jobUtterance,
                        onUtteranceConsumed = { jobUtterance = "" },
                    )
                    PlanSeg.Log -> Unit
                }
            }
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
        StudyTimesSheet(study = s, onDismiss = vm::clearStudy, onConfirm = vm::confirmStudy, busy = agenda.items)
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
