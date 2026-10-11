package com.ledgerai.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.household.HouseholdRepository
import com.ledgerai.app.data.inventory.InventoryRepository
import com.ledgerai.app.data.memory.MemoryRepository
import com.ledgerai.app.data.people.CommitmentRepository
import com.ledgerai.app.data.people.InteractionRepository
import com.ledgerai.app.data.people.PeopleRepository
import com.ledgerai.app.data.receipts.ReceiptRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.subscriptions.SubscriptionRepository
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.home.homeSnapshot
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import com.ledgerai.app.presentation.screens.home.HomeRoute
import com.ledgerai.app.presentation.screens.home.HomeScreen
import com.ledgerai.app.presentation.screens.household.HouseholdScreen
import com.ledgerai.app.presentation.screens.memory.PersonMemoryScreen
import com.ledgerai.app.presentation.screens.people.PeopleScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryViewModel
import com.ledgerai.app.presentation.screens.receipts.ReceiptReviewScreen
import com.ledgerai.app.presentation.screens.receipts.ReceiptReviewViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import com.ledgerai.app.presentation.screens.ai.AiAssistantScreen
import com.ledgerai.app.presentation.screens.analytics.AnalyticsScreen
import com.ledgerai.app.presentation.screens.focus.FocusScreen
import com.ledgerai.app.presentation.screens.money.MoneyTabScreen
import com.ledgerai.app.presentation.screens.money.SpendTodayScreen
import com.ledgerai.app.presentation.screens.plan.PlanTabScreen
import com.ledgerai.app.presentation.screens.search.SearchScreen
import com.ledgerai.app.presentation.screens.settings.SettingsScreen
import com.ledgerai.app.presentation.screens.subscriptions.SubscriptionsScreen
import com.ledgerai.app.presentation.screens.subscriptions.SubscriptionsViewModel
import com.ledgerai.app.presentation.screens.wardrobe.WardrobeScreen
import com.ledgerai.app.presentation.screens.today.TodayScreen
import com.ledgerai.app.presentation.screens.chat.ChatScreen
import com.ledgerai.app.presentation.screens.voice.VoiceRecorderScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Today        : Screen("today",        "Today",    Icons.Filled.Today)
    object Plan         : Screen("plan",         "Plan",     Icons.Filled.CalendarMonth)
    object Chat         : Screen("chat",         "Home",     Icons.Filled.AutoAwesome)
    /** The big centre button. It is not a page: it opens Home and starts listening. */
    object Mic          : Screen("mic",          "Speak",    Icons.Filled.Mic)
    object VoiceLog     : Screen("voice_log",    "Voice log", Icons.Filled.Mic)
    object Money        : Screen("money",        "Money",    Icons.Filled.AccountBalanceWallet)
    object Life         : Screen("life",         "Life",     Icons.Filled.Category)
    object Settings     : Screen("settings",     "Settings", Icons.Filled.Settings)

    object Insights     : Screen("insights",     "Insights", Icons.Filled.Insights)
    object SpendGuide   : Screen("spend_guide",  "Safe today", Icons.AutoMirrored.Filled.TrendingUp)
    object AiAssistant  : Screen("ai_assistant", "Ask",      Icons.Filled.AutoAwesome)
    object Search       : Screen("search",       "Search",   Icons.Filled.Search)
    object Focus        : Screen("focus",        "Focus",    Icons.Filled.Timer)
    object Receipt      : Screen("receipt/{id}", "Receipt",    Icons.AutoMirrored.Filled.ReceiptLong)
    object PersonMemory : Screen("person/{personId}", "Person", Icons.Filled.Person)
    object Subscriptions : Screen("subscriptions", "Subscriptions", Icons.Filled.Subscriptions)
}

private val tabs = listOf(Screen.Chat, Screen.Plan, Screen.Mic, Screen.Money, Screen.Life)

/** Which tab owns a route, so sub-screens keep their tab highlighted. */
private fun tabOf(route: String?): Screen? = when {
    route == null -> null
    route.startsWith(Screen.Plan.route) || route.startsWith(Screen.Focus.route) -> Screen.Plan
    route.startsWith(Screen.Money.route) || route.startsWith(Screen.Insights.route) ||
        route.startsWith(Screen.SpendGuide.route) || route.startsWith(Screen.Subscriptions.route) -> Screen.Money
    route.startsWith(Screen.Life.route) || route.startsWith("receipt/") || route.startsWith("person/") -> Screen.Life
    // Home owns the chat, the full day, search, settings and the other tools.
    else -> Screen.Chat
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

    var lifeSeg by rememberSaveable { mutableStateOf(LifeSeg.Pantry) }
    var planSeg by rememberSaveable { mutableStateOf(PlanSeg.Calendar) }
    var micRequest by remember { mutableStateOf(false) }
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
            voice = { seg ->
                when (seg) {
                    VoiceSeg.Capture, VoiceSeg.Speak -> nav.tab(Screen.Chat.route)
                    VoiceSeg.Notes -> { planSeg = PlanSeg.Notes; nav.tab(Screen.Plan.route) }
                    VoiceSeg.Ask -> nav.go("${Screen.AiAssistant.route}?insight=")
                }
            },
            today = { nav.go(Screen.Today.route) },
            voiceLog = { nav.go(Screen.VoiceLog.route) },
            listen = { micRequest = true; nav.tab(Screen.Chat.route) },
            you = { nav.go(Screen.Settings.route) },
            life = { seg -> lifeSeg = seg; nav.tab(Screen.Life.route) },
            addExpense = { moneySeg = MoneySeg.Spend; addSpend = true; nav.tab(Screen.Money.route) },
            subscriptions = { nav.go(Screen.Subscriptions.route) },
            speak = { text -> voiceSeed = text; nav.tab(Screen.Chat.route) },
            receipt = { id -> nav.go("receipt/$id") },
            person = { id -> nav.go("person/${Uri.encode(id)}") },
            insights = { nav.go(Screen.Insights.route) },
            spendGuide = { nav.go(Screen.SpendGuide.route) },
            chat = { insight -> nav.go("${Screen.AiAssistant.route}?insight=${Uri.encode(insight)}") },
            open = { kind, id ->
                pendingOpen = OpenItem(kind, id, System.nanoTime())
                when (kind) {
                    OpenKind.Event -> { planSeg = PlanSeg.Calendar; nav.tab(Screen.Plan.route) }
                    OpenKind.Job -> { planSeg = PlanSeg.Jobs; nav.tab(Screen.Plan.route) }
                    OpenKind.Note -> { planSeg = PlanSeg.Notes; nav.tab(Screen.Plan.route) }
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
                voiceSeed = r.voiceSeed
                if (r.voiceSeed == null) micRequest = true
                nav.tab(Screen.Chat.route)
            }
            r.notes -> { planSeg = PlanSeg.Notes; nav.tab(Screen.Plan.route) }
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
                onSelect = {
                    if (it == Screen.Mic) {
                        micRequest = true
                        nav.tab(Screen.Chat.route)
                    } else nav.tab(it.route)
                },
                onVoiceHoldStart = {
                    nav.tab(Screen.Chat.route)
                    holdMic = true
                },
                onVoiceHoldEnd = { holdMic = false }
            )
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner)
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            fun openFor(vararg kinds: OpenKind): OpenItem? = pendingOpen?.takeIf { it.kind in kinds }
            val opened: () -> Unit = { pendingOpen = null }

            composable(Screen.Chat.route) {
                ChatScreen(links, micRequest, { micRequest = false }, holdMic, voiceSeed, onSeedConsumed = { voiceSeed = null })
            }
            composable(Screen.Today.route) { TodayScreen(links) }
            composable(Screen.VoiceLog.route) { VoiceRecorderScreen(onNavigateBack = back, links = links) }
            composable(HomeRoute) {
                val appContext = LocalContext.current.applicationContext
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(
                        appContext,
                        TransactionEntryPoint::class.java,
                    ).transactionRepository()
                }
                val transactions by repository.getAllTransactions()
                    .collectAsState(initial = emptyList())
                HomeScreen(
                    snapshot = homeSnapshot(transactions, LocalDate.now()),
                    onReviewSpending = {
                        moneySeg = MoneySeg.Spend
                        nav.tab(Screen.Money.route)
                    },
                )
            }
            composable(Screen.Plan.route) {
                PlanTabScreen(planSeg, { planSeg = it }, links, openFor(OpenKind.Event, OpenKind.Job), opened)
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
            composable(Screen.Life.route) {
                LifeTab(lifeSeg, { lifeSeg = it }, onOpenPerson = { id -> nav.go("person/${Uri.encode(id)}") })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(links = links, onBack = back)
            }
            composable(
                route = Screen.Receipt.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val appContext = LocalContext.current.applicationContext
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(
                        appContext,
                        ReceiptEntryPoint::class.java,
                    ).receiptRepository()
                }
                var receipt by remember(id) { mutableStateOf<Receipt?>(null) }
                var resolved by remember(id) { mutableStateOf(false) }
                LaunchedEffect(id) {
                    receipt = repository.find(id)
                    resolved = true
                }
                val loaded = receipt
                if (resolved && loaded != null) {
                    val viewModel: ReceiptReviewViewModel = viewModel(key = "receipt-$id") {
                        ReceiptReviewViewModel(repository, loaded)
                    }
                    ReceiptReviewScreen(viewModel = viewModel, onBack = back)
                } else if (resolved) {
                    LScreen(title = "Receipt", onBack = back) {
                        item { LEmpty(Icons.AutoMirrored.Filled.ReceiptLong, "No receipt on this phone") }
                    }
                }
            }

            composable(
                route = Screen.PersonMemory.route,
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            ) { entry ->
                val personId = entry.arguments?.getString("personId").orEmpty()
                val auth: AuthViewModel = hiltViewModel()
                val user by auth.uiState.collectAsState()
                val appContext = LocalContext.current.applicationContext
                val gate = remember(appContext) {
                    EntryPointAccessors.fromApplication(
                        appContext,
                        PeopleEntryPoint::class.java,
                    )
                }
                val people = remember(gate) { gate.peopleRepository() }
                val memories = remember(gate) { gate.memoryRepository() }
                val interactions = remember(gate) { gate.interactionRepository() }
                val commitments = remember(gate) { gate.commitmentRepository() }
                PersonMemoryScreen(
                    userId = user.userId,
                    personId = personId,
                    people = people,
                    memories = memories,
                    interactions = interactions,
                    commitments = commitments,
                    onBack = back,
                )
            }

            composable(Screen.Subscriptions.route) {
                val auth: AuthViewModel = hiltViewModel()
                val user by auth.uiState.collectAsState()
                val appContext = LocalContext.current.applicationContext
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(
                        appContext,
                        SubscriptionEntryPoint::class.java,
                    ).subscriptionRepository()
                }
                val viewModel: SubscriptionsViewModel = viewModel(key = user.userId) {
                    SubscriptionsViewModel(repository, user.userId.ifBlank { null })
                }
                SubscriptionsScreen(viewModel = viewModel, onBack = back)
            }

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
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    // Floating bar: a rounded card on the page; the selected tab sits in a soft pill.
    Box(Modifier.fillMaxWidth().background(L.Page).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .background(L.Box)
                .border(1.dp, L.Line, shape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { screen ->
                val on = selected(screen)
                if (screen == Screen.Mic) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(L.Highlight)
                                .semantics { role = Role.Button; contentDescription = "Speak. Tap to talk, or hold." }
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
                                tint = androidx.compose.ui.graphics.Color(0xFF0B1B45),
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
                        Box(
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(if (on) L.BoxDeep else androidx.compose.ui.graphics.Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                screen.icon,
                                contentDescription = null,
                                tint = if (on) L.Primary else L.InkMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            screen.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) L.Primary else L.InkMuted
                        )
                    }
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TransactionEntryPoint {
    fun transactionRepository(): TransactionRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HouseholdEntryPoint {
    fun householdRepository(): HouseholdRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InventoryEntryPoint {
    fun inventoryRepository(): InventoryRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiptEntryPoint {
    fun receiptRepository(): ReceiptRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PeopleEntryPoint {
    fun peopleRepository(): PeopleRepository
    fun memoryRepository(): MemoryRepository
    fun interactionRepository(): InteractionRepository
    fun commitmentRepository(): CommitmentRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SubscriptionEntryPoint {
    fun subscriptionRepository(): SubscriptionRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WardrobeEntryPoint {
    fun wardrobeRepository(): WardrobeRepository
}
