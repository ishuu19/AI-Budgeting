package com.ledgerai.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.screens.ai.AiAssistantScreen
import com.ledgerai.app.presentation.screens.alarms.AlarmsScreen
import com.ledgerai.app.presentation.screens.analytics.AnalyticsScreen
import com.ledgerai.app.presentation.screens.bills.BillsScreen
import com.ledgerai.app.presentation.screens.budget.BudgetScreen
import com.ledgerai.app.presentation.screens.dashboard.DashboardScreen
import com.ledgerai.app.presentation.screens.debts.DebtsScreen
import com.ledgerai.app.presentation.screens.forecast.ForecastScreen
import com.ledgerai.app.presentation.screens.goals.GoalsScreen
import com.ledgerai.app.presentation.screens.notes.NotesScreen
import com.ledgerai.app.presentation.screens.calendar.CalendarScreen
import com.ledgerai.app.presentation.screens.focus.FocusScreen
import com.ledgerai.app.presentation.screens.life.LifeContainerScreen
import com.ledgerai.app.presentation.screens.money.SpendTodayScreen
import com.ledgerai.app.presentation.screens.search.SearchScreen
import com.ledgerai.app.presentation.screens.settings.SettingsScreen
import com.ledgerai.app.presentation.screens.tasks.TasksScreen
import com.ledgerai.app.presentation.screens.transactions.TransactionsScreen
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard    : Screen("dashboard",    "Home",     Icons.Filled.Home)
    object Money        : Screen("money",        "Money",    Icons.Filled.AccountBalanceWallet)
    object VoiceRecord  : Screen("voice_record", "Voice",    Icons.Filled.Mic)
    object Life         : Screen("life",         "Life",     Icons.Filled.CalendarMonth)
    object You          : Screen("you",          "You",      Icons.Filled.Person)

    object Transactions : Screen("transactions", "Spend",    Icons.Filled.Receipt)
    object Budget       : Screen("budget",       "Budget",   Icons.Filled.PieChart)
    object Today        : Screen("today",        "Today",    Icons.Filled.Today)
    object Forecast     : Screen("forecast",     "Forecast", Icons.AutoMirrored.Filled.TrendingUp)
    object Debts        : Screen("debts",        "Debts",    Icons.Filled.People)
    object Analytics    : Screen("analytics",    "Insights", Icons.Filled.Insights)
    object Goals        : Screen("goals",        "Goals",    Icons.Filled.Flag)
    object Bills        : Screen("bills",        "Bills",    Icons.Filled.CreditCard)
    object Tasks        : Screen("tasks",        "Tasks",    Icons.Filled.CheckCircle)
    object Calendar     : Screen("calendar",     "Calendar", Icons.Filled.CalendarMonth)
    object Notes        : Screen("notes",        "Notes",    Icons.AutoMirrored.Filled.StickyNote2)
    object Alarms       : Screen("alarms",       "Alarms",   Icons.Filled.Alarm)
    object AiAssistant  : Screen("ai_assistant", "Ask",      Icons.Filled.AutoAwesome)
    object Search      : Screen("search",       "Search",   Icons.Filled.Search)
    object Focus       : Screen("focus",        "Focus",    Icons.Filled.Timer)
}

private val tabs = listOf(Screen.Dashboard, Screen.Money, Screen.VoiceRecord, Screen.Life, Screen.You)

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/** Always opens the tab's own page. Nested screens are not restored. */
private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = false }
    launchSingleTop = true
    restoreState = false
}

@Composable
fun AppNavigation(
    openVoice: Boolean = false,
    openCalendar: Boolean = false,
    openFocusBlockId: Long = 0L,
    focusTopic: String? = null
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    LaunchedEffect(openVoice) { if (openVoice) nav.tab(Screen.VoiceRecord.route) }
    LaunchedEffect(openCalendar) {
        if (openCalendar) {
            nav.tab(Screen.Life.route)
            nav.go(Screen.Calendar.route)
        }
    }
    LaunchedEffect(openFocusBlockId) {
        if (openFocusBlockId != 0L) {
            nav.go("${Screen.Focus.route}/$openFocusBlockId")
        }
    }

    Scaffold(
        containerColor = L.Page,
        bottomBar = {
            LedgerTabBar(
                selected = { s -> destination?.hierarchy?.any { it.route == s.route } == true },
                onSelect = { nav.tab(it.route) }
            )
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner)
        ) {
            val back: () -> Unit = { nav.popBackStack() }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToTransactions = { nav.go(Screen.Transactions.route) },
                    onNavigateToAnalytics = { nav.go(Screen.Analytics.route) },
                    onNavigateToVoice = { nav.tab(Screen.VoiceRecord.route) },
                    onNavigateToSettings = { nav.tab(Screen.You.route) },
                    onNavigateToSearch = { nav.go(Screen.Search.route) },
                    onNavigateToChat = { insight ->
                        nav.go("${Screen.AiAssistant.route}?insight=${Uri.encode(insight)}")
                    }
                )
            }
            composable(Screen.Money.route) {
                HubScreen(
                    title = "Money",
                    tiles = listOf(Screen.Today, Screen.Transactions, Screen.Budget, Screen.Bills, Screen.Debts, Screen.Goals, Screen.Forecast, Screen.Analytics, Screen.AiAssistant)
                        .map { s -> HubTile(s.label, s.icon) { nav.go(s.route) } }
                )
            }
            composable(Screen.VoiceRecord.route) { VoiceRecorderScreen(embedded = true) }
            composable(Screen.Life.route) {
                LifeContainerScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTasks = { nav.go(Screen.Tasks.route) },
                    onOpenNotes = { nav.go(Screen.Notes.route) },
                    onOpenFocus = { id -> nav.go("${Screen.Focus.route}/$id") }
                )
            }
            composable(Screen.You.route) {
                SettingsScreen(onOpenAi = { nav.go(Screen.AiAssistant.route) })
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(onNavigateToVoice = { nav.tab(Screen.VoiceRecord.route) }, onBack = back)
            }
            composable(Screen.Today.route) { SpendTodayScreen(onBack = back) }
            composable(Screen.Budget.route) {
                BudgetScreen(onNavigateToAi = { nav.go(Screen.AiAssistant.route) }, onBack = back)
            }
            composable(Screen.Forecast.route) { ForecastScreen(onBack = back) }
            composable(Screen.Debts.route) { DebtsScreen(onBack = back) }
            composable(Screen.Analytics.route) { AnalyticsScreen(onBack = back) }
            composable(Screen.Goals.route) { GoalsScreen(onBack = back) }
            composable(Screen.Bills.route) { BillsScreen(onBack = back) }
            composable(Screen.Tasks.route) { TasksScreen(onBack = back) }
            composable(Screen.Calendar.route) { CalendarScreen(onBack = back) }
            composable(Screen.Notes.route) { NotesScreen(onBack = back) }
            composable(Screen.Alarms.route) { AlarmsScreen(onBack = back) }
            composable(Screen.Search.route) { SearchScreen(onBack = back) }
            composable(
                route = "${Screen.Focus.route}/{blockId}",
                arguments = listOf(navArgument("blockId") { type = NavType.LongType })
            ) { e ->
                val id = e.arguments?.getLong("blockId") ?: 0L
                FocusScreen(
                    blockId = id,
                    topic = focusTopic ?: "Focus",
                    onDone = back
                )
            }
            composable(
                route = "${Screen.AiAssistant.route}?insight={insight}",
                arguments = listOf(navArgument("insight") { type = NavType.StringType; defaultValue = "" })
            ) { e ->
                AiAssistantScreen(initialInsight = e.arguments?.getString("insight").orEmpty(), onBack = back)
            }
        }
    }
}

@Composable
private fun LedgerTabBar(selected: (Screen) -> Boolean, onSelect: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(L.Page).navigationBarsPadding()) {
        HorizontalDivider(color = L.Line)
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { screen ->
                val on = selected(screen)
                if (screen == Screen.VoiceRecord) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (on) L.Box else L.Gold)
                                .clickable { onSelect(screen) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                tint = if (on) L.Gold else L.BoxDeep,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(screen) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = screen.label,
                            tint = if (on) L.Box else L.InkMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            screen.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) L.Box else L.InkMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
