package com.ledgerai.app.presentation.screens.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.service.FocusForegroundService
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(blockId: Long, topic: String = "Focus", onDone: () -> Unit) {
    val context = LocalContext.current
    var seconds by remember { mutableIntStateOf(50 * 60) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds--
        }
        if (seconds == 0) onDone()
    }

    Column(
        Modifier.fillMaxSize().background(L.Page).padding(L.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineMedium, color = L.Ink)
        Spacer(Modifier.height(24.dp))
        LHero(
            label = topic,
            value = "%02d:%02d".format(seconds / 60, seconds % 60)
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LButton(if (running) "Pause" else "Resume", onClick = { running = !running })
            LButton("End", onClick = {
                context.startService(
                    Intent(context, FocusForegroundService::class.java).apply {
                        action = FocusForegroundService.ACTION_STOP
                    }
                )
                onDone()
            })
        }
    }
}
