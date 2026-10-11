package com.ledgerai.app.presentation.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.subscriptions.NewSubscription
import com.ledgerai.app.domain.subscriptions.Subscription
import com.ledgerai.app.domain.subscriptions.SubscriptionAmount
import com.ledgerai.app.domain.subscriptions.SubscriptionCharges
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.screens.money.DecimalField
import com.ledgerai.app.presentation.screens.money.SnackEffect
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val chargeDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
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
    val snackbar = remember { SnackbarHostState() }
    SnackEffect(notice, snackbar, onDismissNotice)
    val soonest = subscriptions
        .map { it to SubscriptionCharges.nextChargeDate(it.nextRenewalOn, it.period, today) }
        .minWithOrNull(compareBy({ it.second }, { it.first.id }))
    val dueSoon = SubscriptionCharges.upcomingWithin(subscriptions, today, 30)

    LScreen(
        title = "Subscriptions",
        onBack = onBack,
        action = {
            TextButton(onClick = { showForm = true }) {
                Text("Add", color = L.Primary, style = MaterialTheme.typography.labelLarge)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        subscriptionBody(
            subscriptions = subscriptions,
            today = today,
            soonest = soonest,
            dueSoonCount = dueSoon.size,
            onAdd = { showForm = true },
            onMarkCancelled = onMarkCancelled
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
    onAdd: () -> Unit,
    onMarkCancelled: (Subscription) -> Unit
) {
    if (subscriptions.isEmpty() || soonest == null) {
        item(key = "empty") {
            LEmpty(Icons.Filled.EventRepeat, "No subscriptions yet")
            LButton("Add subscription", onAdd)
        }
        return
    }
    item(key = "next") {
        LHero(
            label = "Next charge",
            value = figure(soonest.first.amount),
            sub = "${soonest.first.merchant} · ${chargeDate.format(soonest.second)}"
        )
    }
    item(key = "soon") {
        Text(
            if (dueSoonCount == 1) "1 due in the next 30 days" else "$dueSoonCount due in the next 30 days",
            style = MaterialTheme.typography.bodyMedium,
            color = L.InkMuted
        )
    }
    item(key = "cancel-note") {
        Text(CANCEL_NOTE, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
    }
    items(subscriptions.size, key = { subscriptions[it].id }) { index ->
        val subscription = subscriptions[index]
        val chargeOn = SubscriptionCharges.nextChargeDate(
            subscription.nextRenewalOn,
            subscription.period,
            today
        )
        SubscriptionRow(subscription, chargeOn, onMarkCancelled)
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    chargeOn: LocalDate,
    onMarkCancelled: (Subscription) -> Unit
) {
    LCard {
        Text(
            figure(subscription.amount),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = L.Gold
        )
        Text(subscription.merchant, style = MaterialTheme.typography.titleMedium, color = L.OnBox)
        Text(
            "Next charge ${chargeDate.format(chargeOn)} · ${subscription.period.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = L.OnBoxMuted
        )
        TextButton(onClick = { onMarkCancelled(subscription) }, modifier = Modifier.fillMaxWidth()) {
            Text("Mark cancelled here", color = L.Gold)
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
        title = "Add subscription",
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
        DecimalField(amountText, { amountText = it }, "Amount")
        Text(
            "Leave amount blank if you don't know it. Blank is not zero.",
            style = MaterialTheme.typography.bodySmall,
            color = L.InkMuted
        )
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
                label = chargeDate.format(nextRenewalOn)
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
