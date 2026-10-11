package com.ledgerai.app.presentation.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.Subscription
import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import com.ledgerai.app.domain.subscriptions.SubscriptionCharges
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.transactions.shortDate
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.SnackEffect
import com.ledgerai.app.presentation.screens.money.subscriptionFigure
import com.ledgerai.app.presentation.screens.money.subscriptionMonth
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.time.LocalDate

private const val CANCEL_NOTE =
    "Cancelling here only changes this list. LedgerAI does not contact the merchant."

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onBack: (() -> Unit)? = null
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val notice by viewModel.notice.collectAsState()
    SubscriptionsScreen(
        subscriptions = subscriptions,
        today = viewModel.currentDay(),
        notice = notice,
        onAdd = viewModel::add,
        onMarkCancelled = viewModel::markCancelled,
        onDismissNotice = viewModel::dismissNotice,
        onBack = onBack
    )
}

@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    today: LocalDate,
    notice: String?,
    onAdd: (NewSubscription) -> Unit,
    onMarkCancelled: (Subscription) -> Unit,
    onDismissNotice: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf(-1L) }
    var confirmId by rememberSaveable { mutableStateOf(-1L) }
    val snackbar = remember { SnackbarHostState() }
    SnackEffect(notice, snackbar, onDismissNotice)
    val soonest = subscriptions
        .map { it to SubscriptionCharges.nextChargeDate(it.nextRenewalOn, it.period, today) }
        .minWithOrNull(compareBy({ it.second }, { it.first.id }))
    val dueSoon = SubscriptionCharges.upcomingWithin(subscriptions, today, 30)
    val month = subscriptionMonth(subscriptions)
    val open = subscriptions.firstOrNull { it.id == openId }
    val confirming = subscriptions.firstOrNull { it.id == confirmId }

    LScreen(
        title = "Subscriptions",
        onBack = onBack,
        action = { LToolButton(Icons.Filled.Add, "New subscription", onClick = { showForm = true }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        subscriptionBody(
            subscriptions = subscriptions,
            today = today,
            soonest = soonest,
            dueSoonCount = dueSoon.size,
            monthly = subscriptionFigure(month),
            onOpen = { openId = it.id }
        )
    }

    open?.let { subscription ->
        val chargeOn = SubscriptionCharges.nextChargeDate(subscription.nextRenewalOn, subscription.period, today)
        LSheet(
            title = subscription.merchant,
            onDismiss = { openId = -1L }
        ) {
            Text(figure(subscription.amount), style = MaterialTheme.typography.titleLarge, color = L.Ink)
            Text(
                "Due ${shortDate(chargeOn)} · ${subscription.period.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted
            )
            TextButton(onClick = { confirmId = subscription.id }) {
                Text("Mark cancelled", color = L.Danger)
            }
        }
    }
    confirming?.let { subscription ->
        AlertDialog(
            onDismissRequest = { confirmId = -1L },
            containerColor = L.Page,
            title = { Text("Mark cancelled?", color = L.Ink) },
            text = { Text(CANCEL_NOTE, color = L.InkMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmId = -1L
                    openId = -1L
                    onMarkCancelled(subscription)
                }) { Text("Mark cancelled", color = L.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmId = -1L }) { Text("Cancel", color = L.InkMuted) }
            }
        )
    }

    if (showForm) {
        AddSubscriptionSheet(
            onDismiss = { showForm = false },
            onSave = { draft ->
                onAdd(draft)
                showForm = false
            }
        )
    }
}

private fun LazyListScope.subscriptionBody(
    subscriptions: List<Subscription>,
    today: LocalDate,
    soonest: Pair<Subscription, LocalDate>?,
    dueSoonCount: Int,
    monthly: String,
    onOpen: (Subscription) -> Unit
) {
    if (subscriptions.isEmpty() || soonest == null) {
        item(key = "empty") { LEmpty(Icons.Filled.EventRepeat, "No subscriptions yet") }
        return
    }
    val dueBit = when (dueSoonCount) {
        0 -> null
        1 -> "1 in 30 days"
        else -> "$dueSoonCount in 30 days"
    }
    item(key = "next") {
        LHero(
            label = "Monthly",
            value = monthly,
            sub = listOfNotNull(
                "${soonest.first.merchant} · Due ${shortDate(soonest.second)}",
                dueBit
            ).joinToString(" · ")
        )
    }
    item(key = "list") {
        LGroup {
            subscriptions.forEachIndexed { index, subscription ->
                if (index > 0) LGroupDivider()
                val chargeOn = SubscriptionCharges.nextChargeDate(
                    subscription.nextRenewalOn,
                    subscription.period,
                    today
                )
                LGroupRow(
                    title = subscription.merchant,
                    sub = "Due ${shortDate(chargeOn)} · ${subscription.period.displayName}",
                    trailing = figure(subscription.amount),
                    onClick = { onOpen(subscription) }
                )
            }
        }
    }
}

@Composable
private fun AddSubscriptionSheet(onDismiss: () -> Unit, onSave: (NewSubscription) -> Unit) {
    var merchant by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(SubscriptionPeriod.MONTHLY) }
    var nextRenewalOn by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val amount = parseAmount(amountText)
    val canSave = merchant.isNotBlank() && amount != null

    LSheet(
        title = "New subscription",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = {
            val parsed = amount ?: return@LSheet
            onSave(
                NewSubscription(
                    merchant = merchant.trim(),
                    amount = parsed,
                    period = period,
                    nextRenewalOn = nextRenewalOn
                )
            )
        },
        primaryEnabled = canSave
    ) {
        LField(merchant, { merchant = it }, label = "Name")
        DecimalField(amountText, { amountText = it }, if (amountText.isEmpty()) "Unknown" else "Amount")
        ChipsRow {
            SubscriptionPeriod.entries.forEach { choice ->
                LChip(choice.displayName, period == choice, onClick = { period = choice })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Next charge", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            DatePickChip(
                date = nextRenewalOn,
                selected = true,
                onDate = { nextRenewalOn = it },
                label = shortDate(nextRenewalOn)
            )
        }
    }
}

/** Unknown stays the word Unknown. Known amounts, including zero, use the money figure. */
internal fun figure(amount: SubscriptionAmount): String = when (amount) {
    is SubscriptionAmount.Known -> money(amount.value)
    SubscriptionAmount.Unknown -> "Unknown"
}

internal fun parseAmount(raw: String): SubscriptionAmount? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return SubscriptionAmount.Unknown
    val value = trimmed.toDoubleOrNull() ?: return null
    if (value < 0.0 || value.isNaN() || value.isInfinite()) return null
    return SubscriptionAmount.Known(value)
}
