package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.navigation.AppLinks

/*
 * Layout kit (U1): the block vocabulary every tab uses.
 * H hero (LHero / LHeroCard), W wide (LWide, LGroup), S small (LSmallPair), segmented control, kind chips,
 * item sheet and the three non-content states.
 */

/** Hero block for an active item (Now). One per screen. Use [LHero] when the hero is one large number. */
@Composable
fun LHeroCard(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val base = modifier.fillMaxWidth()
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label.lowercase(), style = MaterialTheme.typography.labelMedium, color = L.OnBoxMuted)
        Text(title, style = MaterialTheme.typography.displaySmall, color = L.OnBox, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (actions != null) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** Wide block: full width container with an optional gold label. */
@Composable
fun LWide(
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LCard(modifier = modifier, onClick = onClick) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
        content()
    }
}

/** Small block: half width, one count or status. Always used inside [LSmallPair]. */
@Composable
fun LSmallBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    valueColor: Color = L.OnBox,
    onClick: (() -> Unit)? = null,
    delta: Delta? = null,
    trend: List<Float>? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val base = modifier
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (delta != null) DeltaLine(delta)
        if (!trend.isNullOrEmpty()) LSparkline(trend, Modifier.fillMaxWidth())
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        footer?.invoke(this)
    }
}

/** Two small blocks side by side with equal height. Never stack two pairs. */
@Composable
fun LSmallPair(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        left(Modifier.weight(1f).fillMaxSize())
        right(Modifier.weight(1f).fillMaxSize())
    }
}

/** One container for related rows, separated by dividers. */
@Composable
fun LGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), content = content)
}

@Composable
fun LGroupDivider() {
    HorizontalDivider(color = L.Line)
}

/** Row inside an [LGroup]: no box of its own. Minimum height 56dp. */
@Composable
fun LGroupRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: String? = null,
    trailingColor: Color = L.OnBox,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = L.Space.x12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(L.Space.x12)
    ) {
        if (leading != null) leading()
        else if (icon != null) Icon(icon, contentDescription = null, tint = L.InkMuted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1)
        end?.invoke()
    }
}

/**
 * Swipe between [options]. A tap scrolls to that page. A swipe moves the segment highlight with the
 * page, including when you swipe back, because the collector always reads the latest selection.
 * A tap-driven scroll does not report the pages it passes through, so the highlight stays on the
 * page you chose.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> LSwipePages(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (T) -> Unit,
) {
    val initial = options.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { options.size })
    val latestOptions by rememberUpdatedState(options)
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    // True while a tap (not a finger swipe) is moving the pager, so passing pages are not reported.
    val scrollFromTap = remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        val index = latestOptions.indexOf(selected)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
            scrollFromTap.value = true
            try {
                pagerState.animateScrollToPage(index)
            } finally {
                scrollFromTap.value = false
                val landed = latestOptions.getOrNull(pagerState.currentPage)
                if (landed != null && landed != latestSelected) latestOnSelect(landed)
            }
        }
    }
    // currentPage flips once the swipe is more than half across, so the highlight does not wait.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (scrollFromTap.value) return@collect
            val item = latestOptions.getOrNull(page) ?: return@collect
            if (item != latestSelected) latestOnSelect(item)
        }
    }
    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { index ->
        page(options[index])
    }
}

/** Segmented control. At most four options. */
@Composable
fun <T> LSegments(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    require(options.size <= 4) { "Segmented control holds at most 4 options" }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        options.forEach { option ->
            val on = option == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(option) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) L.Ink else L.InkMuted,
                    maxLines = 1
                )
                Box(
                    Modifier.padding(top = 6.dp).height(2.dp).fillMaxWidth()
                        .background(if (on) L.Accent else Color.Transparent)
                )
            }
        }
    }
}

/** Filter chips (All, Events, Tasks, ...). Scrolls sideways. */
@Composable
fun <T> LKindChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        ChipsRow {
            options.forEach { option ->
                LChip(label(option), option == selected, onClick = { onSelect(option) })
            }
        }
    }
}

/** Confirm dialog. Destructive confirms use [L.Danger]. */
@Composable
fun LConfirm(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirm: String = "Delete",
    dismiss: String = "Cancel",
    destructive: Boolean = true,
    body: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = L.Page,
        title = { Text(title, color = L.Ink) },
        text = if (body.isNullOrBlank()) null else ({ Text(body, color = L.InkMuted) }),
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = L.Target)) {
                Text(confirm, color = if (destructive) L.Danger else L.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = L.Target)) {
                Text(dismiss, color = L.InkMuted)
            }
        }
    )
}

/** Small confirm dialog used by every delete. */
@Composable
fun LConfirmDelete(onConfirm: () -> Unit, onDismiss: () -> Unit, title: String = "Delete?") {
    LConfirm(title = title, onConfirm = onConfirm, onDismiss = onDismiss)
}

/**
 * The one item sheet. Primary action (Save or Done), optional Complete toggle, Delete as a ghost button with confirm.
 */
@Composable
fun LItemSheet(
    title: String,
    onDismiss: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    LSheet(
        title = title,
        onDismiss = onDismiss,
        primary = primary,
        onPrimary = onPrimary,
        primaryEnabled = primaryEnabled,
        secondary = if (onDelete != null) "Delete" else null,
        onSecondary = { confirm = true },
        secondaryDestructive = onDelete != null,
        content = content
    )
    if (confirm && onDelete != null) {
        LConfirmDelete(onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false })
    }
}

/** Loading state: centred spinner. */
@Composable
fun LLoading(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator(color = L.Accent, modifier = Modifier.size(32.dp)) }
}

/** Error state: same circle as [LEmpty], short text, Retry. */
@Composable
fun LError(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = L.Space.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(L.Space.x8)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
        if (onRetry != null) LGhostButton("Retry", onRetry, Modifier.width(160.dp), fill = false)
    }
}

@Composable
fun LSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = L.OnAccent,
            checkedTrackColor = L.Accent,
            uncheckedThumbColor = L.Box,
            uncheckedTrackColor = L.BoxDeep,
            uncheckedBorderColor = L.Line,
        ),
    )
}

@Composable
fun LSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = L.Target),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(L.Space.x12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(L.Space.x4)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = L.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LSwitch(checked, onCheckedChange)
    }
}

/** Shell links for the shared AI bar. The navigation host provides the real actions. */
val LocalLinks = androidx.compose.runtime.staticCompositionLocalOf { AppLinks() }

/**
 * Page frame for a tab: heading, optional trailing action, segmented control, then the body.
 * The body is shown with [LocalEmbedded] so embedded screens skip their own title.
 */
@Composable
fun LTabPage(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    action: (@Composable RowScope.() -> Unit)? = null,
    /** Icon row directly under the title. */
    tools: (@Composable () -> Unit)? = null,
    segments: (@Composable () -> Unit)? = null,
    /** Shown under the segments: the AI-first way to add something on this tab. */
    aiBar: (@Composable () -> Unit)? = null,
    /** Life hides this. That tab already has one search row. */
    assistant: Boolean = true,
    body: @Composable () -> Unit
) {
    val searchDismiss = remember { SearchDismissState() }
    val pageCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    CompositionLocalProvider(LocalSearchDismiss provides searchDismiss) {
    Column(
        modifier
            .fillMaxSize()
            .background(L.Page)
            .onGloballyPositioned { pageCoords.value = it }
            .dismissSearchOnOutsideTap(searchDismiss, pageCoords),
    ) {
        Box(
            Modifier.padding(
                start = L.Gutter,
                end = L.Gutter,
                top = if (compact) L.Space.x8 else L.Space.x16,
                bottom = L.Space.x8,
            )
        ) {
            LHeader(title = title, actions = action)
        }
        if (tools != null) Box { tools() }
        if (segments != null) {
            Box(
                Modifier.padding(
                    start = L.Gutter,
                    end = L.Gutter,
                    top = if (compact) L.Space.x8 else L.Space.x16,
                    bottom = L.Space.x8,
                )
            ) { segments() }
        }
        if (assistant) {
            val bar = aiBar
            val hint = when (title) {
                "Money" -> "Coffee 4.50"
                "Life" -> "Buy milk"
                else -> "Dentist Friday at 3"
            }
            Box(Modifier.padding(start = L.Gutter, end = L.Gutter, bottom = L.Space.x8)) {
                if (bar != null) bar()
                else {
                    val links = LocalLinks.current
                    LAiBar(hint, onSend = { links.speak(it) }, onMic = links.listen)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { LEmbedded(body) }
    }
    }
}
