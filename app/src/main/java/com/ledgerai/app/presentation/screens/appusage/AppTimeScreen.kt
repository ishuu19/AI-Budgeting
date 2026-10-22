package com.ledgerai.app.presentation.screens.appusage

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.appusage.AppTotal
import com.ledgerai.app.data.appusage.AppUsageDao
import com.ledgerai.app.data.appusage.AppUsageDayEntity
import com.ledgerai.app.data.appusage.DayTotal
import com.ledgerai.app.data.appusage.UsageCollector
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class UsageRange(val label: String, val days: Long) { Today("Today", 1), Week("7 days", 7), Month("30 days", 30) }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppTimeViewModel @Inject constructor(
    private val dao: AppUsageDao,
    private val collector: UsageCollector,
) : ViewModel() {

    val allowed = MutableStateFlow(collector.hasAccess())
    private val range = MutableStateFlow(UsageRange.Today)
    val selected: StateFlow<UsageRange> = range

    val totals: StateFlow<List<AppTotal>> = range
        .flatMapLatest { r -> dao.observeTotals(LocalDate.now().minusDays(r.days - 1), LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The period just before the selected one, for the "vs before" comparison. */
    val previous: StateFlow<List<DayTotal>> = range
        .flatMapLatest { r -> dao.observeDaily(LocalDate.now().minusDays(2 * r.days - 1), LocalDate.now().minusDays(r.days)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 14 days, one total per day. */
    val daily: StateFlow<List<DayTotal>> = dao.observeDaily(LocalDate.now().minusDays(13), LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun history(pkg: String): List<AppUsageDayEntity> = dao.history(pkg, LocalDate.now().minusDays(13), LocalDate.now())

    fun select(r: UsageRange) { range.value = r }

    fun refresh() {
        allowed.value = collector.hasAccess()
        viewModelScope.launch { collector.collect() }
    }
}

@Composable
fun AppTimeScreen(onBack: () -> Unit, vm: AppTimeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val allowed by vm.allowed.collectAsState()
    val totals by vm.totals.collectAsState()
    val previous by vm.previous.collectAsState()
    val daily by vm.daily.collectAsState()
    val range by vm.selected.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var openHistory by remember { mutableStateOf<List<AppUsageDayEntity>>(emptyList()) }

    LaunchedEffect(open) { openHistory = open?.let { vm.history(it) }.orEmpty() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LScreen(title = "App time", onBack = onBack, itemSpacing = 12.dp) {
        if (!allowed) {
            item { Text("See where your phone time goes.", style = MaterialTheme.typography.headlineMedium, color = L.Ink) }
            item { Text("This needs usage access in Settings. Nothing is recorded until you allow it, and it never leaves this phone.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
            item { LButton("Allow", { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) }
            return@LScreen
        }
        item { LKindChips(UsageRange.entries, range, { it.label }, vm::select) }
        val total = totals.sumOf { it.foregroundMs }
        val prevTotal = previous.sumOf { it.ms }
        val activeDays = daily.count { it.ms > 0 }.coerceAtLeast(1)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(duration(total), style = MaterialTheme.typography.displaySmall, color = L.Ink)
                Text("across ${totals.size} apps", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
            }
        }
        val launches = totals.sumOf { it.launches }
        val peak = daily.maxByOrNull { it.ms }
        val top = totals.firstOrNull()
        val mostOpened = totals.maxByOrNull { it.launches }
        val change = if (prevTotal > 0) ((total - prevTotal) * 100 / prevTotal).toInt() else null
        val stats = buildList {
            add(Triple("Daily average", duration(total / range.days.coerceAtLeast(1)), "over ${range.label.lowercase()}"))
            add(Triple("Versus before", change?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "Not enough data", "the period before"))
            add(Triple("Busiest day", peak?.let { it.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } ?: "None", peak?.let { duration(it.ms) }.orEmpty()))
            add(Triple("Pickups", "$launches", "app opens"))
            add(Triple("Top app", top?.label ?: "None", top?.let { "${if (total > 0) it.foregroundMs * 100 / total else 0}% of your time" }.orEmpty()))
            add(Triple("Most opened", mostOpened?.label ?: "None", mostOpened?.let { "${it.launches} times" }.orEmpty()))
        }
        stats.chunked(2).forEach { pair ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (l, v, s) -> Tile(l, v, s, Modifier.weight(1f)) }
                }
            }
        }
        item { Text("Last 14 days", style = MaterialTheme.typography.labelLarge, color = L.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
        item { DayBars(daily) }
        item { Text("Apps", style = MaterialTheme.typography.labelLarge, color = L.InkMuted, modifier = Modifier.padding(top = 8.dp)) }
        if (totals.isEmpty()) item { Text("Nothing recorded yet. Usage appears within the hour.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
        val topMs = top?.foregroundMs?.coerceAtLeast(1) ?: 1
        totals.forEach { t ->
            item(key = t.packageName) {
                Column(Modifier.fillMaxWidth().clickable { open = if (open == t.packageName) null else t.packageName }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.label, style = MaterialTheme.typography.titleSmall, color = L.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(duration(t.foregroundMs) + " · ${t.launches} opens", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                    }
                    Row(Modifier.fillMaxWidth().height(3.dp).background(L.Line)) {
                        Row(Modifier.fillMaxWidth(t.foregroundMs.toFloat() / topMs).fillMaxHeight().background(L.Ink)) {}
                    }
                    if (open == t.packageName && openHistory.isNotEmpty()) {
                        val days = openHistory.associate { it.day to it.foregroundMs }
                        DayBars((0..13).map { DayTotal(LocalDate.now().minusDays(13L - it), days[LocalDate.now().minusDays(13L - it)] ?: 0L) })
                        Text("Average ${duration(openHistory.sumOf { it.foregroundMs } / 14)} a day over 14 days", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 92.dp).clip(shape).background(L.BoxDeep).border(1.dp, L.Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = L.Ink, maxLines = 1)
        Text(if (sub.isBlank()) " " else sub, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 2)
    }
}

/** One vertical bar per day; today is the accent bar. Missing days show as empty. */
@Composable
private fun DayBars(days: List<DayTotal>) {
    val byDay = days.associate { it.day to it.ms }
    val range = (0..13).map { LocalDate.now().minusDays(13L - it) }
    val top = (range.maxOfOrNull { byDay[it] ?: 0L } ?: 0L).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        range.forEach { d ->
            val ms = byDay[d] ?: 0L
            Box(
                Modifier.weight(1f).fillMaxHeight((ms.toFloat() / top).coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(3.dp)).background(if (d == LocalDate.now()) L.Accent else L.Ink.copy(alpha = if (ms > 0) 0.8f else 0.15f))
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        range.forEach { d -> Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = L.InkMuted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}
