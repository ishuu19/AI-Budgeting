package com.ledgerai.app.presentation.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.security.AppLock
import com.ledgerai.app.data.security.UnlockFailure

/**
 * Nothing below this is composed until the fingerprint passed, so no screen loads or shows any data
 * while the app is locked. Every return to the app after being locked asks again.
 */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    val unlocked by AppLock.unlocked.collectAsStateWithLifecycle()
    if (unlocked) {
        content()
        return
    }
    var failure by remember { mutableStateOf<UnlockFailure?>(null) }
    val ask = { AppLock.unlock(activity, onUnlocked = { failure = null }, onFailed = { failure = it }) }

    // Asks each time the app comes to the front while locked (the observer replays RESUMED on first show).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) ask() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current
    val confirmPin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // The phone's own PIN was confirmed after the fingerprints changed: set up a fresh key.
            AppLock.resetKey()
            failure = null
            ask()
        }
    }

    Column(
        Modifier.fillMaxSize().background(L.Page).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = L.Accent, modifier = Modifier.size(72.dp))
        Text("Ledger AI is locked", style = MaterialTheme.typography.headlineSmall, color = L.Ink)
        Text(
            when (failure) {
                UnlockFailure.NOT_ENROLLED -> "No fingerprint is set up on this phone. Add one in the phone's settings, then come back."
                UnlockFailure.UNAVAILABLE -> "The fingerprint sensor isn't available right now."
                UnlockFailure.BIOMETRICS_CHANGED -> "Fingerprints on this phone were changed. Confirm the phone's PIN to set the lock up again."
                UnlockFailure.NOT_PROVEN -> "The fingerprint couldn't be verified. Try again."
                else -> "Use your fingerprint to open it."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = L.InkMuted,
            textAlign = TextAlign.Center,
        )
        when (failure) {
            UnlockFailure.NOT_ENROLLED -> LButton("Open phone settings", {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            UnlockFailure.BIOMETRICS_CHANGED -> LButton("Confirm phone PIN", {
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                @Suppress("DEPRECATION")
                keyguard.createConfirmDeviceCredentialIntent("Set up the app lock again", "Fingerprints changed")
                    ?.let { confirmPin.launch(it) }
            })
            else -> LButton("Unlock with fingerprint", { ask() })
        }
    }
}
