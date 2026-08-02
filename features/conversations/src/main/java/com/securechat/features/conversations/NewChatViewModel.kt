package com.securechat.features.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.database.entity.ConversationEntity
import com.securechat.core.network.ChatApiService
import com.securechat.core.network.UserRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewChatUiState(
    val query: String = "",
    val results: List<UserRow> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
    /** (conversationId, title) once a conversation has been created — screen navigates on this. */
    val createdConversation: Pair<String, String>? = null,
)

/**
 * Drives the "start new chat" flow: debounced username search via
 * [ChatApiService.searchUsers], then creates a 1:1 conversation via
 * [ChatApiService.createConversation] when a result is tapped.
 */
@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatApiService: ChatApiService,
    private val conversationDao: ConversationDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewChatUiState())
    val uiState: StateFlow<NewChatUiState> = _uiState

    private val queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query -> performSearch(query) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, errorMessage = null) }
        queryFlow.value = query
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }

        chatApiService.searchUsers(query).fold(
            onSuccess = { users ->
                val myId = chatApiService.currentUserId()
                _uiState.update {
                    it.copy(results = users.filter { u -> u.id != myId }, isSearching = false)
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(isSearching = false, errorMessage = error.message ?: "Search failed")
                }
            },
        )
    }

    fun startChatWith(user: UserRow) {
        val myId = chatApiService.currentUserId()
        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "startChatWith() myId=$myId, targetUser=${user.id}/${user.username}")
        // ── END DEBUG ──────────────────────────────────────────────────────
        if (myId == null) {
            _uiState.update { it.copy(errorMessage = "You must be logged in to start a chat") }
            return
        }

        _uiState.update { it.copy(isCreating = true, errorMessage = null) }

        viewModelScope.launch {
            // Check for an existing 1:1 conversation first to avoid duplicates.
            val existing = chatApiService.findExistingConversation(myId, user.id).getOrNull()
            if (existing != null) {
                // Use user.displayName (the person we're chatting WITH), not existing.title —
                // that stored column is a single shared string set by whoever created the
                // conversation, which could be either side and is wrong from our perspective.
                _uiState.update {
                    it.copy(isCreating = false, createdConversation = existing.id to user.displayName)
                }
                return@launch
            }

            chatApiService.createConversation(
                title = user.displayName,
                participantIds = listOf(myId, user.id),
                isGroup = false,
            ).fold(
                onSuccess = { conversation ->
                    // Write through to local Room immediately so the conversation
                    // shows up in the list right away, without waiting for a
                    // server sync. See ConversationListViewModel for the startup
                    // sync that pulls conversations created in other sessions.
                    // Uses user.displayName (not conversation.title) so this stays
                    // correct regardless of which side created the conversation.
                    viewModelScope.launch {
                        conversationDao.upsert(
                            ConversationEntity(
                                id                    = conversation.id,
                                title                 = user.displayName,
                                isGroup               = conversation.isGroup,
                                participantIdsJson    = "[\"$myId\",\"${user.id}\"]",
                            ),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            createdConversation = conversation.id to user.displayName,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isCreating = false, errorMessage = error.message ?: "Could not start chat")
                    }
                },
            )
        }
    }
}