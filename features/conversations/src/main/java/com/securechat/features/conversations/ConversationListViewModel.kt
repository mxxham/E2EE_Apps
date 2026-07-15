package com.securechat.features.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationDao: ConversationDao,
) : ViewModel() {

    /**
     * Exposes a stream of conversations from the local Room database.
     * Real-time updates from Supabase will seamlessly update the DB and flow through here.
     */
    val conversations: StateFlow<List<Conversation>> = conversationDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    // TODO: Add methods to initiate a new conversation search (trigger Supabase search RPC)
}
