package com.ledgerai.app.service

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerai.app.presentation.theme.LedgerAITheme

/**
 * Full-screen ringing UI shown over the lock screen via full-screen intent.
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val alarmId = intent.getLongExtra(AlarmRingingService.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_LABEL) ?: "Alarm"

        setContent {
            LedgerAITheme {
                AlarmRingingScreen(
                    label = label,
                    onDismiss = {
                        sendService(AlarmRingingService.ACTION_DISMISS, alarmId)
                        finish()
                    },
                    onSnooze = { minutes ->
                        sendService(AlarmRingingService.ACTION_SNOOZE, alarmId, minutes)
                        finish()
                    }
                )
            }
        }
    }

    private fun sendService(action: String, alarmId: Long, snoozeMinutes: Int? = null) {
        val intent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            if (snoozeMinutes != null) {
                putExtra(AlarmRingingService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            }
        }
        startService(intent)
    }
}

@Composable
private fun AlarmRingingScreen(
    label: String,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Alarm",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Dismiss", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { onSnooze(5) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) { Text("Snooze 5m") }
            OutlinedButton(
                onClick = { onSnooze(10) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) { Text("Snooze 10m") }
        }
    }
}
