package com.ledgerai.app.presentation.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToChat: (insight: String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = L.Page) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = L.Gold)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LLogo(36.dp)
                    Text(
                        if (state.userName.isNotBlank()) "Hi, ${state.userName}" else "Today",
                        style = MaterialTheme.typography.titleLarge,
                        color = L.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    LIconButton(Icons.Filled.Search, "Search", onNavigateToSearch)
                    LIconButton(Icons.Filled.Mic, "Voice", onNavigateToVoice)
                    LIconButton(Icons.Filled.Settings, "Settings", onNavigateToSettings)
                }
            }

            item {
                val remaining = state.remainingBudget
                val cash = state.cashOnHand
                val heroValue = when {
                    state.trackExpensesOnly -> state.monthlyExpenses
                    cash != null -> cash + state.monthlyIncome - state.monthlyExpenses
                    else -> state.netBalance
                }
                LHero(
                    label = when {
                        state.trackExpensesOnly -> "Spent"
                        cash != null -> "On hand"
                        else -> "Balance"
                    },
                    value = money(heroValue),
                    sub = if (state.trackExpensesOnly) state.currentMonth
                    else if (remaining != null) "${money(remaining)} left" else state.currentMonth,
                    valueColor = if (!state.trackExpensesOnly && heroValue < 0) L.Danger else L.OnBox
                )
            }

            if (!state.trackExpensesOnly) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LStat("Income", money(state.monthlyIncome), Modifier.weight(1f))
                        LStat("Spent", money(state.monthlyExpenses), Modifier.weight(1f))
                    }
                }
            }

            state.healthScore?.let { health ->
                item {
                    LRow(
                        title = "Health",
                        sub = health.grade,
                        trailing = health.score.toString()
                    )
                }
            }

            val soon = state.upcoming.take(3)
            if (soon.isNotEmpty()) {
                item { LSection("Soon") }
                items(soon, key = { it.id }) { item ->
                    LRow(title = item.title, sub = item.subtitle)
                }
            }

            state.aiInsightCard?.let { card ->
                item {
                    LCard(onClick = { onNavigateToChat(card.chatContext) }) {
                        Text("Insight", style = MaterialTheme.typography.labelMedium, color = L.Gold)
                        Text(
                            card.body.ifBlank { card.title },
                            style = MaterialTheme.typography.bodyMedium,
                            color = L.OnBox,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (state.quote.isNotBlank()) {
                item {
                    Text(
                        state.quote,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = L.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }

            item { LSection("Recent", action = "All", onAction = onNavigateToTransactions) }

            if (state.recentTransactions.isEmpty()) {
                item { LEmpty(Icons.AutoMirrored.Filled.ReceiptLong, "No activity") }
            } else {
                items(state.recentTransactions.take(5), key = { it.id }) { tx ->
                    TransactionRow(tx, onClick = onNavigateToTransactions)
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
private fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    val isIncome = tx.type == TransactionType.INCOME
    LRow(
        title = tx.merchant.ifBlank { tx.category.displayName },
        sub = tx.date.format(dayFormat),
        trailing = (if (isIncome) "+" else "") + money(if (isIncome) tx.amount else -tx.amount),
        trailingColor = if (isIncome) L.Gold else L.OnBox,
        icon = categoryIcon(tx.category),
        onClick = onClick
    )
}

private fun categoryIcon(category: TransactionCategory): ImageVector = when (category) {
    TransactionCategory.FOOD -> Icons.Filled.Restaurant
    TransactionCategory.TRANSPORT -> Icons.Filled.DirectionsCar
    TransactionCategory.ENTERTAINMENT -> Icons.Filled.Movie
    TransactionCategory.SHOPPING -> Icons.Filled.ShoppingBag
    TransactionCategory.HEALTH -> Icons.Filled.Favorite
    TransactionCategory.RENT -> Icons.Filled.Home
    TransactionCategory.UTILITIES -> Icons.Filled.Bolt
    TransactionCategory.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    TransactionCategory.EDUCATION -> Icons.Filled.School
    TransactionCategory.SALARY -> Icons.Filled.Payments
    TransactionCategory.FREELANCE -> Icons.Filled.Work
    TransactionCategory.OTHER -> Icons.Filled.Category
}
