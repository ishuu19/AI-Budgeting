package com.ledgerai.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.presentation.navigation.AppNavigation
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.presentation.components.LCurrency
import com.ledgerai.app.presentation.navigation.LaunchRequest
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.widget.WidgetActions
import com.ledgerai.app.presentation.screens.auth.LoginScreen
import com.ledgerai.app.presentation.theme.LedgerAITheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_FOCUS_BLOCK_ID = "open_focus_block_id"
        const val EXTRA_FOCUS_TOPIC = "focus_topic"
    }

    @Inject
    lateinit var userSession: UserSession

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var authRepository: com.ledgerai.app.data.repository.AuthRepository

    override fun onStart() {
        super.onStart()
        // A sign-in older than 30 days ends here, so the sign-in screen shows without waiting for a sync.
        lifecycleScope.launch { authRepository.enforceSessionLimit() }
        // Opening the app uploads anything still waiting.
        com.ledgerai.app.worker.SyncWorker.syncNow(this)
    }

    private var request by mutableStateOf<LaunchRequest?>(null)
    private var requestCounter = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.ledgerai.app.data.security.AppLock.hardenWindow(this)
        requestNotificationPermission()
        // Pick up photos captured while offline or before an update.
        com.ledgerai.app.worker.CaptureWorker.kick(this)
        // A restored activity keeps its old intent; only a fresh launch is a request.
        if (savedInstanceState == null) request = parse(intent)

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            LedgerAITheme(
                darkTheme = when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                com.ledgerai.app.presentation.components.AppLockGate(this@MainActivity) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val userInfo by userSession.userInfo.collectAsStateWithLifecycle(
                        initialValue = com.ledgerai.app.data.preferences.UserInfo()
                    )
                    val symbol by userPreferences.currencySymbol.collectAsStateWithLifecycle(initialValue = LCurrency.symbol)
                    LaunchedEffect(symbol) { LCurrency.symbol = symbol }

                    if (userInfo.hasRemoteUser) {
                        AppNavigation(request = request)
                    } else {
                        LoginScreen(onSignedIn = { /* state update triggers recomposition */ })
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { request = it }
    }

    private fun parse(intent: Intent?): LaunchRequest? {
        if (intent == null) return null
        val id = ++requestCounter
        val planSeg = when (intent.getStringExtra(WidgetActions.EXTRA_LIFE_TAB)) {
            WidgetActions.LIFE_TAB_LOG -> PlanSeg.Log
            WidgetActions.LIFE_TAB_JOBS -> PlanSeg.Jobs
            WidgetActions.LIFE_TAB_PLAN -> PlanSeg.Calendar
            else -> when {
                intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_TASKS, false) -> PlanSeg.Tasks
                intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_CALENDAR, false) -> PlanSeg.Calendar
                else -> null
            }
        }
        val hasFocus = intent.hasExtra(EXTRA_OPEN_FOCUS_BLOCK_ID)
        val voiceSeed = intent.getStringExtra(WidgetActions.EXTRA_VOICE_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val eventId = intent.getLongExtra(WidgetActions.EXTRA_EVENT_ID, 0L)
        val planBlockId = intent.getLongExtra(WidgetActions.EXTRA_PLAN_BLOCK_ID, 0L)
        val req = LaunchRequest(
            id = id,
            voice = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_VOICE, false) || voiceSeed != null,
            voiceSeed = voiceSeed,
            notes = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_NOTES, false),
            plan = planSeg,
            spendGuide = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_TODAY, false),
            spendList = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_SPEND_LIST, false),
            moneyOverview = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_MONEY, false),
            search = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_SEARCH, false),
            openToday = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_DAY, false),
            eventId = eventId,
            planBlockId = planBlockId,
            addTransaction = intent.getBooleanExtra(WidgetActions.EXTRA_ADD_TRANSACTION, false),
            bills = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_BILLS, false),
            focusBlockId = intent.getLongExtra(EXTRA_OPEN_FOCUS_BLOCK_ID, 0L),
            focusTopic = intent.getStringExtra(EXTRA_FOCUS_TOPIC),
            hasFocus = hasFocus,
            notebook = intent.getBooleanExtra(WidgetActions.EXTRA_OPEN_NOTEBOOK, false),
        )
        val any = req.voice || req.notes || req.plan != null || req.spendGuide || req.spendList ||
            req.moneyOverview || req.search || req.openToday || req.eventId > 0L || req.planBlockId > 0L ||
            req.addTransaction || req.bills || req.hasFocus || req.notebook
        return if (any) req else null
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
