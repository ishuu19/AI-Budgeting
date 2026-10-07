package com.ledgerai.app.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val currency: String = "USD",
    val currencySymbol: String = "$",
    val aiModel: String = BuildConfig.AI_MODEL,
    val notificationsEnabled: Boolean = true,
    val budgetAlertThreshold: Int = 80
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.currency,
        prefs.currencySymbol,
        prefs.aiModel,
        prefs.notificationsEnabled,
        prefs.budgetAlertThreshold
    ) { currency, symbol, model, notifs, threshold ->
        SettingsUiState(currency, symbol, model, notifs, threshold)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setAiModel(model: String) = viewModelScope.launch { prefs.setAiModel(model) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setAlertThreshold(threshold: Int) = viewModelScope.launch { prefs.setBudgetAlertThreshold(threshold) }
    fun setCurrency(currency: String, symbol: String) = viewModelScope.launch { prefs.setCurrency(currency, symbol) }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val userInfo by authViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var thresholdSlider by remember(state.budgetAlertThreshold) { mutableStateOf(state.budgetAlertThreshold.toFloat()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { SettingsSectionTitle("AI Configuration") }
            item {
                SettingsRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "AI Model",
                    subtitle = state.aiModel,
                    onClick = { showModelDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item { SettingsSectionTitle("Notifications") }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Push Notifications", fontWeight = FontWeight.Medium)
                            Text("Budget alerts, debt reminders", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = state.notificationsEnabled, onCheckedChange = { viewModel.setNotifications(it) })
                }
            }

            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Budget Alert Threshold: ${thresholdSlider.toInt()}%", fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = thresholdSlider,
                        onValueChange = { thresholdSlider = it },
                        onValueChangeFinished = { viewModel.setAlertThreshold(thresholdSlider.toInt()) },
                        valueRange = 50f..95f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { SettingsSectionTitle("Currency") }
            item {
                SettingsRow(
                    icon = Icons.Filled.CurrencyExchange,
                    title = "Currency",
                    subtitle = "${state.currencySymbol} ${state.currency}",
                    onClick = { showCurrencyDialog = true }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { SettingsSectionTitle("Google Account") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Signed in", fontWeight = FontWeight.Medium)
                                Text(
                                    if (userInfo.isSignedIn) "Google account linked • Calendar enabled"
                                    else "Not signed in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { authViewModel.signOut() }) {
                            Text("Sign Out", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { SettingsSectionTitle("About") }
            item {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "LedgerAI",
                    subtitle = "Version 1.0 • Powered by OpenRouter"
                )
            }
        }
    }

    if (showModelDialog) {
        ModelSelectDialog(
            currentModel = state.aiModel,
            onDismiss = { showModelDialog = false },
            onSelect = { model -> viewModel.setAiModel(model); showModelDialog = false }
        )
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            onDismiss = { showCurrencyDialog = false },
            onSelect = { currency, symbol -> viewModel.setCurrency(currency, symbol); showCurrencyDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth()
        .padding(vertical = 6.dp) else Modifier.fillMaxWidth().padding(vertical = 6.dp)

    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModelSelectDialog(currentModel: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val models = listOf(
        "anthropic/claude-3.7-sonnet" to "Claude 3.7 Sonnet (Best)",
        "anthropic/claude-3.5-sonnet" to "Claude 3.5 Sonnet (Fast)",
        "google/gemini-2.0-flash" to "Gemini 2.0 Flash (Affordable)",
        "openai/gpt-4o" to "GPT-4o (General Purpose)",
        "openai/gpt-4o-mini" to "GPT-4o Mini (Budget)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select AI Model") },
        text = {
            Column {
                models.forEach { (id, label) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = currentModel == id, onClick = { onSelect(id) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(id, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CurrencyDialog(onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    val currencies = listOf("USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CAD" to "C$", "AUD" to "A$")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            Column {
                currencies.forEach { (code, symbol) ->
                    TextButton(onClick = { onSelect(code, symbol) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$symbol $code", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
