package com.securechat.features.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.network.ChatApiService
import com.securechat.core.network.PreKeyRow
import com.securechat.core.security.IdentityKeyStore
import com.securechat.core.security.X3DHKeyManager
import com.securechat.features.notifications.OneSignalHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val username: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Neutral (non-error) status message, e.g. "check your email to confirm". */
    val infoMessage: String? = null,
    val isAuthenticated: Boolean = false,
)

/**
 * Drives both the login and registration flows.
 *
 * Registration additionally:
 * 1. Generates a full X3DH key bundle locally ([X3DHKeyManager.generateRegistrationBundle]).
 * 2. Creates the Supabase Auth user + `public.users` profile row.
 * 3. Uploads the PUBLIC halves of the identity/signed/one-time keys to `public.prekeys`
 *    so peers can start encrypted sessions with this user via X3DH.
 * 4. Persists the WRAPPED PRIVATE halves locally via [IdentityKeyStore] — these never
 *    leave the device.
 *
 * IMPORTANT: If Supabase requires email confirmation, registering does NOT produce
 * an active session — [ChatApiService.hasActiveSession] will be false right after.
 * In that case we do NOT mark the user authenticated or navigate into the app (any
 * authenticated-only server call, like OneSignal token upsert or sending a message,
 * would fail RLS checks with no real session). Instead we show an info message and
 * drop back to the Login screen so the user can log in once they've confirmed.
 *
 * Both flows finish by calling [OneSignalHandler.onUserLoggedIn] so push notifications
 * are registered for the session — but only once a real session actually exists.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val chatApiService: ChatApiService,
    private val x3dhKeyManager: X3DHKeyManager,
    private val identityKeyStore: IdentityKeyStore,
    private val oneSignalHandler: OneSignalHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        // Skip straight past the auth screen if a Supabase session is already active
        // (e.g. app was killed and relaunched while still logged in).
        if (chatApiService.hasActiveSession()) {
            chatApiService.currentUserId()?.let { userId ->
                _uiState.update { it.copy(isAuthenticated = true) }
                viewModelScope.launch { runCatching { oneSignalHandler.onUserLoggedIn(userId) } }
            }
        }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
                errorMessage = null,
                infoMessage = null,
            )
        }
    }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null, infoMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null, infoMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, infoMessage = null) }

    fun onUsernameChange(value: String) =
        _uiState.update { it.copy(username = value, errorMessage = null, infoMessage = null) }

    fun onDisplayNameChange(value: String) =
        _uiState.update { it.copy(displayName = value, errorMessage = null, infoMessage = null) }

    fun submit() {
        val state = _uiState.value
        val validationError = validate(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

        viewModelScope.launch {
            val result = if (state.mode == AuthMode.REGISTER) register(state) else login(state)

            result.fold(
                onSuccess = { userId ->
                    // ── DEBUG ──────────────────────────────────────────────
                    android.util.Log.d("SCDebug", "AuthViewModel.submit() onSuccess userId=$userId, hasActiveSession=${chatApiService.hasActiveSession()}, currentUserId=${chatApiService.currentUserId()}")
                    // ── END DEBUG ────────────────────────────────────────── 
                    if (chatApiService.hasActiveSession()) {
                        runCatching { oneSignalHandler.onUserLoggedIn(userId) }
                        _uiState.update { it.copy(isLoading = false, isAuthenticated = true) }
                    } else {
                        // Account created, but email confirmation is pending — no
                        // session yet. Don't fake "authenticated"; send them to Login.
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mode = AuthMode.LOGIN,
                                password = "",
                                confirmPassword = "",
                                infoMessage = "Account created! Check your email to confirm, then log in.",
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Something went wrong")
                    }
                },
            )
        }
    }

    private suspend fun login(state: AuthUiState): Result<String> =
        chatApiService.login(state.email.trim(), state.password)

    private suspend fun register(state: AuthUiState): Result<String> {
        // 1. Generate the X3DH key bundle locally (identity + signed + 100 one-time keys).
        val bundle = x3dhKeyManager.generateRegistrationBundle()

        // 2. Create the Supabase Auth user + `public.users` profile row.
        val registerResult = chatApiService.register(
            email             = state.email.trim(),
            password          = state.password,
            username          = state.username.trim(),
            displayName       = state.displayName.trim(),
            identityPublicKey = bundle.identityPublicKey,
        )

        val userRow = registerResult.getOrElse { return Result.failure(it) }

        // 3. Upload public prekeys (identity + signed + one-time) so peers can start
        //    X3DH sessions with us. fetchPreKeyBundle() expects an "identity" row too.
        //    (This works even without a session — prekeys allow anon insert since
        //    they're meant to be publicly readable/writable at registration time.)
        val prekeyRows = buildList {
            add(
                PreKeyRow(
                    userId    = userRow.id,
                    keyId     = 0,
                    publicKey = bundle.identityPublicKey,
                    keyType   = "identity",
                ),
            )
            add(
                PreKeyRow(
                    userId    = userRow.id,
                    keyId     = 0,
                    publicKey = bundle.signedPrePublicKey,
                    keyType   = "signed",
                    signature = bundle.spkSignature,
                ),
            )
            bundle.oneTimePrePublicKeys.forEach { opk ->
                add(
                    PreKeyRow(
                        userId    = userRow.id,
                        keyId     = opk.id,
                        publicKey = opk.publicKey,
                        keyType   = "onetime",
                    ),
                )
            }
        }

        chatApiService.uploadPreKeys(prekeyRows).getOrElse { return Result.failure(it) }

        // 4. Persist the WRAPPED private key halves locally — never sent to the server.
        identityKeyStore.saveRegistrationBundle(userRow.id, bundle)

        return Result.success(userRow.id)
    }

    private fun validate(state: AuthUiState): String? = when {
        state.email.isBlank() -> "Email is required"
        !Patterns.EMAIL_ADDRESS.matcher(state.email).matches() -> "Enter a valid email"
        state.password.length < 8 -> "Password must be at least 8 characters"
        state.mode == AuthMode.REGISTER && state.username.isBlank() -> "Username is required"
        state.mode == AuthMode.REGISTER && state.displayName.isBlank() -> "Display name is required"
        state.mode == AuthMode.REGISTER && state.password != state.confirmPassword -> "Passwords do not match"
        else -> null
    }
}
