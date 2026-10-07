package com.ledgerai.app.presentation.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.*
import com.ledgerai.app.presentation.theme.ExpenseRed
import com.ledgerai.app.presentation.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BudgetAI", style = MaterialTheme.typography.titleLarge)
                        Text(state.currentMonth, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAi) {
                        Icon(Icons.Filled.Assistant, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Greeting ──────────────────────────────────────────────────────
            item {
                Text(
                    state.greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Income / Expense Summary ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        label = "Income",
                        amount = state.monthlyIncome,
                        isExpense = false,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Expenses",
                        amount = state.monthlyExpenses,
                        isExpense = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Net Balance ────────────────────────────────────────────────────
            item {
                val net = state.monthlyIncome - state.monthlyExpenses
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (net >= 0) IncomeGreen.copy(alpha = 0.1f)
                                         else ExpenseRed.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Net Balance", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${if (net >= 0) "+" else ""}\$${"%.2f".format(net)}",
                            color = if (net >= 0) IncomeGreen else ExpenseRed,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Health Score ───────────────────────────────────────────────────
            state.healthScore?.let { score ->
                item {
                    SectionTitle("Financial Health")
                    Spacer(Modifier.height(8.dp))
                    HealthScoreCard(score = score)
                }
            }

            // ── Budget Overview ────────────────────────────────────────────────
            if (state.budgets.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Budget Overview",
                        action = {
                            TextButton(onClick = onNavigateToAnalytics) { Text("See all") }
                        }
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.budgets.take(5)) { budget ->
                            BudgetMiniCard(budget = budget)
                        }
                    }
                }
            }

            // ── Recent Transactions ────────────────────────────────────────────
            if (state.recentTransactions.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Recent Transactions",
                        action = {
                            TextButton(onClick = onNavigateToTransactions) { Text("See all") }
                        }
                    )
                }
                items(state.recentTransactions) { tx ->
                    TransactionRow(transaction = tx)
                }
            } else {
                item {
                    EmptyStateCard(
                        emoji = "💸",
                        title = "No transactions yet",
                        subtitle = "Add your first transaction or use the voice widget!"
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amount: Double,
    isExpense: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isExpense) ExpenseRed else IncomeGreen
    val icon = if (isExpense) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "\$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun HealthScoreCard(score: com.ledgerai.app.domain.model.FinancialHealthScore) {
    val scoreColor = when {
        score.score >= 80 -> Color(0xFF22C55E)
        score.score >= 65 -> Color(0xFF84CC16)
        score.score >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Financial Health Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(score.grade, color = scoreColor, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "${score.score}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = scoreColor
                )
            }
            Spacer(Modifier.height(12.dp))
            BudgetProgressBar(usagePercent = score.score)
            Spacer(Modifier.height(8.dp))
            Text(score.summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetMiniCard(budget: com.ledgerai.app.domain.model.Budget) {
    Card(modifier = Modifier.width(140.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryDot(category = budget.category)
            Text(budget.category.displayName, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            BudgetProgressBar(usagePercent = budget.usagePercent)
            Text("${budget.usagePercent}% used", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionRow(transaction: com.ledgerai.app.domain.model.Transaction) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(transaction.category, size = 12.dp)
                Column {
                    Text(
                        transaction.merchant.ifEmpty { transaction.category.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(transaction.date.toString(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AmountText(amount = transaction.amount, isExpense = isExpense, prefix = "$")
        }
    }
}
