package com.ledgerai.app.presentation.screens.today

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LError
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHeader
import com.ledgerai.app.presentation.components.LHeroCard
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSmallBlock
import com.ledgerai.app.presentation.components.LSmallPair
import com.ledgerai.app.presentation.components.LWide
import com.ledgerai.app.presentation.components.dueLabel
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.MoneySeg
import com.ledgerai.app.presentation.navigation.OpenKind
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.presentation.screens.dashboard.DashboardUiState
import com.ledgerai.app.presentation.screens.dashboard.DashboardViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    onBack: (() -> Unit)? = null,
    viewModel: TodayViewModel = hiltViewModel(),
    dashboard: DashboardViewModel = hiltViewModel()
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val back = onBack ?: dispatcher?.let { d -> { d.onBackPressed() } }
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
            contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = L.Space.x16, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(L.Gutter)
        ) {
            item(key = "header") { LHeader(title = "Today", onBack = back) }
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
        val until = upcoming?.takeIf { !it.allDay && it.start.toLocalDate() == now.toLocalDate() }
        LHeroCard(
            label = "Now",
            title = if (until != null) "Free until ${until.start.format(timeFmt)}" else "Free",
            sub = upcoming?.title
        )
        return
    }
    val item = hero.item
    val isBlock = item.kind == TodayKind.Study || item.kind == TodayKind.Habit
    val mins = Duration.between(now, item.start).toMinutes().coerceAtLeast(0)
    val sub = if (hero.current) {
        val left = Duration.between(now, item.end).toMinutes().coerceAtLeast(0)
        "$left min left"
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
        actions = if (hero.current || isBlock) {
            {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                    if (hero.current) LProgress(blockFraction(item, now))
                    if (isBlock) {
                        Row(horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
                            LButton(
                                "Start",
                                onClick = { links.focus(blockIdOf(item), item.topic.ifBlank { item.title }) },
                                fill = false
                            )
                            if (hero.current) LGhostButton("Done", onClick = { vm.doneBlock(item) }, fill = false)
                            else LGhostButton("Skip", onClick = { vm.skipBlock(item.id) }, fill = false)
                        }
                    }
                }
            }
        } else null
    )
}

private fun blockIdOf(item: TodayItem): Long = when (item.kind) {
    TodayKind.Study -> item.id
    TodayKind.Habit -> -item.id
    else -> 0L
}

private fun blockFraction(item: TodayItem, now: LocalDateTime): Float {
    val span = Duration.between(item.start, item.end).toMinutes().coerceAtLeast(1)
    val done = Duration.between(item.start, now).toMinutes().coerceIn(0, span)
    return done.toFloat() / span.toFloat()
}

// --- next ----------------------------------------------------------------------------------

@Composable
private fun Next(items: List<TodayItem>, now: LocalDateTime, label: String, links: AppLinks) {
    val today = now.toLocalDate()
    val todayItems = items.filter { it.start.toLocalDate() == today }
    val laterItems = items.filter { it.start.toLocalDate() != today }
    Column(verticalArrangement = Arrangement.spacedBy(L.Gutter)) {
        if (items.isEmpty()) {
            LSection(label)
            Text("Nothing else planned", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
            return@Column
        }
        if (label != "Tomorrow" && todayItems.isNotEmpty()) {
            LSection("Next")
            TimeRail(todayItems, today, links)
        }
        if (laterItems.isNotEmpty()) {
            LSection("Tomorrow")
            TimeRail(laterItems, today, links)
        }
    }
}

@Composable
private fun TimeRail(items: List<TodayItem>, today: LocalDate, links: AppLinks) {
    LGroup {
        items.forEachIndexed { index, item ->
            if (index > 0) LGroupDivider()
            LGroupRow(
                title = item.title,
                onClick = { openTodayItem(item, links) },
                leading = {
                    Text(
                        itemWhen(item, today),
                        style = MaterialTheme.typography.labelLarge,
                        color = L.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(min = L.ThumbLg)
                    )
                }
            )
        }
    }
}

private fun itemWhen(item: TodayItem, today: LocalDate): String = when {
    item.kind == TodayKind.Bill -> dueLabel(item.start.toLocalDate(), today)
    item.allDay -> "All day"
    else -> item.start.format(timeFmt)
}

private fun openTodayItem(item: TodayItem, links: AppLinks) {
    when (item.kind) {
        TodayKind.Bill -> links.open(OpenKind.Bill, item.id)
        TodayKind.Event, TodayKind.Class, TodayKind.Task, TodayKind.Alarm ->
            links.open(OpenKind.Event, item.id)
        else -> links.plan(PlanSeg.Calendar)
    }
}

// --- safe to spend and attention -----------------------------------------------------------

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
    if (guide == null) {
        LSmallBlock(
            label = "Safe to spend",
            value = "Set budget",
            valueColor = L.Accent,
            onClick = links.spendGuide,
            modifier = modifier
        )
        return
    }
    val over = state.guideStatus == SpendGuideStatus.OVER || state.spentToday > guide
    LSmallBlock(
        label = "Safe to spend",
        value = money(guide),
        sub = "${money(state.spentToday)} spent",
        valueColor = if (over) L.Danger else L.OnBox,
        onClick = links.spendGuide,
        modifier = modifier,
        footer = {
            if (guide > 0) {
                LProgress(
                    (state.spentToday / guide).toFloat(),
                    modifier = Modifier.padding(top = L.Space.x4),
                    color = if (over) L.Danger else L.Accent
                )
            }
        }
    )
}

@Composable
private fun Attention(state: TodayUiState, links: AppLinks, modifier: Modifier) {
    val parts = attentionParts(state)
    val overdue = state.overdueTasks > 0 || state.billsOverdue > 0
    val line = if (parts.isEmpty()) "Clear" else parts.joinToString(" · ")
    LSmallBlock(
        label = "Attention",
        value = if (parts.size > 1) parts.first() else line,
        sub = if (parts.size > 1) parts.drop(1).joinToString(" · ") else null,
        valueColor = if (overdue) L.Danger else L.OnBox,
        onClick = {
            when {
                state.dueTasks > 0 -> links.plan(PlanSeg.Tasks)
                state.billsDue + state.billsOverdue > 0 -> links.money(MoneySeg.Owed)
                state.logGaps > 0 -> links.plan(PlanSeg.Log)
                else -> links.plan(PlanSeg.Tasks)
            }
        },
        modifier = modifier,
        footer = if (parts.size < 2) null else {
            { AttentionBar(state) }
        }
    )
}

private fun attentionParts(state: TodayUiState): List<String> {
    val parts = mutableListOf<String>()
    if (state.dueTasks > 0) parts += countLabel(state.dueTasks, "task", "tasks")
    val bills = state.billsDue + state.billsOverdue
    if (bills > 0) parts += countLabel(bills, "bill", "bills")
    if (state.logGaps > 0) parts += "${"%.0f".format(state.gapHours.coerceAtLeast(1.0))}h log"
    return parts
}

private fun countLabel(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

@Composable
private fun AttentionBar(state: TodayUiState) {
    val lateTasks = state.overdueTasks.coerceAtLeast(0)
    val tasks = (state.dueTasks - lateTasks).coerceAtLeast(0)
    val bills = state.billsDue.coerceAtLeast(0)
    val lateBills = state.billsOverdue.coerceAtLeast(0)
    val gaps = state.logGaps.coerceAtLeast(0)
    val segments = listOf(
        lateTasks + lateBills to L.Danger,
        bills to L.Highlight,
        tasks to L.Accent,
        gaps to L.InkMuted
    ).filter { it.first > 0 }
    if (segments.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(L.Space.x8).clip(L.RadiusPill)) {
        segments.forEach { (n, color) ->
            Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color))
        }
    }
}

// --- insight and wrap-up -------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.insight(dash: DashboardUiState, links: AppLinks) {
    val top = dash.behaviourInsights.firstOrNull()
    val card = dash.aiInsightCard
    val headline: String
    val context: String
    when {
        top != null -> {
            headline = top.headline
            context = "${top.headline}\n${top.action}"
        }
        card != null && !dash.isLoading -> {
            headline = card.body.ifBlank { card.title }
            context = card.chatContext
        }
        else -> return
    }
    item(key = "insight") {
        LWide(label = "Insight", onClick = { links.chat(context) }) {
            Text(headline, style = MaterialTheme.typography.bodyMedium, color = L.OnBox, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WrapUp(state: TodayUiState, links: AppLinks) {
    LWide(label = "Wrap-up") {
        WrapRow(
            text = if (state.logGaps > 0) "${"%.0f".format(state.gapHours.coerceAtLeast(1.0))}h unlogged" else "All logged",
            onClick = { links.plan(PlanSeg.Log) }
        )
        if (state.habitsTotal > 0) {
            HabitWrap(state.habitsDone, state.habitsTotal) { links.plan(PlanSeg.Calendar) }
        }
    }
}

@Composable
private fun HabitWrap(done: Int, total: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = L.Target)
            .clip(RoundedCornerShape(L.RadiusSm))
            .clickable(onClick = onClick)
            .padding(vertical = L.Space.x8),
        verticalArrangement = Arrangement.spacedBy(L.Space.x8)
    ) {
        Text(
            "$done of $total habits",
            style = MaterialTheme.typography.titleSmall,
            color = L.OnBox,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LProgress(done.toFloat() / total.toFloat())
    }
}

@Composable
private fun WrapRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = L.Target)
            .clip(RoundedCornerShape(L.RadiusSm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
