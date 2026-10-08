package com.ledgerai.app.presentation.screens.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.ledgerai.app.presentation.navigation.OpenItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import com.ledgerai.app.presentation.screens.transactions.shortDate
import com.ledgerai.app.presentation.components.LChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Emits today's date now and again right after each midnight, so view models never hold a stale "now". */
fun todayTicker(): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.toLocalDate())
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight + 1_000L)
    }
}.distinctUntilChanged()

/** Today's date as state; changes at midnight so screens never show a stale day. */
@Composable
fun rememberToday(): LocalDate {
    val flow = androidx.compose.runtime.remember { todayTicker() }
    val today by flow.collectAsState(initial = LocalDate.now())
    return today
}

/** Gives up on an open request that never matches an item (deleted, or data never arrives). */
@Composable
fun OpenTimeout(open: OpenItem?, onOpened: () -> Unit) {
    LaunchedEffect(open?.nonce) {
        if (open != null) {
            delay(4_000L)
            onOpened()
        }
    }
}

/** Shows [message] once on the snackbar host, then asks the owner to clear it. */
@Composable
fun SnackEffect(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            onShown()
        }
    }
}

/** Keeps digits and at most one decimal point. */
fun cleanDecimal(raw: String): String? {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    return if (cleaned.count { it == '.' } <= 1) cleaned else null
}

/** Amount field with a number keyboard and input filtering. */
@Composable
fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    LField(
        value = value,
        onValueChange = { raw -> cleanDecimal(raw)?.let(onValueChange) },
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

/** Optional date chosen with a date picker: label, a chip with the date, and Clear. */
@Composable
fun OptionalDateField(label: String, date: LocalDate?, onDate: (LocalDate?) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        DatePickChip(
            date = date ?: LocalDate.now(),
            selected = date != null,
            onDate = { onDate(it) },
            label = date?.let(::shortDate) ?: "Set date"
        )
        if (date != null) LChip("Clear", false, onClick = { onDate(null) })
    }
}

/** "No item" marker for saveable sheet ids. */
const val NO_ID = Long.MIN_VALUE

/**
 * One container holding at most [limit] rows; the rest sit behind a "Show all" row.
 * [id] must be unique per item. [expandKey] keeps the expanded state per group.
 */
@Composable
fun <T> LimitedGroup(
    items: List<T>,
    id: (T) -> Any,
    expandKey: String,
    modifier: Modifier = Modifier,
    limit: Int = 5,
    row: @Composable (T) -> Unit
) {
    var expanded by rememberSaveable(expandKey) { mutableStateOf(false) }
    val shown = if (expanded || items.size <= limit) items else items.take(limit)
    LGroup(modifier) {
        shown.forEachIndexed { index, item ->
            androidx.compose.runtime.key(id(item)) {
                if (index > 0) LGroupDivider()
                row(item)
            }
        }
        if (items.size > limit) {
            LGroupDivider()
            LGroupRow(
                title = if (expanded) "Show less" else "Show all ${items.size}",
                onClick = { expanded = !expanded }
            )
        }
    }
}

@Composable
fun MutedLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, modifier = modifier.padding(horizontal = 4.dp))
}
