package com.ledgerai.app.presentation.screens.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            // Add server client ID if provided (enables id_token for backend verification)
            .apply {
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                }
            }
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    init {
        viewModelScope.launch {
            userSession.userInfo.collect { info ->
                _uiState.update { it.copy(isSignedIn = info.isLoggedIn) }
            }
        }
        // Also check if already signed in via Google
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            viewModelScope.launch { persistAccount(account) }
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(result: ActivityResult) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModelScope.launch { persistAccount(account) }
        } catch (e: ApiException) {
            _uiState.update { it.copy(
                isLoading = false,
                errorMessage = "Google Sign-In failed (${e.statusCode}). Please try again."
            ) }
        }
    }

    private suspend fun persistAccount(account: GoogleSignInAccount) {
        userSession.saveUser(
            userId   = account.id ?: "",
            name     = account.displayName ?: "",
            email    = account.email ?: "",
            photoUrl = account.photoUrl?.toString() ?: ""
        )
        _uiState.update { it.copy(isSignedIn = true, isLoading = false) }
    }

    fun signOut() {
        viewModelScope.launch {
            googleSignInClient.signOut()
            userSession.clearSession()
            _uiState.update { AuthUiState() }
        }
    }
}
