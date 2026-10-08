package com.ledgerai.app.presentation.screens.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LLogo

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onSignedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(L.Page)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        LLogo(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("LedgerAI", style = MaterialTheme.typography.headlineMedium, color = L.Ink)
        Spacer(Modifier.height(4.dp))
        Text("Money. Simply.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        Spacer(Modifier.weight(1f))

        state.errorMessage?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }

        if (state.isLoading) {
            Box(Modifier.height(52.dp + 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = L.Box)
            }
        } else if (state.googleSignInAvailable) {
            GreenButton(
                text = "Continue with Google",
                enabled = activity != null,
                onClick = { activity?.let { viewModel.signInWithGoogle(it) } }
            )
            TextButton(
                onClick = { viewModel.continueLocally() },
                modifier = Modifier.padding(top = 4.dp).height(44.dp)
            ) {
                Text("Skip", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            }
        } else {
            GreenButton(text = "Continue", onClick = { viewModel.continueLocally() })
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun GreenButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(L.RadiusSm),
        colors = ButtonDefaults.buttonColors(containerColor = L.Box, contentColor = L.OnBox)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
