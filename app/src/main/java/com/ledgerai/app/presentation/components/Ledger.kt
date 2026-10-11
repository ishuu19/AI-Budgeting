package com.ledgerai.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledgerai.app.R
import com.ledgerai.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * LedgerAI design system.
 * Deep blue and white, light and dark. Every colour is a token that follows the system theme.
 * Box = card surface, OnBox = text on it, Accent = the one filled action (Gold is the old name),
 * OnAccent = text on that fill, Highlight = the amber mic, OnHighlight = navy on amber.
 * Pace: Accent = on track, Highlight = near or inferred, Danger = over or overdue (amount only).
 * Spacing scale: 4 · 8 · 12 · 16 · 24 · 32.
 */

object L {
    val Page: Color get() = PageColor
    val Box: Color get() = CardColor
    val BoxDeep: Color get() = CardDeepColor
    /** The one primary fill. */
    val Accent: Color get() = AccentColor
    val OnAccent: Color get() = OnAccentColor
    /** Old name for [Accent]. Kept so existing screens keep compiling. */
    val Gold: Color get() = Accent
    val GoldSoft: Color get() = SoftColor
    val Primary: Color get() = PrimaryColor
    val OnPrimary: Color get() = OnPrimaryColor
    val Highlight: Color get() = AmberHighlight
    val OnHighlight: Color get() = OnHighlightColor
    val OnBox: Color get() = Ink
    val OnBoxMuted: Color get() = InkMuted
    val Ink: Color get() = com.ledgerai.app.presentation.theme.Ink
    val InkMuted: Color get() = com.ledgerai.app.presentation.theme.InkMuted
    val Line: Color get() = LineColor
    val Danger: Color get() = DangerColor
    val Income: Color get() = IncomeColor
    val Radius = 16.dp
    val RadiusSm = 10.dp
    val RadiusPill = RoundedCornerShape(50)
    val Gutter = 16.dp
    val Target = 48.dp
    val Mic = 52.dp
    val Thumb = 64.dp
    val ThumbLg = 96.dp

    object Space {
        val x4 = 4.dp
        val x8 = 8.dp
        val x12 = 12.dp
        val x16 = 16.dp
        val x24 = 24.dp
        val x32 = 32.dp
    }
}

/** Accent = on track, Highlight = near or inferred, Danger = over or overdue. */
enum class Pace { OnTrack, Near, Over }

fun paceColor(pace: Pace): Color = when (pace) {
    Pace.OnTrack -> L.Accent
    Pace.Near -> L.Highlight
    Pace.Over -> L.Danger
}

/** Comparison under a hero. [label] is the words ("12% vs Sep"); the arrow comes from [up]. */
data class Delta(val label: String, val up: Boolean, val good: Boolean = up)

/** Current currency symbol. Snapshot state, so screens that call money() recompose on change. */
object LCurrency {
    var symbol by mutableStateOf("$")
}

/** True when a screen is shown as a segment inside a tab. LScreen then hides its own title and back button. */
val LocalEmbedded = compositionLocalOf { false }

@Composable
fun LEmbedded(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEmbedded provides true, content = content)
}

@Composable
fun LLogo(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "LedgerAI",
        modifier = modifier.size(size)
    )
}

/** Full page: large left title, optional trailing action, optional FAB, lazy body. */
@Composable
fun LScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    fab: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    itemSpacing: Dp = 16.dp,
    topPadding: Dp = 16.dp,
    content: LazyListScope.() -> Unit
) {
    val embedded = LocalEmbedded.current
    Scaffold(
        modifier = modifier,
        containerColor = L.Page,
        floatingActionButton = { fab?.invoke() },
        snackbarHost = { snackbarHost?.invoke() }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = topPadding, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            if (!embedded) {
                item {
                    LHeader(title = title, onBack = onBack, actions = action)
                }
            } else if (action != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) { action.invoke(this) }
                }
            }
            content()
        }
    }
}

/** Primary number. One per screen. [delta] and [trend] give the number a comparison. */
@Composable
fun LHero(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = L.OnBox,
    delta: Delta? = null,
    trend: List<Float>? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = L.Box,
        shape = RoundedCornerShape(L.Radius),
        border = BorderStroke(1.dp, L.Line),
    ) {
        Column(Modifier.padding(L.Space.x16), verticalArrangement = Arrangement.spacedBy(L.Space.x4)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
            Text(value, style = MaterialTheme.typography.displaySmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (delta != null) DeltaLine(delta)
            if (!trend.isNullOrEmpty()) LSparkline(trend)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun DeltaLine(delta: Delta) {
    val arrow = if (delta.up) "↑" else "↓"
    Text(
        "$arrow ${delta.label}",
        style = MaterialTheme.typography.labelMedium,
        color = if (delta.good) L.Income else L.Danger,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(L.Radius)
    val base = modifier.fillMaxWidth().clip(shape)
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .border(1.dp, L.Line, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(L.Space.x8),
        content = content
    )
}

@Composable
fun LStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = L.OnBox) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(L.Radius)).background(L.Box)
            .border(1.dp, L.Line, RoundedCornerShape(L.Radius)).padding(L.Space.x16),
        verticalArrangement = Arrangement.spacedBy(L.Space.x4)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** List item. Title left, value right, one optional short line. */
@Composable
fun LRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: String? = null,
    trailingColor: Color = L.OnBox,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(L.Radius)
    val base = modifier.fillMaxWidth().clip(shape)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .border(1.dp, L.Line, shape)
            .padding(horizontal = L.Space.x16, vertical = L.Space.x16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(L.Space.x12)
    ) {
        if (icon != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(L.BoxDeep),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = L.InkMuted, modifier = Modifier.size(16.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(L.Space.x4)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1)
        end?.invoke()
    }
}

@Composable
fun LSection(text: String, action: String? = null, onAction: () -> Unit = {}, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = L.Space.x8)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = L.Target),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = L.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            if (action != null) LTextLink(action, onAction)
        }
        if (!sub.isNullOrBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Form group label. One short line, muted. */
@Composable
fun LFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = L.InkMuted,
        modifier = modifier.padding(top = L.Space.x8).semantics { heading() },
    )
}

/** Empty state: icon + one line. No paragraphs. */
@Composable
fun LEmpty(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = L.Space.x24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(L.Box).border(1.dp, L.Line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = L.InkMuted, modifier = Modifier.size(24.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
    }
}

@Composable
fun LButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).height(L.Target),
        shape = RoundedCornerShape(L.Radius),
        colors = ButtonDefaults.buttonColors(containerColor = L.Accent, contentColor = L.OnAccent)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun LGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = true,
    destructive: Boolean = false,
) {
    val ink = if (destructive) L.Danger else L.Ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).height(L.Target),
        shape = RoundedCornerShape(L.Radius),
        border = BorderStroke(1.dp, if (destructive) L.Danger else L.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Text action. At least 48dp tall. */
@Composable
fun LTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = L.Target).clip(L.RadiusPill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = L.Accent,
            modifier = Modifier.padding(horizontal = L.Space.x8, vertical = L.Space.x8),
        )
    }
}

@Composable
fun LFab(icon: ImageVector, onClick: () -> Unit, label: String = "Add") {
    FloatingActionButton(
        onClick = onClick,
        containerColor = L.Accent,
        contentColor = L.OnAccent,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) { Icon(icon, contentDescription = label) }
}

@Composable
fun LField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(L.Radius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = L.Accent,
            unfocusedBorderColor = L.Line,
            focusedLabelColor = L.Accent,
            cursorColor = L.Accent,
            focusedTextColor = L.Ink,
            unfocusedTextColor = L.Ink
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun LChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LChoiceChip(text, selected, onClick, modifier, Role.Tab)
}

/** Filter or multi-select. Not a button. */
@Composable
fun LFilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LChoiceChip(text, selected, onClick, modifier, Role.Tab)
}

/** A chip that does something. Exclusive choices of 2–4 options belong in [LSegments]. */
@Composable
fun LActionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    LChoiceChip(text, selected, onClick, modifier, Role.Button)
}

@Composable
private fun LChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    role: Role,
) {
    Box(
        modifier = modifier
            .heightIn(min = L.Target)
            .selectable(selected = selected, role = role, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) L.OnAccent else L.Ink,
            modifier = Modifier
                .clip(L.RadiusPill)
                .background(if (selected) L.Accent else Color.Transparent)
                .border(1.dp, if (selected) L.Accent else L.Line, L.RadiusPill)
                .padding(horizontal = L.Space.x16, vertical = L.Space.x8)
        )
    }
}

/**
 * Bottom sheet. [primary] is optional: a missing primary means swipe or the scrim closes the sheet.
 * Pass [secondaryDestructive] when the second button deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LSheet(
    title: String,
    onDismiss: () -> Unit,
    primary: String? = null,
    onPrimary: () -> Unit = {},
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    secondaryDestructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = L.Page) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = L.Gutter).padding(bottom = L.Space.x24),
            verticalArrangement = Arrangement.spacedBy(L.Space.x16)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = L.Ink, modifier = Modifier.semantics { heading() })
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(L.Space.x16)
            ) { content() }
            if (primary != null) LButton(primary, onPrimary, enabled = primaryEnabled)
            if (secondary != null) LGhostButton(secondary, onSecondary, destructive = secondaryDestructive)
        }
    }
}

@Composable
fun LProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = L.Accent,
    marker: Float? = null,
) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(L.Space.x8).clip(L.RadiusPill).background(L.BoxDeep)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(L.RadiusPill).background(color)
        )
        if (marker != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * marker.coerceIn(0f, 1f) - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(L.Ink)
            )
        }
    }
}

/** Same control as [LToolButton]. Kept so existing call sites still compile. */
@Composable
fun LIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    LToolButton(icon, label, onClick)
}

/** Page title row: optional back, trailing actions, and the profile avatar. */
@Composable
fun LHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = L.Target),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(L.Space.x4),
    ) {
        if (onBack != null) LToolButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = L.Ink,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions?.invoke(this)
        ProfileButton()
    }
}

/** One notice. [action] is a short verb such as Undo. */
@Composable
fun LSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = L.Target)
            .clip(RoundedCornerShape(L.Radius))
            .background(L.Box)
            .border(1.dp, L.Line, RoundedCornerShape(L.Radius))
            .padding(horizontal = L.Space.x16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(L.Space.x8),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = L.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) LTextLink(action, onAction)
    }
}

/** 24dp badge inside a 48dp target. */
@Composable
fun LRemoveBadge(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Remove") {
    Box(
        modifier.size(L.Target).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(L.Ink), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = L.Box, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Amount with no sign. Pass [income] for a leading +.
 * Colour income with [L.Income], a normal amount with [L.Ink], and overdue or over-limit with [L.Danger] only.
 */
fun money(amount: Double, symbol: String = LCurrency.symbol, income: Boolean = false): String {
    val abs = kotlin.math.abs(amount)
    val body = if (abs >= 1000) "%,.0f".format(abs) else "%,.2f".format(abs)
    val core = symbol + body
    return if (income) "+$core" else core
}

fun moneyColor(income: Boolean = false, over: Boolean = false): Color = when {
    over -> L.Danger
    income -> L.Income
    else -> L.Ink
}

/** "Today", a weekday ("Fri"), "3d ago", or "Oct 12". */
fun relativeDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when (days) {
        0L -> "Today"
        in 1..6 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        in -6..-1 -> "${-days}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date)
    }
}

/** Short time for the device locale. */
fun localeTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "Due today", "Due Oct 12", or "3 days overdue". */
fun dueLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val late = ChronoUnit.DAYS.between(date, today)
    return when {
        date == today -> "Due today"
        late > 0 -> if (late == 1L) "1 day overdue" else "$late days overdue"
        else -> "Due " + DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date)
    }
}
