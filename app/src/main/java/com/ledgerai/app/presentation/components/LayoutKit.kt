package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = L.Gold)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = L.OnBox, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (actions != null) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
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
        if (label != null) Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = L.Gold)
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
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    val base = modifier.clip(RoundedCornerShape(L.Radius))
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = L.Gold, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(L.Radius)).background(L.Box),
        content = content
    )
}

@Composable
fun LGroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = L.OnBox.copy(alpha = 0.12f))
}

/** Row inside an [LGroup]: no box of its own. Minimum height 56dp. */
@Composable
fun LGroupRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: String? = null,
    trailingColor: Color = L.Gold,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = L.Gold, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1)
        end?.invoke()
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
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(L.Line)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) L.Box else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) L.OnBox else L.Ink,
                    maxLines = 1
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

/** Small confirm dialog used by every delete. */
@Composable
fun LConfirmDelete(onConfirm: () -> Unit, onDismiss: () -> Unit, title: String = "Delete?") {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = L.Page,
        title = { Text(title, color = L.Ink) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete", color = L.Box) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = L.InkMuted) } }
    )
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
        modifier.fillMaxWidth().padding(vertical = 48.dp).semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator(color = L.Box, modifier = Modifier.size(32.dp)) }
}

/** Error state: icon, short text, Retry. */
@Composable
fun LError(text: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = L.Box, modifier = Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
        if (onRetry != null) LGhostButton("Retry", onRetry, Modifier.width(160.dp))
    }
}

/**
 * Page frame for a tab: heading, optional trailing action, segmented control, then the body.
 * The body is shown with [LocalEmbedded] so embedded screens skip their own title.
 */
@Composable
fun LTabPage(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    segments: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().background(L.Page)) {
        Row(
            Modifier.fillMaxWidth().padding(start = L.Gutter, end = L.Gutter, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = L.Ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            action?.invoke(this)
        }
        if (segments != null) Box(Modifier.padding(start = L.Gutter, end = L.Gutter, top = 16.dp, bottom = 8.dp)) { segments() }
        Box(Modifier.weight(1f).fillMaxWidth()) { LEmbedded(body) }
    }
}
