package com.ledgerai.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.voice.OfflineSttEngine
import com.ledgerai.app.data.voice.OfflineVoiceEngine
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val currency: String = "USD",
    val currencySymbol: String = "$",
    val notificationsEnabled: Boolean = true,
    val budgetAlertThreshold: Int = 80,
    val voiceOnlyWidget: Boolean = false,
    val voiceEngine: String = "sherpa"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val offline: OfflineSttEngine
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            prefs.currency,
            prefs.currencySymbol,
            prefs.notificationsEnabled,
            prefs.budgetAlertThreshold,
            prefs.voiceOnlyWidget
        ) { currency, symbol, notifs, threshold, voiceOnly ->
            SettingsUiState(currency, symbol, notifs, threshold, voiceOnly)
        },
        prefs.voiceEngine
    ) { base, engine -> base.copy(voiceEngine = engine) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** Engine id -> download fraction (0..1) while a prepare is running. */
    private val _downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloads: StateFlow<Map<String, Float>> = _downloads.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    /** Bumped after each prepare so non-reactive [engineStatus] is re-read. */
    private val _statusTick = MutableStateFlow(0)
    val statusTick: StateFlow<Int> = _statusTick.asStateFlow()

    fun setNotifications(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setAlertThreshold(threshold: Int) = viewModelScope.launch { prefs.setBudgetAlertThreshold(threshold) }
    fun setCurrency(currency: String, symbol: String) = viewModelScope.launch { prefs.setCurrency(currency, symbol) }
    val trackMode = prefs.trackMode
    val cashOnHand = prefs.cashOnHand
    fun setTrackMode(mode: String) = viewModelScope.launch { prefs.setTrackMode(mode) }
    fun setCashOnHand(amount: String) = viewModelScope.launch { prefs.setCashOnHand(amount) }
    fun setVoiceOnlyWidget(enabled: Boolean) = viewModelScope.launch { prefs.setVoiceOnlyWidget(enabled) }
    fun setVoiceEngine(id: String) = viewModelScope.launch { prefs.setVoiceEngine(id) }
    fun engineStatus(engine: OfflineVoiceEngine) = offline.status(engine)

    fun prepare(engine: OfflineVoiceEngine) {
        if (_downloads.value.containsKey(engine.id)) return
        viewModelScope.launch {
            _failed.update { it - engine.id }
            _downloads.update { it + (engine.id to 0f) }
            val result = offline.prepare(engine) { p ->
                _downloads.update { m -> if (m.containsKey(engine.id)) m + (engine.id to p) else m }
            }
            _downloads.update { it - engine.id }
            if (result.isFailure) _failed.update { it + engine.id }
            _statusTick.update { it + 1 }
        }
    }
}

@Composable
fun SettingsScreen(
    onOpenAi: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val user by authViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val statusTick by viewModel.statusTick.collectAsState()

    var showCurrency by remember { mutableStateOf(false) }
    var showTrack by remember { mutableStateOf(false) }
    var showCash by remember { mutableStateOf(false) }
    val trackMode by viewModel.trackMode.collectAsState(initial = "both")
    val cashOnHand by viewModel.cashOnHand.collectAsState(initial = "")
    var showAlert by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val isLocal = user.userId.isBlank() || user.userId.startsWith("local-")

    LScreen(title = "You") {
        item { ProfileCard(name = user.displayName, email = user.email, isLocal = isLocal) }

        item { LSection("Voice") }
        OfflineVoiceEngine.entries.forEach { engine ->
            item(key = "engine-${engine.id}") {
                val status = remember(engine, statusTick, downloads.containsKey(engine.id)) {
                    viewModel.engineStatus(engine)
                }
                EngineRow(
                    engine = engine,
                    selected = state.voiceEngine == engine.id,
                    status = status,
                    progress = downloads[engine.id],
                    failed = engine.id in failed,
                    onSelect = { viewModel.setVoiceEngine(engine.id) },
                    onGet = { viewModel.prepare(engine) }
                )
            }
        }
        item { LRow(title = "Ask AI", icon = Icons.Filled.AutoAwesome, onClick = onOpenAi) }

        item { LSection("App") }
        item {
            LRow(
                title = "Currency",
                icon = Icons.Filled.CurrencyExchange,
                trailing = state.currency,
                onClick = { showCurrency = true }
            )
        }
        item {
            LRow(
                title = "Track",
                icon = Icons.Filled.AccountBalanceWallet,
                trailing = if (trackMode == "expenses") "Expenses" else "Both",
                onClick = { showTrack = true }
            )
        }
        item {
            LRow(
                title = "On hand",
                icon = Icons.Filled.Savings,
                trailing = cashOnHand.ifBlank { "—" },
                onClick = { showCash = true }
            )
        }
        item {
            LRow(
                title = "Alerts",
                icon = Icons.Filled.Notifications,
                onClick = { viewModel.setNotifications(!state.notificationsEnabled) },
                end = {
                    LSwitch(state.notificationsEnabled) { viewModel.setNotifications(it) }
                }
            )
        }
        item {
            LRow(
                title = "Threshold",
                icon = Icons.Filled.Percent,
                trailing = "${state.budgetAlertThreshold}%",
                onClick = { showAlert = true }
            )
        }
        item {
            LRow(
                title = "Widget quote",
                icon = Icons.Filled.Widgets,
                onClick = { viewModel.setVoiceOnlyWidget(!state.voiceOnlyWidget) },
                end = {
                    LSwitch(!state.voiceOnlyWidget) { viewModel.setVoiceOnlyWidget(!it) }
                }
            )
        }

        item { LSection("Account") }
        item {
            LRow(
                title = "Sync",
                icon = Icons.Filled.Sync,
                trailing = if (isLocal) "Off" else "On"
            )
        }
        item {
            LRow(title = "Privacy", icon = Icons.Filled.PrivacyTip, onClick = { showPrivacy = true })
        }
        item { LRow(title = "Version", icon = Icons.Filled.Info, trailing = "1.0") }
        if (user.isSignedIn) {
            item {
                LGhostButton(
                    "Sign out",
                    onClick = { authViewModel.signOut() },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showTrack) {
        LSheet(
            title = "Track",
            onDismiss = { showTrack = false },
            primary = "Done",
            onPrimary = { showTrack = false }
        ) {
            LChip("Expenses", trackMode == "expenses", onClick = { viewModel.setTrackMode("expenses") })
            LChip("Both", trackMode != "expenses", onClick = { viewModel.setTrackMode("both") })
        }
    }

    if (showCash) {
        var amount by remember { mutableStateOf(cashOnHand) }
        LSheet(
            title = "On hand",
            onDismiss = { showCash = false },
            primary = "Save",
            onPrimary = {
                viewModel.setCashOnHand(amount)
                showCash = false
            },
            secondary = "Clear",
            onSecondary = {
                viewModel.setCashOnHand("")
                showCash = false
            }
        ) {
            LField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount")
        }
    }

    if (showCurrency) {
        CurrencyPopup(
            selected = state.currency,
            onPick = { code, symbol ->
                viewModel.setCurrency(code, symbol)
                showCurrency = false
            },
            onDismiss = { showCurrency = false }
        )
    }

    if (showAlert) {
        var value by remember { mutableStateOf(state.budgetAlertThreshold.toFloat()) }
        LSheet(
            title = "Threshold",
            onDismiss = { showAlert = false },
            primary = "Save",
            onPrimary = {
                viewModel.setAlertThreshold(value.toInt())
                showAlert = false
            }
        ) {
            Text("${value.toInt()}%", style = MaterialTheme.typography.headlineMedium, color = L.Box)
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 50f..95f,
                colors = SliderDefaults.colors(
                    thumbColor = L.Box,
                    activeTrackColor = L.Box,
                    inactiveTrackColor = L.Line
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showPrivacy) {
        LSheet(
            title = "Privacy",
            onDismiss = { showPrivacy = false },
            primary = "Done",
            onPrimary = { showPrivacy = false }
        ) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(PRIVACY_POLICY_TEXT, style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
            }
        }
    }
}

@Composable
private fun ProfileCard(name: String, email: String, isLocal: Boolean) {
    val shownName = name.ifBlank { if (isLocal) "Local" else "You" }
    val initial = (name.ifBlank { email }).firstOrNull()?.uppercaseChar()?.toString() ?: "L"
    LCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(L.Gold),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, style = MaterialTheme.typography.titleLarge, color = L.BoxDeep)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    shownName,
                    style = MaterialTheme.typography.titleMedium,
                    color = L.OnBox,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email.ifBlank { "On device" },
                    style = MaterialTheme.typography.bodySmall,
                    color = L.OnBoxMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EngineRow(
    engine: OfflineVoiceEngine,
    selected: Boolean,
    status: String,
    progress: Float?,
    failed: Boolean,
    onSelect: () -> Unit,
    onGet: () -> Unit
) {
    val needsGet = status != "Ready" && status != "On demand"
    LRow(
        title = engine.label,
        sub = if (progress != null) "${(progress.coerceIn(0f, 1f) * 100).toInt()}%" else engine.sizeHint,
        onClick = onSelect,
        end = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    progress != null -> Box(Modifier.width(56.dp)) { LProgress(progress) }
                    needsGet || failed -> Text(
                        if (failed) "Retry" else "Get",
                        style = MaterialTheme.typography.labelLarge,
                        color = L.Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onGet)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = L.Gold, modifier = Modifier.size(20.dp))
                } else {
                    Spacer(Modifier.size(20.dp))
                }
            }
        }
    )
}

@Composable
private fun LSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = L.BoxDeep,
            checkedTrackColor = L.Gold,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = L.OnBoxMuted,
            uncheckedTrackColor = L.BoxDeep,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun CurrencyPopup(
    selected: String,
    onPick: (code: String, symbol: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        Currency.getAvailableCurrencies()
            .filter { it.currencyCode.length == 3 }
            .sortedBy { it.currencyCode }
            .map { currency ->
                val symbol = currency.getSymbol(Locale.getDefault()).ifBlank { currency.currencyCode }
                Triple(currency.currencyCode, symbol, currency.getDisplayName(Locale.getDefault()))
            }
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) all else all.filter { (code, symbol, name) ->
        code.contains(q, ignoreCase = true) ||
            name.contains(q, ignoreCase = true) ||
            symbol.contains(q, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(L.Radius))
                .background(L.Page)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Currency", style = MaterialTheme.typography.titleLarge, color = L.Ink)
            LField(value = query, onValueChange = { query = it }, label = "Search")
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.first }) { (code, symbol, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(code, symbol) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            code,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected == code) L.Box else L.Ink,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = L.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            symbol,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected == code) L.Gold else L.Ink
                        )
                    }
                }
            }
        }
    }
}

private val PRIVACY_POLICY_TEXT = """
LedgerAI is designed offline-first. Your transactions, budgets, tasks, notes, and alarms are stored on this device by default.

What we process locally
• Voice transcripts and text you enter for parsing into transactions
• On-device summaries used to build compact AI context
• Preferences such as currency, notifications, and widget display

Cloud AI (optional)
• When signed in, prompts may be sent through a Supabase Edge Function so API keys never ship in the release APK
• Do not include secrets or passwords in notes or voice input

Accounts
• Google sign-in via Supabase Auth; or continue locally with data on-device only
• You can clear app data from system settings to remove local records

We do not sell personal financial data. This summary is product guidance, not legal advice. Contact the app owner for formal policy updates.
""".trimIndent()
