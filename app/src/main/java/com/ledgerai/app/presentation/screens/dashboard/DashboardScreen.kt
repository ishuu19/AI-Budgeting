package com.ledgerai.app.presentation.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
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
    onNavigateToVoice: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "LedgerAI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.currentMonth,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToVoice,
                icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                text = { Text("Add by voice") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title → subtitle takeaway → primary KPI (F-pattern)
            item {
                Text(
                    state.greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                PrimaryKpi(
                    label = "Net this month",
                    formattedValue = formatSignedCurrency(state.netBalance),
                    takeaway = state.takeaway,
                    valueColor = if (state.netBalance >= 0) IncomeGreen else ExpenseRed
                )
            }

            // Supporting metrics — muted, not competing with primary KPI
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SupportingMetric(
                        label = "Income",
                        amount = state.monthlyIncome,
                        accent = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SupportingMetric(
                        label = "Expenses",
                        amount = state.monthlyExpenses,
                        accent = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            state.healthScore?.let { score ->
                item {
                    SectionTitle("Health")
                    Spacer(Modifier.height(8.dp))
                    HealthScoreCard(score = score)
                }
            }

            if (state.budgets.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Budgets",
                        action = {
                            TextButton(onClick = onNavigateToAnalytics) {
                                Text("Details")
                            }
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

            if (state.recentTransactions.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Recent activity",
                        action = {
                            TextButton(onClick = onNavigateToTransactions) {
                                Text("See all")
                            }
                        }
                    )
                }
                items(state.recentTransactions) { tx ->
                    TransactionRow(transaction = tx)
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Use Add by voice to log your first entry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSignedCurrency(amount: Double): String {
    val sign = when {
        amount > 0 -> "+"
        amount < 0 -> "-"
        else -> ""
    }
    return "$sign\$${"%.2f".format(kotlin.math.abs(amount))}"
}

@Composable
private fun SupportingMetric(
    label: String,
    amount: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "\$${"%.2f".format(amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
    }
}

@Composable
private fun HealthScoreCard(score: com.ledgerai.app.domain.model.FinancialHealthScore) {
    val scoreColor = when {
        score.score >= 80 -> IncomeGreen
        score.score >= 65 -> Color(0xFF84CC16)
        score.score >= 50 -> Color(0xFFF59E0B)
        else -> ExpenseRed
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    score.grade,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor
                )
                Text(
                    score.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Text(
                text = "${score.score}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
        }
    }
}

@Composable
private fun BudgetMiniCard(budget: com.ledgerai.app.domain.model.Budget) {
    Surface(
        modifier = Modifier.width(140.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryDot(category = budget.category)
            Text(
                budget.category.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            BudgetProgressBar(usagePercent = budget.usagePercent)
            Text(
                "${budget.usagePercent}% used",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionRow(transaction: com.ledgerai.app.domain.model.Transaction) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDot(transaction.category, size = 10.dp)
            Column {
                Text(
                    transaction.merchant.ifEmpty { transaction.category.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    transaction.date.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AmountText(amount = transaction.amount, isExpense = isExpense, prefix = "$")
    }
}
