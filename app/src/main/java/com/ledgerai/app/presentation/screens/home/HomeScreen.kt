package com.ledgerai.app.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.home.HomeSnapshot
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LHero
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.money

/** Route registered in the NavHost for [HomeScreen]. */
const val HomeRoute = "home"

/**
 * What needs attention today. The large figure is money spent today, from [snapshot].
 * Household, shopping, and receipts stay empty until those modules are wired.
 */
@Composable
fun HomeScreen(
    snapshot: HomeSnapshot,
    onReviewSpending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LScreen(title = "Today", modifier = modifier) {
        item(key = "money") {
            LHero(
                label = "Spent today",
                value = money(snapshot.todaySpend),
                sub = "This month ${money(snapshot.monthSpend)}",
            )
        }
        item(key = "review") {
            LButton(text = "Review spending", onClick = onReviewSpending)
        }
        item(key = "recent-heading") { LSection("Recent") }
        item(key = "recent") {
            if (snapshot.recent.isEmpty()) {
                QuietLine("No transactions yet.")
            } else {
                LGroup {
                    snapshot.recent.forEachIndexed { index, tx ->
                        if (index > 0) LGroupDivider()
                        RecentRow(tx)
                    }
                }
            }
        }
        item(key = "household") {
            Placeholder(title = "Household", body = "Household sharing isn't connected yet.")
        }
        item(key = "shopping") {
            Placeholder(title = "Shopping", body = "Shopping lists aren't connected yet.")
        }
        item(key = "receipts") {
            Placeholder(title = "Receipts", body = "Receipts aren't connected yet.")
        }
    }
}

@Composable
private fun RecentRow(tx: Transaction) {
    val income = tx.type == TransactionType.INCOME
    LGroupRow(
        title = tx.merchant.ifBlank { tx.category.displayName },
        sub = tx.category.displayName,
        trailing = (if (income) "+" else "-") + money(tx.amount),
        trailingColor = if (income) L.Gold else L.OnBox,
    )
}

@Composable
private fun Placeholder(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = L.Ink,
            modifier = Modifier.semantics { heading() },
        )
        QuietLine(body)
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = L.InkMuted,
    )
}
