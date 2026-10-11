package com.ledgerai.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.screens.capture.CaptureComposer
import com.ledgerai.app.presentation.screens.capture.CaptureEvent
import com.ledgerai.app.presentation.screens.capture.CaptureViewModel
import com.ledgerai.app.presentation.theme.LedgerAITheme
import com.ledgerai.app.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app without opening the app. A floating sheet over whatever is on screen, with the same
 * one-box composer as the Capture tab. Reached from the system assistant gesture, the quick-settings
 * tile, launcher shortcuts, and "Share" from the gallery.
 */
@AndroidEntryPoint
class QuickCaptureActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)
        val shared = sharedImages(intent)
        if (savedInstanceState == null && shared.isNotEmpty()) vm.addImages(shared)

        setContent {
            LedgerAITheme {
                LaunchedEffect(Unit) {
                    vm.events.collect { event ->
                        when (event) {
                            is CaptureEvent.HandOff -> {
                                startActivity(WidgetActions.openVoiceReview(this@QuickCaptureActivity, event.text))
                                finish()
                            }
                            is CaptureEvent.Saved -> {
                                Toast.makeText(
                                    this@QuickCaptureActivity,
                                    "Saved. I'll sort it out in the background.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                finish()
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        color = L.Page,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        Column(
                            Modifier
                                .navigationBarsPadding()
                                .imePadding()
                                .heightIn(max = 640.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Add or ask",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = L.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = L.InkMuted,
                                    modifier = Modifier.clickable { finish() }.padding(8.dp),
                                )
                            }
                            CaptureComposer(
                                vm = vm,
                                autoMic = mode == MODE_VOICE && shared.isEmpty(),
                                autoCamera = mode == MODE_CAMERA && shared.isEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sharedImages(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
        Intent.ACTION_SEND_MULTIPLE -> streamsExtra(intent)
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamsExtra(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.ledgerai.app.action.QUICK_CAPTURE"
        const val EXTRA_MODE = "capture_mode"
        const val MODE_VOICE = "voice"
        const val MODE_CAMERA = "camera"

        fun intent(context: Context, mode: String? = null): Intent =
            Intent(context, QuickCaptureActivity::class.java)
                .setAction(ACTION_QUICK_CAPTURE)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
