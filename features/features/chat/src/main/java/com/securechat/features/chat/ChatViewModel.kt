package com.securechat.features.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.model.Message
import com.securechat.core.network.ChatApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val chatApiService: ChatApiService,
) : ViewModel() {

    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    val conversationTitle: String = checkNotNull(savedStateHandle["conversationTitle"])

    private val myUserId: String
        get() = chatApiService.currentUserId() ?: "unknown"

    // TODO: still hardcoded — resolving the real peer requires a conversation_participants
    // lookup, not yet wired up. Doesn't block sending/receiving (which route by
    // conversationId), just affects anywhere peerUserId itself is referenced directly.
    private val peerUserId = "peer-user-id"

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
            messageRepository.markConversationRead(conversationId)
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

    /** Encrypts and sends a picked media attachment (image, video, audio, or generic file). */
    fun sendMedia(uri: Uri, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            messageRepository.sendMediaMessage(
                conversationId = conversationId,
                senderId = myUserId,
                uri = uri,
                mimeType = mimeType,
                fileName = fileName,
            )
        }
    }
}
