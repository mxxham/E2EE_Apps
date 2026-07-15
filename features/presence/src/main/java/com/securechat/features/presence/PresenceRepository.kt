package com.securechat.features.presence

import com.securechat.core.network.WebSocketClient
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * PresencePayload is the schema exchanged via WebSockets to communicate state updates.
 */
@Serializable
data class PresencePayload(
    val type: String,
    val status: String? = null,
    val isTyping: Boolean? = null,
    val targetId: String? = null
)

/**
 * PresenceRepository handles sending heartbeat signals, keeping track of connection
 * state and presence updates (Online/Offline/Last Seen), and throttling typing events.
 */
class PresenceRepository(
    private val webSocketClient: WebSocketClient,
    private val externalScope: CoroutineScope
) {
    private var heartbeatJob: Job? = null
    private val typingStateFlow = MutableStateFlow(false)

    init {
        // Collect local typing updates, throttle/debounce to avoid flooding the websocket channel
        @OptIn(FlowPreview::class)
        externalScope.launch {
            typingStateFlow
                .debounce(300) // Emit only if idle for 300ms
                .distinctUntilChanged()
                .collect { isTyping ->
                    sendPresenceEvent(
                        PresencePayload(
                            type = "TYPING",
                            isTyping = isTyping
                        )
                    )
                }
        }
    }

    /**
     * Spawns a scheduled job emitting periodic heartbeats while active.
     */
    fun startHeartbeat(userId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = externalScope.launch(Dispatchers.IO) {
            while (isActive) {
                val heartbeat = PresencePayload(
                    type = "PRESENCE",
                    status = "ONLINE"
                )
                sendPresenceEvent(heartbeat)
                delay(15000) // 15-second heartbeat intervals
            }
        }
    }

    /**
     * Cancels active heartbeat loop and announces offline state.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        externalScope.launch(Dispatchers.IO) {
            sendPresenceEvent(
                PresencePayload(
                    type = "PRESENCE",
                    status = "OFFLINE"
                )
            )
        }
    }

    /**
     * Triggers typing flow updates.
     */
    fun updateTypingState(isTyping: Boolean) {
        typingStateFlow.value = isTyping
    }

    private suspend fun sendPresenceEvent(payload: PresencePayload) {
        try {
            val jsonString = Json.encodeToString(payload)
            webSocketClient.sendMessage(jsonString)
        } catch (e: Exception) {
            // Log or handle JSON parsing / socket errors gracefully
        }
    }
}
