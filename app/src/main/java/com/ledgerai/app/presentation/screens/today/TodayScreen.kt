package com.ledgerai.app.presentation.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LIconButton
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LLogo
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.LWide
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.MoneySeg
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.presentation.screens.dashboard.DashboardUiState
import com.ledgerai.app.presentation.screens.dashboard.DashboardViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private enum class Part { Morning, Day, Evening }

private fun partOf(now: LocalDateTime) = when {
    now.hour < 12 -> Part.Morning
    now.hour < 18 -> Part.Day
    else -> Part.Evening
}

/** Today tab: the spine of the app. Order of blocks follows the time of day. */
@Composable
fun TodayScreen(
    links: AppLinks,
    viewModel: TodayViewModel = hiltViewModel(),
    dashboard: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dash by dashboard.uiState.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalDateTime.now()
            delay(Duration.between(t, t.plusMinutes(1).withSecond(0).withNano(0)).toMillis().coerceAtLeast(1_000L))
            now = LocalDateTime.now()
            viewModel.setDay(now.toLocalDate())
        }
    }

    Box(Modifier.fillMaxSize().background(L.Page)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "header") { Header(now, dash, links) }
            when {
                state.isLoading -> item(key = "loading") { LLoading() }
                state.error != null -> item(key = "error") {
                    LError(state.error ?: "Could not load Today", onRetry = viewModel::retry)
                }
                else -> body(state, dash, now, links, viewModel)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.body(
    state: TodayUiState,
    dash: DashboardUiState,
    now: LocalDateTime,
    links: AppLinks,
    vm: TodayViewModel
) {
    val part = partOf(now)
    val hero = heroOf(state.items, now)
    val next = nextOf(state.items, now, hero?.item, part == Part.Evening)

    item(key = "hero") { Hero(hero, next.firstOrNull(), now, links, vm) }

    if (part == Part.Evening) {
        item(key = "wrap") { WrapUp(state, links) }
        item(key = "next") { Next(next, now, "Tomorrow", links) }
        item(key = "pair") { SafeAttentionPair(state, links, safeFirst = true) }
        insight(dash, links)
    } else {
        item(key = "next") { Next(next, now, "Next", links) }
        item(key = "pair") { SafeAttentionPair(state, links, safeFirst = part == Part.Morning) }
        insight(dash, links)
        item(key = "wrap") { WrapUp(state, links) }
    }
}

// --- header --------------------------------------------------------------------------------

@Composable
private fun Header(now: LocalDateTime, dash: DashboardUiState, links: AppLinks) {
    val hello = when {
        now.hour in 5..11 -> "Good morning"
        now.hour in 12..17 -> "Good afternoon"
        now.hour in 18..21 -> "Good evening"
        else -> "Hello"
    }
    val title = if (dash.userName.isNotBlank()) "$hello, ${dash.userName}" else hello
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LLogo(32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = L.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + now.dayOfMonth,
                style = MaterialTheme.typography.bodySmall,
                color = L.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LIconButton(Icons.Filled.Search, "Search", links.search)
        LIconButton(Icons.Filled.Settings, "Settings", links.you)
    }
}

// --- hero ----------------------------------------------------------------------------------

private data class HeroPick(val item: TodayItem, val current: Boolean)

private fun heroOf(items: List<TodayItem>, now: LocalDateTime): HeroPick? {
    val blocks = items.filter { it.isBlock }
    blocks.filter { !it.start.isAfter(now) && it.end.isAfter(now) }
        .minByOrNull { it.start }
        ?.let { return HeroPick(it, true) }
    blocks.filter { it.start.isAfter(now) && !it.start.isAfter(now.plusMinutes(90)) }
        .minByOrNull { it.start }
        ?.let { return HeroPick(it, false) }
    return null
}

private fun nextOf(
    items: List<TodayItem>,
    now: LocalDateTime,
    hero: TodayItem?,
    tomorrowOnly: Boolean
): List<TodayItem> {
    val tomorrow = now.toLocalDate().plusDays(1)
    val pool = items.filter { it != hero }
    val picked = if (tomorrowOnly) {
        pool.filter { it.start.toLocalDate() == tomorrow }
    } else {
        val today = pool.filter {
            it.start.toLocalDate() == now.toLocalDate() && (it.allDay || !it.start.isBefore(now))
        }
        if (today.size >= 3) today else today + pool.filter { it.start.toLocalDate() == tomorrow }
    }
    return picked.take(4)
}

@Composable
private fun Hero(hero: HeroPick?, upcoming: TodayItem?, now: LocalDateTime, links: AppLinks, vm: TodayViewModel) {
    if (hero == null) {
        LHeroCard(
            label = "Now",
            title = "Free",
            sub = upcoming?.title
        )
        return
    }
    val item = hero.item
    val isBlock = item.kind == TodayKind.Study || item.kind == TodayKind.Habit
    val mins = Duration.between(now, item.start).toMinutes().coerceAtLeast(0)
    val sub = if (hero.current) {
        val left = Duration.between(now, item.end).toMinutes().coerceAtLeast(0)
        "${item.start.format(timeFmt)}–${item.end.format(timeFmt)} · ${left}m"
    } else {
        "${item.start.format(timeFmt)} · ${mins}m"
    }
    LHeroCard(
        label = if (hero.current) "Now" else "Soon",
        title = item.title,
        sub = sub,
        onClick = {
            when (item.kind) {
                TodayKind.Event, TodayKind.Class -> links.open(OpenKind.Event, item.id)
                else -> links.plan(PlanSeg.Calendar)
            }
        },
        actions = {
            val blockId = when (item.kind) {
                TodayKind.Study -> item.id
                TodayKind.Habit -> -item.id
                else -> 0L
            }
            HeroButton("Start", filled = true) { links.focus(blockId, item.topic.ifBlank { item.title }) }
            if (isBlock) {
                if (hero.current) HeroButton("Done", filled = false) { vm.doneBlock(item) }
                else HeroButton("Skip", filled = false) { vm.skipBlock(item.id) }
            }
        }
    )
}

@Composable
private fun HeroButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(L.RadiusSm))
            .background(if (filled) L.Gold else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) L.BoxDeep else L.OnBox,
            maxLines = 1
        )
    }
}

// --- next ----------------------------------------------------------------------------------

@Composable
private fun Next(items: List<TodayItem>, now: LocalDateTime, label: String, links: AppLinks) {
    LWide(label = label) {
        if (items.isEmpty()) {
            Text("Nothing else planned", style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
        }
        items.forEach { item ->
            val showDay = item.start.toLocalDate() != now.toLocalDate() && label != "Tomorrow"
            val time = when {
                item.kind == TodayKind.Bill -> "Due"
                item.allDay -> "All day"
                else -> item.start.format(timeFmt)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(L.RadiusSm))
                    .clickable {
                        when (item.kind) {
                            TodayKind.Bill -> links.open(OpenKind.Bill, item.id)
                            TodayKind.Event, TodayKind.Class, TodayKind.Task, TodayKind.Alarm ->
                                links.open(OpenKind.Event, item.id)
                            else -> links.plan(PlanSeg.Calendar)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelLarge,
                    color = L.Gold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp).widthIn(min = 56.dp)
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = L.OnBox,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showDay) {
                    Text("Tomorrow", style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1)
                }
            }
        }
    }
}

// --- safe today and attention --------------------------------------------------------------

@Composable
private fun SafeAttentionPair(state: TodayUiState, links: AppLinks, safeFirst: Boolean) {
    val safe: @Composable (Modifier) -> Unit = { m -> Safe(state, links, m) }
    val attention: @Composable (Modifier) -> Unit = { m -> Attention(state, links, m) }
    if (safeFirst) LSmallPair(left = safe, right = attention)
    else LSmallPair(left = attention, right = safe)
}

@Composable
private fun Safe(state: TodayUiState, links: AppLinks, modifier: Modifier) {
    val guide = state.guide
    val over = state.guideStatus == SpendGuideStatus.OVER || (guide != null && state.spentToday > guide)
    LSmallBlock(
        label = "Safe today",
        value = if (guide != null) money(guide) else "-",
        sub = if (guide != null) "${money(state.spentToday)} spent" else null,
        valueColor = if (over) L.Danger else L.OnBox,
        onClick = links.spendGuide,
        modifier = modifier,
        footer = {
            if (guide != null && guide > 0) {
                LProgress(
                    (state.spentToday / guide).toFloat(),
                    modifier = Modifier.padding(top = 4.dp),
                    color = if (over) L.Danger else L.Gold
                )
            }
        }
    )
}

@Composable
private fun Attention(state: TodayUiState, links: AppLinks, modifier: Modifier) {
    val total = state.dueTasks + state.billsDue + state.billsOverdue + state.logGaps
    val overdue = state.overdueTasks > 0 || state.billsOverdue > 0
    LSmallBlock(
        label = "Attention",
        value = if (total == 0) "Clear" else total.toString(),
        sub = null,
        valueColor = if (overdue) L.Danger else L.OnBox,
        onClick = {
            when {
                state.dueTasks > 0 -> links.plan(PlanSeg.Tasks)
                state.billsDue + state.billsOverdue > 0 -> links.money(MoneySeg.Owed)
                state.logGaps > 0 -> links.plan(PlanSeg.Log)
                else -> links.plan(PlanSeg.Tasks)
            }
        },
        modifier = modifier
    )
}

// --- insight and wrap-up -------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.insight(dash: DashboardUiState, links: AppLinks) {
    val top = dash.behaviourInsights.firstOrNull()
    val card = dash.aiInsightCard
    val headline: String
    val context: String
    val source: String
    when {
        top != null -> {
            headline = top.headline
            context = "${top.headline}\n${top.action}"
            source = "Rules"
        }
        card != null && !dash.isLoading -> {
            headline = card.body.ifBlank { card.title }
            context = card.chatContext
            source = card.source
        }
        else -> return
    }
    item(key = "insight") {
        LWide(label = "Insight · $source", onClick = { links.chat(context) }) {
            Text(headline, style = MaterialTheme.typography.bodyMedium, color = L.OnBox, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WrapUp(state: TodayUiState, links: AppLinks) {
    LWide(label = "Wrap-up") {
        WrapRow(
            text = if (state.logGaps > 0) "%.0f h open".format(state.gapHours.coerceAtLeast(1.0)) else "Log clear",
            onClick = { links.plan(PlanSeg.Log) }
        )
        if (state.habitsTotal > 0) {
            WrapRow(text = "${state.habitsDone}/${state.habitsTotal} habits", onClick = { links.plan(PlanSeg.Calendar) })
        }
    }
}

@Composable
private fun WrapRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(L.RadiusSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
