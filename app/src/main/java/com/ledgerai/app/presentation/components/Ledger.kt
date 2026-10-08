package com.ledgerai.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledgerai.app.R
import com.ledgerai.app.presentation.theme.*

/*
 * LedgerAI design system.
 * Page: white. Boxes: royal green. Accent: gold. Text on boxes: white.
 * Spacing scale: 4 · 8 · 12 · 16 · 20 · 24 · 32.
 */

object L {
    val Page = Color.White
    val Box = BrandEmerald
    val BoxDeep = BrandEmeraldDark
    val Gold = BrandGold
    val GoldSoft = BrandGoldLight
    val OnBox = Color.White
    val OnBoxMuted = Color.White.copy(alpha = 0.72f)
    val Ink = com.ledgerai.app.presentation.theme.Ink
    val InkMuted = com.ledgerai.app.presentation.theme.InkMuted
    val Line = Color(0xFFE9ECEA)
    val Danger = Color(0xFFFFB4A9)
    val Radius = 20.dp
    val RadiusSm = 14.dp
    val Gutter = 20.dp
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
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = L.Page,
        floatingActionButton = { fab?.invoke() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = L.Ink
                            )
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = L.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    action?.invoke(this)
                }
            }
            content()
        }
    }
}

/** Primary number. One per screen. */
@Composable
fun LHero(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, valueColor: Color = L.OnBox) {
    Surface(modifier = modifier.fillMaxWidth(), color = L.Box, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = L.Gold)
            Text(value, style = MaterialTheme.typography.displaySmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted, maxLines = 1)
        }
    }
}

@Composable
fun LCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(L.Radius))
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun LStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = L.OnBox) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(L.RadiusSm)).background(L.Box).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = L.Gold, maxLines = 1)
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
    trailingColor: Color = L.Gold,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null
) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(L.RadiusSm))
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .background(L.Box)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(L.BoxDeep),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = L.Gold, modifier = Modifier.size(18.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = trailingColor, maxLines = 1)
        end?.invoke()
    }
}

@Composable
fun LSection(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = L.Ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = L.Box,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp)
            )
        }
    }
}

/** Empty state: icon + one line. No paragraphs. */
@Composable
fun LEmpty(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(L.Box), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = L.Gold, modifier = Modifier.size(28.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted)
    }
}

@Composable
fun LButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(L.RadiusSm),
        colors = ButtonDefaults.buttonColors(containerColor = L.Gold, contentColor = L.BoxDeep)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun LGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(L.RadiusSm),
        border = BorderStroke(1.5.dp, L.Box),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = L.Box)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun LFab(icon: ImageVector, onClick: () -> Unit, label: String = "Add") {
    FloatingActionButton(
        onClick = onClick,
        containerColor = L.Gold,
        contentColor = L.BoxDeep,
        shape = CircleShape
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
        shape = RoundedCornerShape(L.RadiusSm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = L.Box,
            unfocusedBorderColor = L.Line,
            focusedLabelColor = L.Box,
            cursorColor = L.Box,
            focusedTextColor = L.Ink,
            unfocusedTextColor = L.Ink
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun LChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) L.OnBox else L.Box,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) L.Box else Color.Transparent)
            .border(1.dp, if (selected) L.Box else L.Box.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** Bottom sheet with a title and a primary action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LSheet(
    title: String,
    onDismiss: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = L.Page) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = L.Gutter).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = L.Ink)
            content()
            Spacer(Modifier.height(4.dp))
            LButton(primary, onPrimary, enabled = primaryEnabled)
            if (secondary != null) LGhostButton(secondary, onSecondary)
        }
    }
}

@Composable
fun LProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = L.Gold) {
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(L.BoxDeep)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(50)).background(color)
        )
    }
}

@Composable
fun LIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = L.Box) }
}

fun money(amount: Double, symbol: String = "$"): String {
    val abs = kotlin.math.abs(amount)
    val body = if (abs >= 1000) "%,.0f".format(abs) else "%,.2f".format(abs)
    return (if (amount < 0) "-" else "") + symbol + body
}
