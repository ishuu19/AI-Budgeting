package com.ledgerai.app.presentation.screens.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Request RECORD_AUDIO permission before starting
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else viewModel.onPermissionDenied()
    }

    LaunchedEffect(state.recorderState) {
        if (state.recorderState == VoiceRecorderState.SAVED) {
            kotlinx.coroutines.delay(1600)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelRecording()
                        onNavigateBack()
                    }) { Icon(Icons.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Status chip ───────────────────────────────────────────────────
            StatusChip(state.recorderState)

            Spacer(Modifier.height(32.dp))

            // ── Central recording zone ────────────────────────────────────────
            AnimatedContent(
                targetState = state.recorderState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "central"
            ) { recorderState ->
                when (recorderState) {
                    VoiceRecorderState.IDLE, VoiceRecorderState.ERROR ->
                        IdleMicSection(
                            recorderState = recorderState,
                            onStart = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )

                    VoiceRecorderState.RECORDING ->
                        RecordingSection(
                            seconds = state.recordingSeconds,
                            amplitude = state.amplitudeLevel,
                            onStop = { viewModel.stopRecording() },
                            onCancel = { viewModel.cancelRecording() }
                        )

                    VoiceRecorderState.TRANSCRIBING ->
                        ProcessingSection(label = "Transcribing audio with Whisper AI…")

                    VoiceRecorderState.PARSING ->
                        ProcessingSection(label = "Parsing transaction with Claude AI…")

                    VoiceRecorderState.RESULT ->
                        Unit // Handled below

                    VoiceRecorderState.SAVED ->
                        SavedSection()
                }
            }

            // ── Transcript preview (shown after transcription) ────────────────
            if (state.transcript.isNotBlank() &&
                state.recorderState in listOf(VoiceRecorderState.PARSING, VoiceRecorderState.RESULT)) {
                Spacer(Modifier.height(12.dp))
                TranscriptBadge(state.transcript)
            }

            // ── Error card ────────────────────────────────────────────────────
            val errorMsg = state.errorMessage
            if (state.recorderState == VoiceRecorderState.ERROR && errorMsg != null) {
                Spacer(Modifier.height(12.dp))
                ErrorCard(
                    message = errorMsg,
                    errorType = state.errorType,
                    onRetry = { viewModel.retryRecording() }
                )
            }

            // ── Parsed result editable card ───────────────────────────────────
            AnimatedVisibility(
                visible = state.recorderState == VoiceRecorderState.RESULT && state.parsedTransaction != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = fadeOut()
            ) {
                state.parsedTransaction?.let { parsed ->
                    Spacer(Modifier.height(12.dp))
                    ParsedResultCard(
                        parsed = parsed,
                        originalText = state.transcript,
                        onConfirm = { amount, type, category, merchant, note, date ->
                            viewModel.confirmAndSave(amount, type, category, merchant, note, date)
                        },
                        onRetry = { viewModel.retryRecording() }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Text fallback ─────────────────────────────────────────────────
            if (state.recorderState == VoiceRecorderState.IDLE ||
                state.recorderState == VoiceRecorderState.ERROR) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("  or type instead  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.typedInput,
                        onValueChange = { viewModel.updateTypedInput(it) },
                        placeholder = { Text("e.g. Spent \$12 on lunch at McDonald's") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            viewModel.parseTypedInput()
                        }),
                        trailingIcon = {
                            if (state.typedInput.isNotBlank()) {
                                IconButton(onClick = {
                                    keyboard?.hide()
                                    viewModel.parseTypedInput()
                                }) {
                                    Icon(Icons.Filled.Send, "Parse",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Status Chip ──────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(state: VoiceRecorderState) {
    val (icon, text, containerColor) = when (state) {
        VoiceRecorderState.IDLE          -> Triple(Icons.Filled.Mic, "Ready to record", MaterialTheme.colorScheme.surfaceVariant)
        VoiceRecorderState.RECORDING     -> Triple(Icons.Filled.RadioButtonChecked, "Recording…", MaterialTheme.colorScheme.errorContainer)
        VoiceRecorderState.TRANSCRIBING  -> Triple(Icons.Filled.Hearing, "Transcribing…", MaterialTheme.colorScheme.tertiaryContainer)
        VoiceRecorderState.PARSING       -> Triple(Icons.Filled.AutoAwesome, "AI Parsing…", MaterialTheme.colorScheme.secondaryContainer)
        VoiceRecorderState.RESULT        -> Triple(Icons.Filled.CheckCircle, "Review transaction", MaterialTheme.colorScheme.primaryContainer)
        VoiceRecorderState.SAVED         -> Triple(Icons.Filled.TaskAlt, "Saved to LedgerAI!", Color(0xFF22C55E).copy(alpha = 0.2f))
        VoiceRecorderState.ERROR         -> Triple(Icons.Filled.Warning, "Try again", MaterialTheme.colorScheme.errorContainer)
    }
    Surface(shape = RoundedCornerShape(50), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─── Idle Mic ─────────────────────────────────────────────────────────────────

@Composable
private fun IdleMicSection(recorderState: VoiceRecorderState, onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hint text
        Text(
            "Tap the mic and describe your transaction naturally",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Text(
            "\"Spent \$45 on groceries at Walmart\"\n\"Received \$3,200 salary from my employer\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))
        FilledIconButton(
            onClick = onStart,
            modifier = Modifier.size(100.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recorderState == VoiceRecorderState.ERROR)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Filled.Mic, "Start recording", Modifier.size(48.dp),
                tint = if (recorderState == VoiceRecorderState.ERROR)
                    MaterialTheme.colorScheme.onErrorContainer
                else Color.White)
        }
    }
}

// ─── Recording with waveform ──────────────────────────────────────────────────

@Composable
private fun RecordingSection(
    seconds: Int,
    amplitude: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f + amplitude * 0.5f,
        animationSpec = infiniteRepeatable(tween(500, easing = EaseInOut), RepeatMode.Reverse),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f + amplitude * 0.4f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOut), RepeatMode.Reverse),
        label = "ring2"
    )
    val dotPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Timer
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).scale(dotPulse).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Pulsing mic with amplitude rings
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(Modifier.size(160.dp).scale(ring2).clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)))
            Box(Modifier.size(130.dp).scale(ring1).clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)))
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(100.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Stop, "Stop recording", Modifier.size(40.dp), tint = Color.White)
            }
        }

        Text("Tap to stop and transcribe",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        TextButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel")
        }
    }
}

// ─── Processing Spinner ───────────────────────────────────────────────────────

@Composable
private fun ProcessingSection(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 5.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ─── Transcript badge ─────────────────────────────────────────────────────────

@Composable
private fun TranscriptBadge(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.RecordVoiceOver, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("\"$text\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Error Card ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorCard(message: String, errorType: ErrorType, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    when (errorType) {
                        ErrorType.AUDIO_UNCLEAR -> "Audio Unclear"
                        ErrorType.NETWORK -> "Transcription Failed"
                        ErrorType.PARSE_FAILED -> "Couldn't Parse"
                        ErrorType.PERMISSION -> "Permission Needed"
                        ErrorType.NONE -> "Error"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try Again")
            }
        }
    }
}

// ─── Parsed Result Card ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParsedResultCard(
    parsed: ParsedTransaction,
    originalText: String,
    onConfirm: (Double, TransactionType, TransactionCategory, String, String, LocalDate) -> Unit,
    onRetry: () -> Unit
) {
    var amount by remember { mutableStateOf(parsed.amount?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "") }
    var merchant by remember { mutableStateOf(parsed.merchant) }
    var note by remember { mutableStateOf(originalText) }
    var selectedType by remember { mutableStateOf(parsed.type) }
    var selectedCategory by remember { mutableStateOf(parsed.category) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("AI Parsed Transaction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Income / Expense toggle
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (selectedType == type) ({
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                        }) else null
                    )
                }
            }

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount *") },
                prefix = { Text("$") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amount.toDoubleOrNull() == null && amount.isNotBlank(),
                supportingText = if (amount.isNotBlank() && amount.toDoubleOrNull() == null)
                    ({ Text("Enter a valid number") }) else null
            )

            // Merchant
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Source") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Store, null) }
            )

            // Category dropdown
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(
                    value = selectedCategory.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Filled.Category, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    TransactionCategory.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.displayName) },
                            onClick = { selectedCategory = cat; categoryExpanded = false }
                        )
                    }
                }
            }

            // Note (transcript text, editable)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (from transcript)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                leadingIcon = { Icon(Icons.Filled.Notes, null) }
            )

            // Action row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Re-record")
                }
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: return@Button
                        onConfirm(amt, selectedType, selectedCategory, merchant, note, LocalDate.now())
                    },
                    modifier = Modifier.weight(1f),
                    enabled = amount.toDoubleOrNull()?.let { it > 0 } == true
                ) {
                    Icon(Icons.Filled.SaveAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}

// ─── Saved ────────────────────────────────────────────────────────────────────

@Composable
private fun SavedSection() {
    val scale by animateFloatAsState(1f, spring(Spring.DampingRatioMediumBouncy), label = "")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(90.dp).scale(scale).clip(CircleShape).background(Color(0xFF22C55E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Text("Transaction saved!", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
        Text("Added to your LedgerAI database",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
