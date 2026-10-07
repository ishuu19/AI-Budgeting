package com.ledgerai.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.preferences.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Temporary local session until Phase 2 (Credential Manager → Supabase Auth).
 * Does not use play-services-auth or ship Google client secrets.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userSession.userInfo.collect { info ->
                _uiState.update { it.copy(isSignedIn = info.isLoggedIn) }
            }
        }
    }

    fun continueLocally() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            userSession.saveUser(
                userId = "local-${UUID.randomUUID()}",
                name = "Local user",
                email = "",
                photoUrl = ""
            )
            _uiState.update { it.copy(isSignedIn = true, isLoading = false) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            userSession.clearSession()
            _uiState.update { AuthUiState() }
        }
    }
}
