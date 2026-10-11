package com.ledgerai.app.presentation.screens.focus

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.domain.model.HabitOutcome
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LConfirm
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.service.FocusForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

private const val DEFAULT_MINUTES = 50

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val planRepo: PlanRepository,
    private val habitRepo: HabitRepository
) : ViewModel() {

    /** Length of the block in minutes, or null when it is free focus or cannot be found. */
    suspend fun minutesFor(blockId: Long): Int? = when {
        blockId > 0 -> planRepo.getBlock(blockId)
            ?.let { Duration.between(it.startAt, it.endAt).toMinutes().toInt() }
            ?.takeIf { it > 0 }
        blockId < 0 -> habitRepo.getById(-blockId)?.durationMinutes?.takeIf { it > 0 }
        else -> null
    }

    /** Marks the block (or habit) done with the minutes actually spent. */
    fun finish(blockId: Long, minutes: Int) {
        viewModelScope.launch {
            when {
                blockId > 0 -> planRepo.markBlockDone(blockId, minutes)
                blockId < 0 -> habitRepo.logOutcome(-blockId, HabitOutcome.DONE, minutes)
            }
        }
    }
}

/** blockId > 0 study block, < 0 habit, 0 free focus. Timer state survives rotation. */
@Composable
fun FocusScreen(
    blockId: Long,
    topic: String = "Focus",
    onDone: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var totalSeconds by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(-1) }
    var endsAt by rememberSaveable { mutableLongStateOf(0L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(blockId) {
        if (left < 0) {
            val minutes = viewModel.minutesFor(blockId) ?: DEFAULT_MINUTES
            totalSeconds = minutes * 60
            left = totalSeconds
            endsAt = System.currentTimeMillis() + left * 1000L
            running = true
        }
    }

    val shown = if (running) (((endsAt - now) + 999) / 1000).toInt().coerceAtLeast(0) else left.coerceAtLeast(0)

    LaunchedEffect(running, endsAt) {
        while (running) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    fun stopService() {
        context.startService(
            Intent(context, FocusForegroundService::class.java).apply { action = FocusForegroundService.ACTION_STOP }
        )
    }

    fun finish() {
        if (finished) return
        finished = true
        val minutes = ((totalSeconds - shown) / 60).coerceAtLeast(1)
        viewModel.finish(blockId, minutes)
        stopService()
        onDone()
    }

    LaunchedEffect(shown, running) {
        if (running && left >= 0 && shown <= 0) finish()
    }

    val totalMin = (totalSeconds / 60).coerceAtLeast(1)
    val leftMin = shown / 60
    val remaining = if (totalSeconds == 0) 0f else shown / totalSeconds.toFloat()

    Column(
        Modifier.fillMaxSize().background(L.Page).padding(L.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            topic.ifBlank { "Focus" },
            style = MaterialTheme.typography.headlineMedium,
            color = L.Ink,
            modifier = Modifier.semantics { heading() }
        )
        if (left < 0) {
            LLoading()
        } else {
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .size(160.dp)
                    .semantics { contentDescription = "$leftMin of $totalMin minutes" },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = L.BoxDeep,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = L.Gold,
                        startAngle = -90f,
                        sweepAngle = 360f * remaining.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "%02d:%02d".format(shown / 60, shown % 60),
                    style = MaterialTheme.typography.displaySmall,
                    color = L.OnBox
                )
            }
            Text(
                "$leftMin of $totalMin min",
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LButton(
                    if (running) "Pause" else "Resume",
                    onClick = {
                        if (running) {
                            left = shown
                            running = false
                        } else {
                            endsAt = System.currentTimeMillis() + left * 1000L
                            now = System.currentTimeMillis()
                            running = true
                        }
                    }
                )
                LGhostButton("Finish", onClick = { finish() })
                LGhostButton("End without saving", { confirmEnd = true }, destructive = true)
            }
        }
    }

    if (confirmEnd) {
        LConfirm(
            title = "End without saving?",
            confirm = "End",
            onConfirm = {
                confirmEnd = false
                finished = true
                stopService()
                onDone()
            },
            onDismiss = { confirmEnd = false }
        )
    }
}
