package com.ledgerai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.ledgerai.app.presentation.screens.auth.LoginScreen
import com.ledgerai.app.presentation.theme.LedgerAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userSession: UserSession

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LedgerAITheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val userInfo by userSession.userInfo.collectAsStateWithLifecycle(
                        initialValue = com.ledgerai.app.data.preferences.UserInfo()
                    )

                    if (userInfo.isLoggedIn) {
                        val openVoice = intent?.getBooleanExtra(
                            com.ledgerai.app.widget.VoiceTransactionWidget.EXTRA_OPEN_VOICE,
                            false
                        ) == true
                        AppNavigation(openVoice = openVoice)
                    } else {
                        LoginScreen(onSignedIn = { /* state update triggers recomposition */ })
                    }
                }
            }
        }
    }
}
