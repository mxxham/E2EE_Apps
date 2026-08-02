package com.securechat.features.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.database.entity.ConversationEntity
import com.securechat.core.model.Conversation
import com.securechat.core.network.ChatApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
    private val chatApiService: ChatApiService,
) : ViewModel() {

    /**
     * Exposes a stream of conversations from the local Room database.
     * Real-time message inserts update the DB and flow through here automatically.
     * Newly created conversations get written to Room immediately by
     * [NewChatViewModel]; this class handles the OTHER direction — pulling down
     * conversations that already exist on the server (e.g. from a previous
     * session, or created from a different device) that Room doesn't know about yet.
     */
    val conversations: StateFlow<List<Conversation>> = conversationDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    init {
        syncFromServer()
    }

    /** Pulls the user's conversations from Supabase and upserts them into Room. */
    fun syncFromServer() {
        val userId = chatApiService.currentUserId() ?: return

        viewModelScope.launch {
            chatApiService.fetchConversations(userId).onSuccess { rows ->
                val entities = rows.map { row ->
                    // Preserve any locally-known fields (last message preview, unread
                    // count, etc.) for conversations Room already has; only fill in
                    // fresh defaults for ones Room has never seen before.
                    val existing = conversationDao.getById(row.id)
                    existing?.copy(title = row.title, isGroup = row.isGroup)
                        ?: ConversationEntity(
                            id      = row.id,
                            title   = row.title,
                            isGroup = row.isGroup,
                        )
                }
                conversationDao.upsertAll(entities)

                // Clean up any local conversations that no longer exist server-side
                // (e.g. leftover duplicates from before findExistingConversation()
                // was added, or conversations deleted from another device).
                val serverIds = rows.map { it.id }.toSet()
                val localOnly = conversationDao.getAllOnce().filter { it.id !in serverIds }
                localOnly.forEach { conversationDao.deleteById(it.id) }
            }
        }
    }
}
