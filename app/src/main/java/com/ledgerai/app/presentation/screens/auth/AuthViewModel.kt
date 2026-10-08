package com.ledgerai.app.presentation.screens.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val errorMessage: String? = null,
    /** True when Supabase URL/anon + Google web client ID are present. */
    val googleSignInAvailable: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userSession: UserSession,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(googleSignInAvailable = canUseGoogleSignIn())
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userSession.userInfo.collect { info ->
                _uiState.update {
                    it.copy(
                        isSignedIn = info.hasRemoteUser,
                        userId = info.userId,
                        email = info.email,
                        displayName = info.displayName,
                        googleSignInAvailable = canUseGoogleSignIn()
                    )
                }
            }
        }
    }

    private fun canUseGoogleSignIn(): Boolean =
        authRepository.isSupabaseConfigured &&
            BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Credential Manager Google ID token → Supabase [AuthRepository.signInWithIdToken].
     */
    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            if (!authRepository.isSupabaseConfigured) {
                _uiState.update {
                    it.copy(errorMessage = "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY.")
                }
                return@launch
            }
            val webClientId = BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID.trim()
            if (webClientId.isEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = "Google Sign-In not configured. Set SUPABASE_GOOGLE_WEB_CLIENT_ID in secrets.properties.")
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = activity,
                    request = request
                )
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    authRepository.signInWithIdToken(googleCred.idToken)
                    _uiState.update { it.copy(isSignedIn = true, isLoading = false) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unexpected credential type from Google Sign-In")
                    }
                }
            } catch (_: GetCredentialCancellationException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            } catch (_: NoCredentialException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No Google account available on this device"
                    )
                }
            } catch (e: GetCredentialException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Google Sign-In failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Sign-in failed"
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update {
                AuthUiState(googleSignInAvailable = canUseGoogleSignIn())
            }
        }
    }
}
