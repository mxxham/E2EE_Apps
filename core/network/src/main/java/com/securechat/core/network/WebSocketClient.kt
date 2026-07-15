package com.securechat.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Manages Supabase Realtime channel subscriptions for live message delivery.
 *
 * Architecture:
 * - Each conversation gets its own Supabase Realtime channel subscribed to
 *   `postgres_changes` on the `messages` table filtered by `conversation_id`.
 * - Presence (online/typing) is managed via Supabase Realtime Presence state.
 *
 * This replaces the custom WebSocket engine — Supabase handles reconnection,
 * authentication token refresh, and channel multiplexing internally.
 */
class RealtimeMessageClient(
    private val supabase: SupabaseClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(RealtimeState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeState> = _connectionState.asStateFlow()

    /** Broadcasts every new incoming [MessageRow] from the realtime subscription. */
    private val _incomingMessages = MutableSharedFlow<MessageRow>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val incomingMessages: SharedFlow<MessageRow> = _incomingMessages.asSharedFlow()

    /** Active channels keyed by conversationId. */
    private val activeChannels = mutableMapOf<String, RealtimeChannel>()
    private val channelJobs    = mutableMapOf<String, Job>()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Connects to the Supabase Realtime WebSocket.
     * Must be called once (e.g., in Application or after login).
     */
    suspend fun connect() {
        supabase.realtime.connect()
        _connectionState.value = RealtimeState.CONNECTED
    }

    suspend fun disconnect() {
        activeChannels.values.forEach { it.unsubscribe() }
        activeChannels.clear()
        channelJobs.values.forEach { it.cancel() }
        channelJobs.clear()
        supabase.realtime.disconnect()
        _connectionState.value = RealtimeState.DISCONNECTED
    }

    // ── Channel Management ────────────────────────────────────────────────────

    /**
     * Subscribes to new messages in [conversationId].
     * Safe to call multiple times — returns early if already subscribed.
     */
    fun subscribeToConversation(conversationId: String) {
        if (activeChannels.containsKey(conversationId)) return

        val channel = supabase.realtime.channel("conversation:$conversationId")
        activeChannels[conversationId] = channel

        val job = scope.launch {
            // Listen for INSERT events on the messages table filtered by this conversation.
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table  = "messages"
                filter = "conversation_id=eq.$conversationId"
            }.collect { action ->
                runCatching {
                    val row = json.decodeFromJsonElement<MessageRow>(action.record)
                    _incomingMessages.emit(row)
                }
            }
        }
        channelJobs[conversationId] = job

        scope.launch {
            channel.subscribe()
        }
    }

    /** Unsubscribes from a conversation channel when the user navigates away. */
    fun unsubscribeFromConversation(conversationId: String) {
        scope.launch {
            activeChannels.remove(conversationId)?.unsubscribe()
            channelJobs.remove(conversationId)?.cancel()
        }
    }

    // ── Presence ──────────────────────────────────────────────────────────────

    /**
     * Tracks the current user's presence state in a conversation channel.
     * Use this to broadcast typing indicators and online status.
     */
    suspend fun updatePresence(
        conversationId: String,
        userId: String,
        state: Map<String, String>,
    ) {
        activeChannels[conversationId]?.track(
            JsonObject(state.mapValues { (_, value) -> JsonPrimitive(value) }),
        )
    }

    /** Returns a flow of all presence states in a conversation channel. */
    fun presenceFlow(conversationId: String): Flow<*>? {
        // Return the presence sync flow from the channel if subscribed.
        return activeChannels[conversationId]?.presenceChangeFlow()
    }
}

enum class RealtimeState { CONNECTED, DISCONNECTED }
