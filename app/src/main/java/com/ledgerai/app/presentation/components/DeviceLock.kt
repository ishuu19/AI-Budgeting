package com.ledgerai.app.presentation.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** How long an unlock stays valid after it succeeds. Shared by Passwords and locked Vault folders. */
object UnlockSession {
    private const val WINDOW_MS = 2 * 60 * 1000L
    private var until = 0L

    fun isOpen(now: Long = System.currentTimeMillis()): Boolean = now < until
    fun open(now: Long = System.currentTimeMillis()) { until = now + WINDOW_MS }
    fun close() { until = 0L }
}

fun isDeviceSecure(context: Context): Boolean =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

/**
 * Returns a function that asks for the phone's screen lock (PIN, pattern, password or biometric).
 * [onUnlocked] runs only after the user passes. [onUnavailable] runs when the phone has no screen lock.
 */
@Composable
fun rememberDeviceUnlock(onUnlocked: () -> Unit, onUnavailable: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val unlocked = rememberUpdatedState(onUnlocked)
    val unavailable = rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            UnlockSession.open()
            unlocked.value()
        }
    }
    return remember(launcher) {
        {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (UnlockSession.isOpen()) {
                unlocked.value()
            } else if (!keyguard.isDeviceSecure) {
                unavailable.value()
            } else {
                @Suppress("DEPRECATION")
                val intent = keyguard.createConfirmDeviceCredentialIntent("Unlock", "Confirm it is you")
                if (intent == null) unavailable.value() else launcher.launch(intent)
            }
        }
    }
}

/** No screenshots, screen recording or recents thumbnail while this is on screen. */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
