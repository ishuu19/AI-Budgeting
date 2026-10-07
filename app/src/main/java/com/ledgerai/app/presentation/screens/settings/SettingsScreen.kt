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
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currency: String = "USD",
    val currencySymbol: String = "$",
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
        prefs.notificationsEnabled,
        prefs.budgetAlertThreshold
    ) { currency, symbol, notifs, threshold ->
        SettingsUiState(currency, symbol, notifs, threshold)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setNotifications(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setAlertThreshold(threshold: Int) = viewModelScope.launch { prefs.setBudgetAlertThreshold(threshold) }
    fun setCurrency(currency: String, symbol: String) = viewModelScope.launch { prefs.setCurrency(currency, symbol) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val userInfo by authViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var thresholdSlider by remember(state.budgetAlertThreshold) {
        mutableStateOf(state.budgetAlertThreshold.toFloat())
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { SettingsSectionTitle("AI") }
            item {
                SettingsRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Cloud AI",
                    subtitle = "Gemini via Supabase Edge Functions — Phase 7"
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item { SettingsSectionTitle("Notifications") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Push Notifications", fontWeight = FontWeight.Medium)
                            Text(
                                "Budget alerts, debt reminders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotifications(it) }
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Budget Alert Threshold: ${thresholdSlider.toInt()}%",
                            fontWeight = FontWeight.Medium
                        )
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
            item { SettingsSectionTitle("Account") }
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    if (userInfo.isSignedIn) "Local session" else "Not signed in",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Supabase Google auth arrives in Phase 2",
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
                    subtitle = "Version 1.0 • Phase 1 foundation"
                )
            }
        }
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            onDismiss = { showCurrencyDialog = false },
            onSelect = { currency, symbol ->
                viewModel.setCurrency(currency, symbol)
                showCurrencyDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)

    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
