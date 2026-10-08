package com.ledgerai.app.presentation.screens.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val MicSize = 168.dp

@Composable
fun VoiceRecorderScreen(
    onNavigateBack: () -> Unit = {},
    embedded: Boolean = false,
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else viewModel.onPermissionDenied()
    }

    LaunchedEffect(state.recorderState) {
        if (state.recorderState == VoiceRecorderState.SAVED) {
            delay(1400)
            if (embedded) viewModel.reset() else onNavigateBack()
        }
    }

    val recording = state.recorderState == VoiceRecorderState.RECORDING
    val busy = state.recorderState == VoiceRecorderState.TRANSCRIBING ||
        state.recorderState == VoiceRecorderState.PARSING
    val intent = state.parsedIntent
    val reviewing = state.recorderState == VoiceRecorderState.RESULT && intent != null
    var editing by remember(intent) { mutableStateOf(false) }

    val back: (() -> Unit)? = if (embedded) null else ({
        viewModel.cancelRecording()
        onNavigateBack()
    })

    LScreen(
        title = "Voice",
        onBack = back,
        action = { EngineChip(state.engineLabel) }
    ) {
        item {
            MicButton(
                recording = recording,
                busy = busy,
                amplitude = state.amplitudeLevel,
                onClick = {
                    when {
                        recording -> viewModel.stopRecording()
                        busy -> Unit
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }

        item { StatusWord(statusWord(state)) }

        state.modelDownloadProgress?.let { progress ->
            item { DownloadProgress(progress) }
        }

        if (state.transcript.isNotBlank() && state.recorderState != VoiceRecorderState.IDLE) {
            item { TranscriptCard(state.transcript) }
        }

        if (reviewing && intent != null) {
            item { ResultCard(intent, onEdit = { editing = true }) }
            item {
                LButton(
                    "Save",
                    onClick = { saveIntent(intent, state.transcript, viewModel) },
                    enabled = canSave(intent)
                )
            }
            item { LGhostButton("Discard", onClick = viewModel::retryRecording) }
        }

        if (state.recorderState == VoiceRecorderState.IDLE ||
            state.recorderState == VoiceRecorderState.ERROR
        ) {
            item {
                LField(
                    value = state.typedInput,
                    onValueChange = viewModel::updateTypedInput,
                    label = "Type",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
            if (state.typedInput.isNotBlank()) {
                item { LGhostButton("Send", onClick = viewModel::parseTypedInput) }
            }
        }
    }

    if (editing && reviewing && intent != null) {
        EditSheet(
            intent = intent,
            transcript = state.transcript,
            onDismiss = { editing = false },
            viewModel = viewModel
        )
    }
}

private fun statusWord(state: VoiceUiState): String = when (state.recorderState) {
    VoiceRecorderState.IDLE -> "Tap to speak"
    VoiceRecorderState.RECORDING -> "Listening"
    VoiceRecorderState.TRANSCRIBING ->
        if (state.modelDownloadProgress != null) "Downloading" else "Thinking"
    VoiceRecorderState.PARSING -> "Thinking"
    VoiceRecorderState.RESULT -> "Review"
    VoiceRecorderState.SAVED -> "Saved"
    VoiceRecorderState.ERROR -> state.errorMessage ?: "Try again"
}

@Composable
private fun EngineChip(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = L.Gold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(L.Box)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun MicButton(recording: Boolean, busy: Boolean, amplitude: Float, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            if (recording) PulseRings(amplitude)
            Box(
                Modifier
                    .size(MicSize)
                    .clip(CircleShape)
                    .background(L.Gold)
                    .clickable(
                        enabled = !busy,
                        onClickLabel = if (recording) "Stop" else "Speak",
                        role = Role.Button,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Stop" else "Speak",
                    tint = L.BoxDeep,
                    modifier = Modifier.size(64.dp).alpha(if (busy) 0.4f else 1f)
                )
            }
        }
    }
}

@Composable
private fun PulseRings(amplitude: Float) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val level by animateFloatAsState(amplitude, tween(120), label = "level")
    listOf(0f, 0.5f).forEach { offset ->
        val f = (phase + offset) % 1f
        val scale = 1f + f * (0.5f + level * 0.15f)
        Box(
            Modifier
                .size(MicSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - f) * 0.45f
                }
                .clip(CircleShape)
                .background(L.Box)
        )
    }
}

@Composable
private fun StatusWord(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = L.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DownloadProgress(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LProgress(p, Modifier.weight(1f))
        Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = L.Ink)
    }
}

@Composable
private fun TranscriptCard(text: String) {
    LCard {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
    }
}

@Composable
private fun ResultCard(intent: ParsedIntent, onEdit: () -> Unit) {
    val (headline, sub) = summarize(intent)
    LCard(onClick = onEdit) {
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            color = L.OnBox,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!sub.isNullOrBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = L.OnBoxMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val DateTimeShort = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val TimeShort = DateTimeFormatter.ofPattern("h:mm a")
private val DateShort = DateTimeFormatter.ofPattern("MMM d")
private val DateInput = DateTimeFormatter.ISO_LOCAL_DATE
private val ReminderInput = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun typeLabel(type: TransactionType) = type.name.lowercase().replaceFirstChar { it.uppercase() }

private fun ruleLabel(rule: String) = rule.lowercase().replaceFirstChar { it.uppercase() }

private val AlarmRepeats = listOf(0 to "Once", 62 to "Weekdays", 127 to "Daily")

private fun repeatLabel(mask: Int) = AlarmRepeats.firstOrNull { it.first == mask }?.second ?: "Custom"

private fun summarize(intent: ParsedIntent): Pair<String, String?> = when (intent) {
    is ParsedIntent.Transaction -> Pair(
        "${typeLabel(intent.type)} · ${money(intent.amount ?: 0.0)} · ${intent.category.displayName}",
        intent.merchant
    )
    is ParsedIntent.Task -> Pair("Task · ${intent.title}", intent.dueAt?.format(DateTimeShort))
    is ParsedIntent.Reminder -> Pair("Reminder · ${intent.title}", intent.remindAt.format(DateTimeShort))
    is ParsedIntent.Alarm -> Pair(
        "Alarm · ${intent.time.format(TimeShort)}",
        "${intent.label} · ${repeatLabel(intent.repeatDays)}"
    )
    is ParsedIntent.Note -> Pair("Note · ${intent.title.ifBlank { "Untitled" }}", intent.body)
    is ParsedIntent.Routine -> Pair("Routine · ${intent.title}", ruleLabel(intent.repeatRule.ifBlank { "DAILY" }))
    is ParsedIntent.Bill -> Pair(
        "Bill · ${money(intent.amount)} · ${intent.name}",
        "${intent.frequency.displayName} · ${intent.nextDueDate.format(DateShort)}"
    )
    is ParsedIntent.Debt -> Pair(
        "Debt · ${money(intent.amount)} · ${intent.friendName}",
        listOfNotNull(
            if (intent.direction == DebtDirection.I_OWE) "I owe" else "They owe",
            intent.dueDate?.format(DateShort)
        ).joinToString(" · ")
    )
    is ParsedIntent.Goal -> Pair("Goal · ${money(intent.targetAmount)} · ${intent.name}", null)
}

private fun canSave(intent: ParsedIntent): Boolean = when (intent) {
    is ParsedIntent.Transaction -> (intent.amount ?: 0.0) > 0.0
    is ParsedIntent.Task -> intent.title.isNotBlank()
    is ParsedIntent.Reminder -> true
    is ParsedIntent.Alarm -> true
    is ParsedIntent.Note -> intent.title.isNotBlank() || intent.body.isNotBlank()
    is ParsedIntent.Routine -> intent.title.isNotBlank()
    is ParsedIntent.Bill -> intent.name.isNotBlank() && intent.amount > 0.0
    is ParsedIntent.Debt -> intent.friendName.isNotBlank() && intent.amount > 0.0
    is ParsedIntent.Goal -> intent.name.isNotBlank() && intent.targetAmount > 0.0
}

private fun saveIntent(intent: ParsedIntent, transcript: String, vm: VoiceRecorderViewModel) {
    when (intent) {
        is ParsedIntent.Transaction -> {
            val amount = intent.amount ?: return
            vm.confirmTransaction(
                amount, intent.type, intent.category, intent.merchant,
                intent.note.ifBlank { if (intent.merchant.isBlank()) transcript else "" },
                intent.date
            )
        }
        is ParsedIntent.Task -> vm.confirmTask(intent.title, intent.notes, intent.dueAt)
        is ParsedIntent.Reminder -> vm.confirmReminder(intent.title, intent.label, intent.remindAt)
        is ParsedIntent.Alarm -> vm.confirmAlarm(intent.label, intent.time, intent.repeatDays)
        is ParsedIntent.Note -> vm.confirmNote(intent.title, intent.body, intent.tags)
        is ParsedIntent.Routine -> vm.confirmRoutine(intent.title, intent.notes, intent.repeatRule)
        is ParsedIntent.Bill -> vm.confirmBill(
            intent.name, intent.amount, intent.frequency, intent.nextDueDate, intent.category
        )
        is ParsedIntent.Debt -> vm.confirmDebt(
            intent.friendName, intent.amount, intent.direction, intent.dueDate
        )
        is ParsedIntent.Goal -> vm.confirmGoal(intent.name, intent.targetAmount)
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun EditSheet(
    intent: ParsedIntent,
    transcript: String,
    onDismiss: () -> Unit,
    viewModel: VoiceRecorderViewModel
) {
    when (intent) {
        is ParsedIntent.Transaction -> TransactionSheet(intent, transcript, onDismiss) { a, t, c, m, n, d ->
            onDismiss()
            viewModel.confirmTransaction(a, t, c, m, n, d)
        }
        is ParsedIntent.Task -> TaskSheet(intent, onDismiss) { title, notes ->
            onDismiss()
            viewModel.confirmTask(title, notes, intent.dueAt)
        }
        is ParsedIntent.Reminder -> ReminderSheet(intent, onDismiss) { title, label, at ->
            onDismiss()
            viewModel.confirmReminder(title, label, at)
        }
        is ParsedIntent.Alarm -> AlarmSheet(intent, onDismiss) { label, time, repeat ->
            onDismiss()
            viewModel.confirmAlarm(label, time, repeat)
        }
        is ParsedIntent.Note -> NoteSheet(intent, onDismiss) { title, body, tags ->
            onDismiss()
            viewModel.confirmNote(title, body, tags)
        }
        is ParsedIntent.Routine -> RoutineSheet(intent, onDismiss) { title, notes, rule ->
            onDismiss()
            viewModel.confirmRoutine(title, notes, rule)
        }
        is ParsedIntent.Bill -> BillSheet(intent, onDismiss) { name, amount, freq, due, cat ->
            onDismiss()
            viewModel.confirmBill(name, amount, freq, due, cat)
        }
        is ParsedIntent.Debt -> DebtSheet(intent, onDismiss) { name, amount, direction, due ->
            onDismiss()
            viewModel.confirmDebt(name, amount, direction, due)
        }
        is ParsedIntent.Goal -> GoalSheet(intent, onDismiss) { name, target ->
            onDismiss()
            viewModel.confirmGoal(name, target)
        }
    }
}

@Composable
private fun TransactionSheet(
    parsed: ParsedIntent.Transaction,
    transcript: String,
    onDismiss: () -> Unit,
    onSave: (Double, TransactionType, TransactionCategory, String, String, LocalDate) -> Unit
) {
    var amount by remember {
        mutableStateOf(parsed.amount?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var merchant by remember { mutableStateOf(parsed.merchant) }
    var note by remember {
        mutableStateOf(parsed.note.ifBlank { if (parsed.merchant.isBlank()) transcript else "" })
    }
    var type by remember { mutableStateOf(parsed.type) }
    var category by remember { mutableStateOf(parsed.category) }
    val value = amount.toDoubleOrNull()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { value?.let { onSave(it, type, category, merchant, note, parsed.date) } },
        primaryEnabled = value != null && value > 0.0
    ) {
        ChipRow {
            TransactionType.entries.forEach { t ->
                LChip(typeLabel(t), selected = type == t, onClick = { type = t })
            }
        }
        LField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        LField(value = merchant, onValueChange = { merchant = it }, label = "Merchant")
        ChipRow {
            TransactionCategory.entries.forEach { c ->
                LChip(c.displayName, selected = category == c, onClick = { category = c })
            }
        }
        LField(value = note, onValueChange = { note = it }, label = "Note", singleLine = false)
    }
}

@Composable
private fun TaskSheet(
    parsed: ParsedIntent.Task,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(parsed.title) }
    var notes by remember { mutableStateOf(parsed.notes) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(title, notes) },
        primaryEnabled = title.isNotBlank()
    ) {
        LField(value = title, onValueChange = { title = it }, label = "Title")
        LField(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
    }
}

@Composable
private fun ReminderSheet(
    parsed: ParsedIntent.Reminder,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDateTime) -> Unit
) {
    var title by remember { mutableStateOf(parsed.title) }
    var label by remember { mutableStateOf(parsed.label) }
    var whenText by remember { mutableStateOf(parsed.remindAt.format(ReminderInput)) }
    val at = remember(whenText) {
        runCatching { LocalDateTime.parse(whenText.trim(), ReminderInput) }.getOrNull()
    }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { at?.let { onSave(title, label, it) } },
        primaryEnabled = title.isNotBlank() && at != null
    ) {
        LField(value = title, onValueChange = { title = it }, label = "Title")
        LField(value = label, onValueChange = { label = it }, label = "Label")
        LField(value = whenText, onValueChange = { whenText = it }, label = "When")
    }
}

@Composable
private fun AlarmSheet(
    parsed: ParsedIntent.Alarm,
    onDismiss: () -> Unit,
    onSave: (String, LocalTime, Int) -> Unit
) {
    var label by remember { mutableStateOf(parsed.label) }
    var hour by remember { mutableStateOf(parsed.time.hour.toString()) }
    var minute by remember { mutableStateOf("%02d".format(parsed.time.minute)) }
    var repeat by remember { mutableIntStateOf(parsed.repeatDays) }
    val time = remember(hour, minute) {
        val h = hour.toIntOrNull()
        val m = minute.toIntOrNull()
        if (h != null && m != null && h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { time?.let { onSave(label, it, repeat) } },
        primaryEnabled = time != null
    ) {
        LField(value = label, onValueChange = { label = it }, label = "Label")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                label = "Hour",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            LField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                label = "Minute",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        ChipRow {
            AlarmRepeats.forEach { (mask, name) ->
                LChip(name, selected = repeat == mask, onClick = { repeat = mask })
            }
        }
    }
}

@Composable
private fun NoteSheet(
    parsed: ParsedIntent.Note,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(parsed.title) }
    var body by remember { mutableStateOf(parsed.body) }
    var tags by remember { mutableStateOf(parsed.tags.joinToString(", ")) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            onSave(title, body, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        },
        primaryEnabled = title.isNotBlank() || body.isNotBlank()
    ) {
        LField(value = title, onValueChange = { title = it }, label = "Title")
        LField(value = body, onValueChange = { body = it }, label = "Body", singleLine = false, minLines = 3)
        LField(value = tags, onValueChange = { tags = it }, label = "Tags")
    }
}

private val RoutineRules = listOf("DAILY", "WEEKLY", "WEEKDAYS", "CUSTOM")

@Composable
private fun RoutineSheet(
    parsed: ParsedIntent.Routine,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(parsed.title) }
    var notes by remember { mutableStateOf(parsed.notes) }
    var rule by remember { mutableStateOf(parsed.repeatRule.ifBlank { "DAILY" }) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { onSave(title, notes, rule) },
        primaryEnabled = title.isNotBlank()
    ) {
        LField(value = title, onValueChange = { title = it }, label = "Title")
        LField(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)
        ChipRow {
            RoutineRules.forEach { r ->
                LChip(ruleLabel(r), selected = rule.equals(r, ignoreCase = true), onClick = { rule = r })
            }
        }
    }
}

private fun parseDateInput(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim(), DateInput) }.getOrNull()

@Composable
private fun BillSheet(
    parsed: ParsedIntent.Bill,
    onDismiss: () -> Unit,
    onSave: (String, Double, BillFrequency, LocalDate, TransactionCategory) -> Unit
) {
    var name by remember { mutableStateOf(parsed.name) }
    var amount by remember {
        mutableStateOf(parsed.amount.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    var frequency by remember { mutableStateOf(parsed.frequency) }
    var dueText by remember { mutableStateOf(parsed.nextDueDate.format(DateInput)) }
    var category by remember { mutableStateOf(parsed.category) }
    val value = amount.toDoubleOrNull()
    val due = remember(dueText) { parseDateInput(dueText) }

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            if (value != null && due != null) onSave(name, value, frequency, due, category)
        },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0 && due != null
    ) {
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        ChipRow {
            BillFrequency.entries.forEach { f ->
                LChip(f.displayName, selected = frequency == f, onClick = { frequency = f })
            }
        }
        ChipRow {
            TransactionCategory.entries.forEach { c ->
                LChip(c.displayName, selected = category == c, onClick = { category = c })
            }
        }
        LField(value = dueText, onValueChange = { dueText = it }, label = "Due")
    }
}

@Composable
private fun DebtSheet(
    parsed: ParsedIntent.Debt,
    onDismiss: () -> Unit,
    onSave: (String, Double, DebtDirection, LocalDate?) -> Unit
) {
    var name by remember { mutableStateOf(parsed.friendName) }
    var amount by remember {
        mutableStateOf(parsed.amount.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    var direction by remember { mutableStateOf(parsed.direction) }
    var dueText by remember { mutableStateOf(parsed.dueDate?.format(DateInput).orEmpty()) }
    val value = amount.toDoubleOrNull()
    val due = remember(dueText) {
        if (dueText.isBlank()) null else parseDateInput(dueText)
    }
    val dueOk = dueText.isBlank() || due != null

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            if (value != null && dueOk) onSave(name, value, direction, due)
        },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0 && dueOk
    ) {
        ChipRow {
            LChip("They owe", selected = direction == DebtDirection.THEY_OWE, onClick = {
                direction = DebtDirection.THEY_OWE
            })
            LChip("I owe", selected = direction == DebtDirection.I_OWE, onClick = {
                direction = DebtDirection.I_OWE
            })
        }
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        LField(value = dueText, onValueChange = { dueText = it }, label = "Due")
    }
}

@Composable
private fun GoalSheet(
    parsed: ParsedIntent.Goal,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf(parsed.name) }
    var amount by remember {
        mutableStateOf(parsed.targetAmount.toBigDecimal().stripTrailingZeros().toPlainString())
    }
    val value = amount.toDoubleOrNull()

    LSheet(
        title = "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { value?.let { onSave(name, it) } },
        primaryEnabled = name.isNotBlank() && value != null && value > 0.0
    ) {
        LField(value = name, onValueChange = { name = it }, label = "Name")
        LField(
            value = amount,
            onValueChange = { amount = it },
            label = "Target",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}
