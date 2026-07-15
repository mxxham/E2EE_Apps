package com.securechat.features.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    val conversationTitle: String = checkNotNull(savedStateHandle["conversationTitle"])

    /** Hardcoded for UI demo. In reality, retrieved from Auth system. */
    private val myUserId = "my-user-id"
    private val peerUserId = "peer-user-id" // In a real app, fetched from conversation participants

    val messages: StateFlow<List<Message>> = messageRepository.observeMessages(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    init {
        // Mark conversation as read when opening
        viewModelScope.launch {
            messageRepository.markConversationRead(conversationId, myUserId)
        }
    }

    fun onMessageInputChanged(input: String) {
        _messageInput.value = input
    }

    fun sendMessage() {
        val text = _messageInput.value.trim()
        if (text.isEmpty()) return

        _messageInput.value = ""

        viewModelScope.launch {
            messageRepository.sendTextMessage(
                conversationId = conversationId,
                senderId = myUserId,
                recipientId = peerUserId,
                body = text,
            )
        }
    }
}
