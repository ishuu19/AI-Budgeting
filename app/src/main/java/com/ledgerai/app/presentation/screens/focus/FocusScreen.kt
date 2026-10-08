package com.ledgerai.app.presentation.screens.focus

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
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
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LHero
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

    Column(
        Modifier.fillMaxSize().background(L.Page).padding(L.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Focus",
            style = MaterialTheme.typography.headlineMedium,
            color = L.Ink,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(24.dp))
        if (left < 0) {
            LLoading()
        } else {
            LHero(label = topic, value = "%02d:%02d".format(shown / 60, shown % 60))
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LButton(
                        if (running) "Pause" else "Resume",
                        modifier = Modifier.weight(1f),
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
                    LButton("Finish", modifier = Modifier.weight(1f), onClick = { finish() })
                }
                LGhostButton("Abandon", onClick = {
                    stopService()
                    onDone()
                })
            }
        }
    }
}
