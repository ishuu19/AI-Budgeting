package com.ledgerai.app.presentation.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.voice.OfflineSttEngine
import com.ledgerai.app.data.voice.OfflineVoiceEngine
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LSnackbar
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import com.ledgerai.app.widget.FocusWidget
import com.ledgerai.app.widget.HomeWidget
import com.ledgerai.app.widget.QuickActionsWidget
import com.ledgerai.app.widget.WidgetPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val currency: String = "USD",
    val currencySymbol: String = "$",
    val notificationsEnabled: Boolean = true,
    val budgetAlertThreshold: Int = 80,
    val voiceEngine: String = "sherpa"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val offline: OfflineSttEngine,
    localModel: com.ledgerai.app.data.ai.LocalParseModel,
) : ViewModel() {

    val localModelStatus = localModel.status

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            prefs.currency,
            prefs.currencySymbol,
            prefs.notificationsEnabled,
            prefs.budgetAlertThreshold
        ) { currency, symbol, notifs, threshold ->
            SettingsUiState(currency, symbol, notifs, threshold)
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

    val themeMode = prefs.themeMode
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setAlertThreshold(threshold: Int) = viewModelScope.launch { prefs.setBudgetAlertThreshold(threshold) }

    /** Saves code and symbol together. MainActivity mirrors the symbol into LCurrency, so money() follows. */
    fun setCurrency(currency: String, symbol: String) = viewModelScope.launch { prefs.setCurrency(currency, symbol) }
    val trackMode = prefs.trackMode
    val cashOnHand = prefs.cashOnHand
    fun setTrackMode(mode: String) = viewModelScope.launch { prefs.setTrackMode(mode) }
    fun setCashOnHand(amount: String) = viewModelScope.launch { prefs.setCashOnHand(amount) }
    fun setVoiceEngine(id: String) = viewModelScope.launch { prefs.setVoiceEngine(id) }
    val cloudFallback = prefs.cloudFallback
    fun setCloudFallback(enabled: Boolean) = viewModelScope.launch { prefs.setCloudFallback(enabled) }
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

private enum class YouSheet { None, Profile, Look, Voice, Money, Alerts, Reminders, About, Privacy }

@Composable
fun SettingsScreen(
    links: com.ledgerai.app.presentation.navigation.AppLinks = com.ledgerai.app.presentation.navigation.AppLinks(),
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val user by authViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val trackMode by viewModel.trackMode.collectAsState(initial = "both")
    val cashOnHand by viewModel.cashOnHand.collectAsState(initial = "")
    val themeMode by viewModel.themeMode.collectAsState(initial = "system")

    var sheet by rememberSaveable { mutableStateOf(YouSheet.None) }
    val close = { sheet = YouSheet.None }

    val isLocal = user.userId.isBlank() || user.userId.startsWith("local-")
    val engineLabel = OfflineVoiceEngine.entries.firstOrNull { it.id == state.voiceEngine }?.label ?: "Default"

    LScreen(title = "Settings", onBack = onBack) {
        item { LSection("Account") }
        item {
            LGroup {
                LGroupRow(
                    title = user.displayName.ifBlank { if (isLocal) "Local user" else "Profile" },
                    sub = user.email.ifBlank { "On this device" },
                    icon = Icons.Filled.Person,
                    trailing = if (isLocal) "Sign in" else null,
                    trailingColor = L.Primary,
                    onClick = { sheet = YouSheet.Profile },
                    end = { Chevron() }
                )
            }
        }
        item { LSection("Preferences") }
        item {
            LGroup {
                LGroupRow(
                    title = "Appearance",
                    sub = when (themeMode) { "light" -> "Light"; "dark" -> "Dark"; else -> "Follow system" },
                    icon = Icons.Filled.Palette,
                    onClick = { sheet = YouSheet.Look },
                    end = { Chevron() }
                )
                LGroupDivider()
                LGroupRow(
                    title = "Voice and AI",
                    sub = engineLabel,
                    icon = Icons.Filled.Mic,
                    onClick = { sheet = YouSheet.Voice },
                    end = { Chevron() }
                )
                LGroupDivider()
                LGroupRow(
                    title = "Money rules",
                    sub = "${state.currency} · ${if (trackMode == "expenses") "Spending" else "Income and spending"}",
                    icon = Icons.Filled.CurrencyExchange,
                    onClick = { sheet = YouSheet.Money },
                    end = { Chevron() }
                )
                LGroupDivider()
                LGroupRow(
                    title = "Alerts and widget",
                    sub = if (state.notificationsEnabled) "On · ${state.budgetAlertThreshold}%" else "Off",
                    icon = Icons.Filled.Notifications,
                    onClick = { sheet = YouSheet.Alerts },
                    end = { Chevron() }
                )
                LGroupDivider()
                LGroupRow(
                    title = "Reminders",
                    sub = "Birthdays and trips",
                    icon = Icons.Filled.Notifications,
                    onClick = { sheet = YouSheet.Reminders },
                    end = { Chevron() }
                )
            }
        }
        item { LSection("Shortcuts") }
        item {
            LGroup {
                LGroupRow(title = "Analysis", icon = Icons.Filled.Insights, onClick = links.analysis, end = { Chevron() })
            }
        }
        item { LSection("About") }
        item {
            LGroup {
                LGroupRow(
                    title = "About",
                    sub = "Version ${BuildConfig.VERSION_NAME}",
                    icon = Icons.Filled.Info,
                    onClick = { sheet = YouSheet.About },
                    end = { Chevron() }
                )
            }
        }
    }

    when (sheet) {
        YouSheet.None -> Unit
        YouSheet.Profile -> ProfileSheet(user = user, isLocal = isLocal, authViewModel = authViewModel, onDismiss = close)
        YouSheet.Look -> LSheet(title = "Appearance", onDismiss = close, primary = "Done", onPrimary = close) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
                    LChip(label, themeMode == id, onClick = { viewModel.setThemeMode(id) })
                }
            }
        }
        YouSheet.Voice -> VoiceSheet(viewModel = viewModel, selected = state.voiceEngine, onDismiss = close)
        YouSheet.Money -> MoneySheet(
            viewModel = viewModel,
            currency = state.currency,
            trackMode = trackMode,
            cashOnHand = cashOnHand,
            onDismiss = close
        )
        YouSheet.Reminders -> RemindersSheet(onDismiss = close)
        YouSheet.Alerts -> AlertsSheet(
            viewModel = viewModel,
            enabled = state.notificationsEnabled,
            threshold = state.budgetAlertThreshold,
            onDismiss = close
        )
        YouSheet.About -> AboutSheet(isLocal = isLocal, onPrivacy = { sheet = YouSheet.Privacy }, onDismiss = close)
        YouSheet.Privacy -> LSheet(title = "Privacy policy", onDismiss = { sheet = YouSheet.About }, primary = "Done", onPrimary = { sheet = YouSheet.About }) {
            Text(PRIVACY_POLICY_TEXT, style = MaterialTheme.typography.bodyMedium, color = L.Ink)
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = L.OnBoxMuted)
}

// --- profile -------------------------------------------------------------------------------

@Composable
private fun ProfileSheet(
    user: com.ledgerai.app.presentation.screens.auth.AuthUiState,
    isLocal: Boolean,
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    var confirmOut by rememberSaveable { mutableStateOf(false) }

    LSheet(
        title = "Profile",
        onDismiss = onDismiss,
        primary = when {
            !isLocal -> "Sign out"
            user.isLoading -> "Signing in"
            else -> "Sign in with Google"
        },
        onPrimary = {
            if (!isLocal) confirmOut = true
            else activity?.let { authViewModel.signInWithGoogle(it) }
        },
        primaryEnabled = !isLocal || (user.googleSignInAvailable && activity != null && !user.isLoading)
    ) {
        LGroup {
            LGroupRow(
                title = user.displayName.ifBlank { if (isLocal) "Local user" else "You" },
                sub = user.email.ifBlank { "On this device" },
                icon = Icons.Filled.Person
            )
            LGroupDivider()
            LGroupRow(title = "Sync", trailing = if (isLocal) "Off" else "On")
        }
        user.errorMessage?.let { LSnackbar(it) }
    }

    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            containerColor = L.Page,
            title = { Text("Sign out?", color = L.Ink) },
            confirmButton = {
                TextButton(
                    onClick = { confirmOut = false; onDismiss(); authViewModel.signOut() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Sign out", color = L.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmOut = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", color = L.InkMuted)
                }
            }
        )
    }
}

// --- voice and AI --------------------------------------------------------------------------

@Composable
private fun VoiceSheet(viewModel: SettingsViewModel, selected: String, onDismiss: () -> Unit) {
    val downloads by viewModel.downloads.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val statusTick by viewModel.statusTick.collectAsState()
    val cloudFallback by viewModel.cloudFallback.collectAsState(initial = true)

    LSheet(title = "Voice and AI", onDismiss = onDismiss, primary = "Done", onPrimary = onDismiss) {
        LGroup {
            LGroupRow(
                title = "Cloud fallback",
                sub = "When rules are unsure",
                onClick = { viewModel.setCloudFallback(!cloudFallback) },
                end = { LSwitch(cloudFallback, "Cloud fallback") { viewModel.setCloudFallback(it) } }
            )
        }
        LGroup {
            OfflineVoiceEngine.entries.forEachIndexed { index, engine ->
                if (index > 0) LGroupDivider()
                val status = remember(statusTick, engine) { viewModel.engineStatus(engine) }
                EngineRow(
                    engine = engine,
                    selected = selected == engine.id,
                    status = status,
                    progress = downloads[engine.id],
                    failed = engine.id in failed,
                    onSelect = { viewModel.setVoiceEngine(engine.id) },
                    onGet = { viewModel.prepare(engine) }
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
    LGroupRow(
        title = engine.label,
        sub = when {
            progress != null -> "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
            failed -> "Download failed"
            engine == OfflineVoiceEngine.ANDROID -> "Built in"
            needsGet -> engine.sizeHint
            else -> "Ready"
        },
        onClick = onSelect,
        end = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    progress != null -> Box(Modifier.width(56.dp)) { LProgress(progress) }
                    needsGet || failed -> LTextLink(if (failed) "Retry" else "Get", onGet)
                }
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = L.Primary, modifier = Modifier.size(20.dp))
                } else {
                    Box(Modifier.size(20.dp))
                }
            }
        }
    )
}

// --- money rules ---------------------------------------------------------------------------

@Composable
private fun MoneySheet(
    viewModel: SettingsViewModel,
    currency: String,
    trackMode: String,
    cashOnHand: String,
    onDismiss: () -> Unit
) {
    var cash by rememberSaveable(cashOnHand) { mutableStateOf(cashOnHand) }
    var picking by rememberSaveable { mutableStateOf(false) }

    LSheet(
        title = "Money rules",
        onDismiss = onDismiss,
        primary = "Done",
        onPrimary = onDismiss
    ) {
        LGroup {
            LGroupRow(
                title = "Currency",
                trailing = currency,
                onClick = { picking = true },
                end = { Chevron() }
            )
        }
        Text("Track", style = MaterialTheme.typography.titleSmall, color = L.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("Spending", trackMode == "expenses", onClick = { viewModel.setTrackMode("expenses") })
            LChip("Income and spending", trackMode != "expenses", onClick = { viewModel.setTrackMode("both") })
        }
        LField(
            value = cash,
            onValueChange = {
                val next = it.filter { ch -> ch.isDigit() || ch == '.' }
                cash = next
                viewModel.setCashOnHand(next)
            },
            label = "Cash on hand",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

    if (picking) {
        CurrencySheet(
            selected = currency,
            onPick = { code, symbol ->
                viewModel.setCurrency(code, symbol)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

private val POPULAR = listOf("USD", "EUR", "GBP", "INR", "CAD", "AUD", "JPY", "CNY", "CHF", "NZD", "SGD", "AED")

@Composable
private fun CurrencySheet(
    selected: String,
    onPick: (code: String, symbol: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember {
        Currency.getAvailableCurrencies()
            .filter { it.currencyCode.length == 3 }
            .sortedBy { it.currencyCode }
            .map { c ->
                val symbol = c.getSymbol(Locale.getDefault()).ifBlank { c.currencyCode }
                Triple(c.currencyCode, symbol, c.getDisplayName(Locale.getDefault()))
            }
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) {
        POPULAR.mapNotNull { code -> all.firstOrNull { it.first == code } }
    } else {
        all.filter { (code, symbol, name) ->
            code.contains(q, ignoreCase = true) ||
                name.contains(q, ignoreCase = true) ||
                symbol.contains(q, ignoreCase = true)
        }.take(30)
    }

    LSheet(title = "Currency", onDismiss = onDismiss, primary = "Done", onPrimary = onDismiss) {
        LField(value = query, onValueChange = { query = it }, label = "Search")
        LGroup {
            shown.forEachIndexed { index, (code, symbol, name) ->
                if (index > 0) LGroupDivider()
                LGroupRow(
                    title = code,
                    sub = name,
                    trailing = symbol,
                    onClick = { onPick(code, symbol) },
                    end = {
                        if (code == selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = L.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }
            if (shown.isEmpty()) LGroupRow(title = "No match")
        }
    }
}

// --- alerts and widget ---------------------------------------------------------------------

@Composable
private fun AlertsSheet(
    viewModel: SettingsViewModel,
    enabled: Boolean,
    threshold: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by rememberSaveable { mutableStateOf(threshold.toFloat()) }
    var theme by rememberSaveable { mutableStateOf(WidgetPrefs.theme(context)) }
    var hidden by rememberSaveable { mutableStateOf(WidgetPrefs.privateMode(context)) }

    fun refreshWidgets() {
        scope.launch {
            runCatching {
                HomeWidget().updateAll(context)
                QuickActionsWidget().updateAll(context)
                FocusWidget().updateAll(context)
            }
        }
    }

    LSheet(
        title = "Alerts and widget",
        onDismiss = {
            viewModel.setAlertThreshold(value.toInt())
            onDismiss()
        },
        primary = "Done",
        onPrimary = {
            viewModel.setAlertThreshold(value.toInt())
            onDismiss()
        }
    ) {
        LGroup {
            LGroupRow(
                title = "Notifications",
                icon = Icons.Filled.Notifications,
                onClick = { viewModel.setNotifications(!enabled) },
                end = { LSwitch(enabled, "Notifications") { viewModel.setNotifications(it) } }
            )
            LGroupDivider()
            LGroupRow(
                title = "Private widget",
                onClick = {
                    hidden = !hidden
                    WidgetPrefs.setPrivateMode(context, hidden)
                    refreshWidgets()
                },
                end = {
                    LSwitch(hidden, "Private widget") {
                        hidden = it
                        WidgetPrefs.setPrivateMode(context, it)
                        refreshWidgets()
                    }
                }
            )
        }
        Text("Budget alert at ${value.toInt()}%", style = MaterialTheme.typography.titleSmall, color = L.Ink)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { viewModel.setAlertThreshold(value.toInt()) },
            valueRange = 50f..95f,
            colors = SliderDefaults.colors(
                thumbColor = L.Primary,
                activeTrackColor = L.Primary,
                inactiveTrackColor = L.Line
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Budget alert threshold" }
        )
        Text("Widget theme", style = MaterialTheme.typography.titleSmall, color = L.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WidgetPrefs.THEME_SYSTEM to "System",
                WidgetPrefs.THEME_DARK to "Dark",
                WidgetPrefs.THEME_LIGHT to "Light"
            ).forEach { (id, label) ->
                LChip(label, theme == id, onClick = {
                    theme = id
                    WidgetPrefs.setTheme(context, id)
                    refreshWidgets()
                })
            }
        }
    }
}

@Composable
private fun LSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = L.OnPrimary,
            checkedTrackColor = L.Gold,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = L.OnBoxMuted,
            uncheckedTrackColor = L.BoxDeep,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

// --- about ---------------------------------------------------------------------------------

@Composable
private fun AboutSheet(isLocal: Boolean, onPrivacy: () -> Unit, onDismiss: () -> Unit) {
    LSheet(title = "About", onDismiss = onDismiss, primary = "Done", onPrimary = onDismiss) {
        LGroup {
            LGroupRow(title = "Version", trailing = BuildConfig.VERSION_NAME)
            LGroupDivider()
            LGroupRow(title = "Sync", trailing = if (isLocal) "Off" else "On")
            LGroupDivider()
            LGroupRow(title = "Privacy policy", onClick = onPrivacy, end = { Chevron() })
        }
    }
}

private val PRIVACY_POLICY_TEXT = """
LedgerAI is designed offline-first. Your transactions, budgets, tasks, notes, and alarms are stored on this device by default.

What we process locally
• Voice transcripts and text you enter for parsing into transactions
• On-device summaries used to build compact AI context
• Preferences such as currency, notifications, and widget display

Cloud AI
• When signed in, prompts may be sent through a Supabase Edge Function so API keys never ship in the release APK
• Do not include secrets or passwords in notes or voice input

Accounts
• Google sign-in via Supabase Auth; or continue locally with data on-device only
• You can clear app data from system settings to remove local records

We do not sell personal financial data. This summary is product guidance, not legal advice. Contact the app owner for formal policy updates.
""".trimIndent()

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ReminderEngineEntry {
    fun engine(): com.ledgerai.app.service.ReminderEngine
}

/** When day-level birthday and trip reminders fire, whether exact alarms are allowed, and a test button. */
@Composable
private fun RemindersSheet(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val engine = remember(ctx) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(ctx.applicationContext, ReminderEngineEntry::class.java).engine()
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hour by remember { mutableStateOf(com.ledgerai.app.data.reminders.ReminderRepository.reminderHour(ctx).hour) }
    val exact = android.os.Build.VERSION.SDK_INT < 31 ||
        (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
    LSheet(title = "Reminders", onDismiss = onDismiss, primary = "Done", onPrimary = onDismiss) {
        Text("Reminders a day or more ahead fire at", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        com.ledgerai.app.presentation.components.ChipsRow {
            listOf(7, 8, 9, 10, 12, 18).forEach { h ->
                com.ledgerai.app.presentation.components.LChip("%02d:00".format(h), hour == h, {
                    hour = h
                    com.ledgerai.app.data.reminders.ReminderRepository.setReminderHour(ctx, h)
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { engine.rebuildAndArm() }
                })
            }
        }
        Text(
            if (exact) "Exact alarms are allowed, so reminders arrive on time." else "Exact alarms are off, so reminders can arrive late.",
            style = MaterialTheme.typography.bodySmall, color = if (exact) L.InkMuted else L.Danger,
        )
        if (!exact && android.os.Build.VERSION.SDK_INT >= 31) {
            com.ledgerai.app.presentation.components.LGhostButton("Allow exact alarms", {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        com.ledgerai.app.presentation.components.LGhostButton("Send a test reminder", { scope.launch(kotlinx.coroutines.Dispatchers.IO) { engine.sendTest() } })
    }
}
