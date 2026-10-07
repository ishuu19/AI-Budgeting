package com.ledgerai.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledgerai.app.presentation.screens.ai.AiAssistantScreen
import com.ledgerai.app.presentation.screens.analytics.AnalyticsScreen
import com.ledgerai.app.presentation.screens.bills.BillsScreen
import com.ledgerai.app.presentation.screens.budget.BudgetScreen
import com.ledgerai.app.presentation.screens.dashboard.DashboardScreen
import com.ledgerai.app.presentation.screens.debts.DebtsScreen
import com.ledgerai.app.presentation.screens.forecast.ForecastScreen
import com.ledgerai.app.presentation.screens.goals.GoalsScreen
import com.ledgerai.app.presentation.screens.settings.SettingsScreen
import com.ledgerai.app.presentation.screens.transactions.TransactionsScreen
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard",    "Dashboard",     Icons.Filled.Dashboard)
    object Transactions : Screen("transactions", "Transactions",  Icons.Filled.Receipt)
    object Budget       : Screen("budget",       "Budget",        Icons.Filled.AccountBalance)
    object Forecast     : Screen("forecast",     "Forecast",      Icons.AutoMirrored.Filled.TrendingUp)
    object Debts        : Screen("debts",        "Debts",         Icons.Filled.People)
    object Analytics    : Screen("analytics",    "Analytics",     Icons.Filled.Analytics)
    object Goals        : Screen("goals",        "Goals",         Icons.Filled.EmojiEvents)
    object Bills        : Screen("bills",        "Bills",         Icons.Filled.CreditCard)
    object AiAssistant  : Screen("ai_assistant", "AI Chat",       Icons.Filled.Assistant)
    object Settings     : Screen("settings",     "Settings",      Icons.Filled.Settings)
    object VoiceRecord  : Screen("voice_record", "Add by Voice",  Icons.Filled.Mic)
}

private val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.Budget,
    Screen.Forecast,
    Screen.Debts,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Hide bottom nav on voice screen for immersive UX
    val showBottomNav = currentRoute != Screen.VoiceRecord.route

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                    onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                    onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onNavigateToVoice = { navController.navigate(Screen.VoiceRecord.route) }
                )
            }
            composable(Screen.Budget.route) {
                BudgetScreen(onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) })
            }
            composable(Screen.Forecast.route) { ForecastScreen() }
            composable(Screen.Debts.route) { DebtsScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
            composable(Screen.Goals.route) { GoalsScreen() }
            composable(Screen.Bills.route) { BillsScreen() }
            composable(Screen.AiAssistant.route) { AiAssistantScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.VoiceRecord.route) {
                VoiceRecorderScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
