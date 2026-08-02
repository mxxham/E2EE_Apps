package com.securechat.features.presence

import com.securechat.core.network.RealtimeMessageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * PresencePayload is the schema tracked via Supabase Realtime Presence to communicate
 * online/offline and typing-indicator state updates for a conversation.
 */
@Serializable
data class PresencePayload(
    val type: String,
    val status: String? = null,
    val isTyping: Boolean? = null,
    val targetId: String? = null,
)

/**
 * PresenceRepository handles sending heartbeat signals, tracking connection
 * state and presence updates (Online/Offline/Last Seen), and throttling typing events
 * for a single conversation's Supabase Realtime channel.
 *
 * Replaces the old custom-WebSocket-backed implementation — presence now rides on
 * Supabase Realtime's built-in Presence feature via [RealtimeMessageClient.updatePresence].
 *
 * IMPORTANT: The caller must have already subscribed to [conversationId] via
 * [RealtimeMessageClient.subscribeToConversation] before presence events sent here
 * will actually broadcast — [RealtimeMessageClient.updatePresence] is a no-op on
 * channels that aren't active yet.
 */
class PresenceRepository(
    private val realtimeMessageClient: RealtimeMessageClient,
    private val conversationId: String,
    private val externalScope: CoroutineScope,
) {
    private var heartbeatJob: Job? = null
    private var currentUserId: String? = null
    private val typingStateFlow = MutableStateFlow(false)

    init {
        // Collect local typing updates, throttle/debounce to avoid flooding the realtime channel.
        @OptIn(FlowPreview::class)
        externalScope.launch {
            typingStateFlow
                .debounce(300) // Emit only if idle for 300ms
                .distinctUntilChanged()
                .collect { isTyping ->
                    currentUserId?.let { userId ->
                        sendPresenceEvent(userId, PresencePayload(type = "TYPING", isTyping = isTyping))
                    }
                }
        }
    }

    /**
     * Spawns a scheduled job emitting periodic heartbeats while active.
     */
    fun startHeartbeat(userId: String) {
        currentUserId = userId
        heartbeatJob?.cancel()
        heartbeatJob = externalScope.launch(Dispatchers.IO) {
            while (isActive) {
                sendPresenceEvent(userId, PresencePayload(type = "PRESENCE", status = "ONLINE"))
                delay(15000) // 15-second heartbeat intervals
            }
        }
    }

    /**
     * Cancels active heartbeat loop and announces offline state.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        val userId = currentUserId ?: return
        externalScope.launch(Dispatchers.IO) {
            sendPresenceEvent(userId, PresencePayload(type = "PRESENCE", status = "OFFLINE"))
        }
    }

    /**
     * Triggers typing flow updates.
     */
    fun updateTypingState(isTyping: Boolean) {
        typingStateFlow.value = isTyping
    }

    private suspend fun sendPresenceEvent(userId: String, payload: PresencePayload) {
        try {
            realtimeMessageClient.updatePresence(
                conversationId = conversationId,
                userId = userId,
                state = buildMap {
                    put("type", payload.type)
                    payload.status?.let { put("status", it) }
                    payload.isTyping?.let { put("isTyping", it.toString()) }
                    payload.targetId?.let { put("targetId", it) }
                },
            )
        } catch (e: Exception) {
            // Log or handle presence tracking errors gracefully.
        }
    }
}
