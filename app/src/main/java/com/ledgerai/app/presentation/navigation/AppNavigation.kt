package com.ledgerai.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
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
import com.ledgerai.app.presentation.screens.analytics.AnalyticsScreen
import com.ledgerai.app.presentation.screens.focus.FocusScreen
import com.ledgerai.app.presentation.screens.money.MoneyTabScreen
import com.ledgerai.app.presentation.screens.money.SpendTodayScreen
import com.ledgerai.app.presentation.screens.plan.PlanTabScreen
import com.ledgerai.app.presentation.screens.search.SearchScreen
import com.ledgerai.app.presentation.screens.settings.SettingsScreen
import com.ledgerai.app.presentation.screens.today.TodayScreen
import com.ledgerai.app.presentation.screens.voice.VoiceTabScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Today        : Screen("today",        "Today",    Icons.Filled.Today)
    object Plan         : Screen("plan",         "Plan",     Icons.Filled.CalendarMonth)
    object Voice        : Screen("voice",        "Voice",    Icons.Filled.Mic)
    object Money        : Screen("money",        "Money",    Icons.Filled.AccountBalanceWallet)
    object You          : Screen("you",          "You",      Icons.Filled.Person)

    object Insights     : Screen("insights",     "Insights", Icons.Filled.Insights)
    object SpendGuide   : Screen("spend_guide",  "Safe today", Icons.AutoMirrored.Filled.TrendingUp)
    object AiAssistant  : Screen("ai_assistant", "Ask",      Icons.Filled.AutoAwesome)
    object Search       : Screen("search",       "Search",   Icons.Filled.Search)
    object Focus        : Screen("focus",        "Focus",    Icons.Filled.Timer)
}

private val tabs = listOf(Screen.Today, Screen.Plan, Screen.Voice, Screen.Money, Screen.You)

/** Which tab owns a route, so sub-screens keep their tab highlighted. */
private fun tabOf(route: String?): Screen? = when {
    route == null -> null
    route.startsWith(Screen.Today.route) -> Screen.Today
    route.startsWith(Screen.Plan.route) -> Screen.Plan
    route.startsWith(Screen.Voice.route) || route.startsWith(Screen.AiAssistant.route) -> Screen.Voice
    route.startsWith(Screen.Money.route) || route.startsWith(Screen.Insights.route) || route.startsWith(Screen.SpendGuide.route) -> Screen.Money
    route.startsWith(Screen.You.route) -> Screen.You
    route.startsWith(Screen.Focus.route) -> Screen.Plan
    route.startsWith(Screen.Search.route) -> Screen.Today
    else -> null
}

private fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

/** Always opens the tab's own page. Nested screens are not restored. */
private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = false }
    launchSingleTop = true
    restoreState = false
}

@Composable
fun AppNavigation(request: LaunchRequest? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = tabOf(entry?.destination?.route)

    var planSeg by rememberSaveable { mutableStateOf(PlanSeg.Calendar) }
    var voiceSeg by rememberSaveable { mutableStateOf(VoiceSeg.Speak) }
    var moneySeg by rememberSaveable { mutableStateOf(MoneySeg.Overview) }
    var handledRequest by rememberSaveable { mutableStateOf(0L) }
    var pendingOpen by remember { mutableStateOf<OpenItem?>(null) }
    var holdMic by remember { mutableStateOf(false) }
    var voiceSeed by remember { mutableStateOf<String?>(null) }
    var addSpend by remember { mutableStateOf(false) }

    fun focusRoute(id: Long, topic: String) = "${Screen.Focus.route}/$id?topic=${Uri.encode(topic)}"

    val links = remember(nav) {
        AppLinks(
            search = { nav.go(Screen.Search.route) },
            focus = { id, topic -> nav.go(focusRoute(id, topic)) },
            plan = { seg -> planSeg = seg; nav.tab(Screen.Plan.route) },
            money = { seg -> moneySeg = seg; nav.tab(Screen.Money.route) },
            voice = { seg -> voiceSeg = seg; nav.tab(Screen.Voice.route) },
            you = { nav.tab(Screen.You.route) },
            insights = { nav.go(Screen.Insights.route) },
            spendGuide = { nav.go(Screen.SpendGuide.route) },
            chat = { insight -> nav.go("${Screen.AiAssistant.route}?insight=${Uri.encode(insight)}") },
            open = { kind, id ->
                pendingOpen = OpenItem(kind, id, System.nanoTime())
                when (kind) {
                    OpenKind.Event -> { planSeg = PlanSeg.Calendar; nav.tab(Screen.Plan.route) }
                    OpenKind.Job -> { planSeg = PlanSeg.Jobs; nav.tab(Screen.Plan.route) }
                    OpenKind.Note -> { voiceSeg = VoiceSeg.Notes; nav.tab(Screen.Voice.route) }
                    OpenKind.Transaction -> { moneySeg = MoneySeg.Spend; nav.tab(Screen.Money.route) }
                    OpenKind.Budget, OpenKind.Goal -> { moneySeg = MoneySeg.Plan; nav.tab(Screen.Money.route) }
                    OpenKind.Bill, OpenKind.Debt -> { moneySeg = MoneySeg.Owed; nav.tab(Screen.Money.route) }
                }
            }
        )
    }

    LaunchedEffect(request?.id) {
        val r = request ?: return@LaunchedEffect
        if (r.id == handledRequest) return@LaunchedEffect
        handledRequest = r.id
        when {
            r.voice -> {
                voiceSeg = VoiceSeg.Speak
                voiceSeed = r.voiceSeed
                nav.tab(Screen.Voice.route)
            }
            r.notes -> { voiceSeg = VoiceSeg.Notes; nav.tab(Screen.Voice.route) }
            r.hasFocus -> {
                nav.tab(Screen.Plan.route)
                nav.go(focusRoute(r.focusBlockId, r.focusTopic ?: "Focus"))
            }
            r.addTransaction -> {
                moneySeg = MoneySeg.Spend
                addSpend = true
                nav.tab(Screen.Money.route)
            }
            r.spendGuide -> { moneySeg = MoneySeg.Overview; nav.tab(Screen.Money.route); nav.go(Screen.SpendGuide.route) }
            r.bills -> { moneySeg = MoneySeg.Owed; nav.tab(Screen.Money.route) }
            r.plan != null -> { planSeg = r.plan; nav.tab(Screen.Plan.route) }
        }
    }

    Scaffold(
        containerColor = L.Page,
        bottomBar = {
            LedgerTabBar(
                selected = { s -> currentTab == s },
                onSelect = { nav.tab(it.route) },
                onVoiceHoldStart = {
                    voiceSeg = VoiceSeg.Speak
                    nav.tab(Screen.Voice.route)
                    holdMic = true
                },
                onVoiceHoldEnd = { holdMic = false }
            )
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner)
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            fun openFor(vararg kinds: OpenKind): OpenItem? = pendingOpen?.takeIf { it.kind in kinds }
            val opened: () -> Unit = { pendingOpen = null }

            composable(Screen.Today.route) { TodayScreen(links) }
            composable(Screen.Plan.route) {
                PlanTabScreen(planSeg, { planSeg = it }, links, openFor(OpenKind.Event, OpenKind.Job), opened)
            }
            composable(Screen.Voice.route) {
                VoiceTabScreen(
                    voiceSeg,
                    { voiceSeg = it },
                    links,
                    openFor(OpenKind.Note),
                    opened,
                    holdMic,
                    seed = voiceSeed,
                    onSeedConsumed = { voiceSeed = null }
                )
            }
            composable(Screen.Money.route) {
                MoneyTabScreen(
                    moneySeg, { moneySeg = it }, links,
                    openFor(OpenKind.Transaction, OpenKind.Budget, OpenKind.Goal, OpenKind.Bill, OpenKind.Debt),
                    opened,
                    addSpend = addSpend,
                    onAddSpendConsumed = { addSpend = false }
                )
            }
            composable(Screen.You.route) { SettingsScreen(onOpenAi = { links.voice(VoiceSeg.Ask) }, onBack = null) }

            composable(Screen.Insights.route) { AnalyticsScreen(onBack = back) }
            composable(Screen.SpendGuide.route) { SpendTodayScreen(onBack = back) }
            composable(Screen.Search.route) { SearchScreen(onBack = back, links = links) }
            composable(
                route = "${Screen.Focus.route}/{blockId}?topic={topic}",
                arguments = listOf(
                    navArgument("blockId") { type = NavType.LongType },
                    navArgument("topic") { type = NavType.StringType; defaultValue = "Focus" }
                )
            ) { e ->
                FocusScreen(
                    blockId = e.arguments?.getLong("blockId") ?: 0L,
                    topic = e.arguments?.getString("topic").orEmpty().ifBlank { "Focus" },
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
private fun LedgerTabBar(
    selected: (Screen) -> Boolean,
    onSelect: (Screen) -> Unit,
    onVoiceHoldStart: () -> Unit,
    onVoiceHoldEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(L.Page).navigationBarsPadding()) {
        HorizontalDivider(color = L.Line)
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { screen ->
                val on = selected(screen)
                if (screen == Screen.Voice) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (on) L.Box else L.Gold)
                                .semantics { role = Role.Button; contentDescription = "Voice. Hold to record." }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitFirstDown()
                                            val tap = withTimeoutOrNull(200) { waitForUpOrCancellation() }
                                            if (tap != null) onSelect(screen)
                                            else {
                                                onVoiceHoldStart()
                                                waitForUpOrCancellation()
                                                onVoiceHoldEnd()
                                            }
                                        }
                                    }
                                },
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
                            .selectable(
                                selected = on,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(screen) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = null,
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
