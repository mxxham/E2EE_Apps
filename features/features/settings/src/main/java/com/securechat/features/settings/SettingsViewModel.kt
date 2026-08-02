package com.securechat.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.network.ChatApiService
import com.securechat.core.network.UserRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserRow? = null,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val chatApiService: ChatApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val userId = chatApiService.currentUserId()
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, loggedOut = true) }
            return
        }
        viewModelScope.launch {
            chatApiService.getProfile(userId).fold(
                onSuccess = { profile -> _uiState.update { it.copy(profile = profile, isLoading = false) } },
                onFailure = { error -> _uiState.update { it.copy(isLoading = false, errorMessage = error.message) } },
            )
        }
    }

    fun logout() {
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            chatApiService.logout()
            _uiState.update { it.copy(isLoggingOut = false, loggedOut = true) }
        }
    }
}
