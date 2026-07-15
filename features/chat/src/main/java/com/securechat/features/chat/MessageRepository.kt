package com.securechat.features.chat

import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.database.dao.MessageDao
import com.securechat.core.database.entity.MessageEntity
import com.securechat.core.database.entity.toEntity
import com.securechat.core.model.DeliveryStatus
import com.securechat.core.model.Message
import com.securechat.core.model.MessageType
import com.securechat.core.model.Reaction
import com.securechat.core.network.WebSocketClient
import com.securechat.core.security.EncryptedMessage
import com.securechat.core.security.SessionCipherManager
import com.securechat.core.security.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wire format for a chat message sent over the WebSocket.
 * The [body] field contains the *Base64-encoded ciphertext* of the actual message.
 */
@Serializable
data class WireMessage(
    val type: String = "MESSAGE",
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    /** Base64-encoded ciphertext (AES-GCM via Double Ratchet). */
    val body: String,
    /** Base64-encoded IV. */
    val iv: String,
    /** Ratchet message index for out-of-order handling. */
    val messageIndex: Int,
    val timestamp: Long,
    val messageType: String = "TEXT",
)

@Serializable
data class WireAck(
    val type: String = "ACK",
    val messageId: String,
    val status: String,
    val timestamp: Long,
)

@Serializable
data class WireReaction(
    val type: String = "REACTION",
    val messageId: String,
    val conversationId: String,
    val emoji: String,
    val senderId: String,
)

/**
 * Central repository coordinating:
 * - Incoming WebSocket frame routing and decryption.
 * - Outgoing message encryption and send (with offline queuing).
 * - Room DB persistence of decrypted message content.
 * - Delivery status acknowledgment dispatching.
 */
@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val socketClient: WebSocketClient,
    private val cipherManager: SessionCipherManager,
    private val json: Json,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Active Double Ratchet sessions keyed by conversationId. */
    private val sessions = mutableMapOf<String, SessionState>()

    init {
        scope.launch { collectIncomingFrames() }
    }

    // ── Observing ──────────────────────────────────────────────────────────

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeMessages(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }

    // ── Sending ────────────────────────────────────────────────────────────

    /**
     * Encrypts and sends a text message, persisting it locally as PENDING.
     * The delivery status is updated asynchronously via incoming ACK frames.
     */
    suspend fun sendTextMessage(
        conversationId: String,
        senderId: String,
        recipientId: String,
        body: String,
        replyToMessageId: String? = null,
        replyToBody: String? = null,
    ): Message {
        val messageId = UUID.randomUUID().toString()
        val session   = getOrCreateSession(conversationId)

        // Encrypt via Double Ratchet.
        val encryptedFrame = cipherManager.encrypt(session, body.toByteArray(Charsets.UTF_8))

        val wire = WireMessage(
            messageId      = messageId,
            conversationId = conversationId,
            senderId       = senderId,
            recipientId    = recipientId,
            body           = android.util.Base64.encodeToString(encryptedFrame.cipherText, android.util.Base64.NO_WRAP),
            iv             = android.util.Base64.encodeToString(encryptedFrame.iv, android.util.Base64.NO_WRAP),
            messageIndex   = encryptedFrame.messageIndex,
            timestamp      = System.currentTimeMillis(),
        )

        // Persist locally in PENDING state.
        val message = Message(
            id                = messageId,
            conversationId    = conversationId,
            senderId          = senderId,
            body              = body,
            deliveryStatus    = DeliveryStatus.PENDING,
            timestamp         = wire.timestamp,
            isMine            = true,
            replyToMessageId  = replyToMessageId,
            replyToBody       = replyToBody,
        )
        messageDao.upsert(message.toEntity())
        conversationDao.updateLastMessage(conversationId, body, wire.timestamp, delta = 0)

        // Send over WebSocket (queued if offline).
        socketClient.sendMessage(json.encodeToString(wire))

        return message
    }

    // ── Reactions ─────────────────────────────────────────────────────────

    suspend fun toggleReaction(messageId: String, conversationId: String, senderId: String, emoji: String) {
        val entity = messageDao.getById(messageId) ?: return
        val currentReactions = json.decodeFromString<List<Reaction>>(entity.reactionsJson).toMutableList()

        val existing = currentReactions.indexOfFirst { it.senderId == senderId && it.emoji == emoji }
        if (existing >= 0) {
            currentReactions.removeAt(existing)
        } else {
            currentReactions.add(Reaction(emoji = emoji, senderId = senderId))
        }

        val newJson = json.encodeToString(currentReactions)
        messageDao.updateReactions(messageId, newJson)

        val wire = WireReaction(
            messageId      = messageId,
            conversationId = conversationId,
            emoji          = emoji,
            senderId       = senderId,
        )
        socketClient.sendMessage(json.encodeToString(wire))
    }

    // ── Read Receipts ──────────────────────────────────────────────────────

    suspend fun markConversationRead(conversationId: String, myId: String) {
        // Get all delivered messages not from me and mark them read.
        conversationDao.clearUnread(conversationId)
        // Emit READ ACK for each unread message (simplified: batch ACK).
        val ack = WireAck(
            messageId = conversationId,  // Server handles batch by conversationId
            status    = "READ",
            timestamp = System.currentTimeMillis(),
        )
        socketClient.sendMessage(json.encodeToString(ack))
    }

    // ── Incoming WebSocket Routing ─────────────────────────────────────────

    private suspend fun collectIncomingFrames() {
        socketClient.incomingMessages.collect { raw ->
            runCatching {
                // Peek at the type field to route the frame.
                val typeObj = json.parseToJsonElement(raw)
                val type    = typeObj.jsonObject["type"]?.toString()?.trim('"') ?: return@collect

                when (type) {
                    "MESSAGE"  -> handleIncomingMessage(json.decodeFromString<WireMessage>(raw))
                    "ACK"      -> handleAck(json.decodeFromString<WireAck>(raw))
                    "REACTION" -> handleReaction(json.decodeFromString<WireReaction>(raw))
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(wire: WireMessage) {
        val session     = getOrCreateSession(wire.conversationId)
        val cipherBytes = android.util.Base64.decode(wire.body, android.util.Base64.NO_WRAP)
        val ivBytes     = android.util.Base64.decode(wire.iv, android.util.Base64.NO_WRAP)
        val frame       = EncryptedMessage(wire.messageIndex, ivBytes, cipherBytes)
        val plainBytes  = cipherManager.decrypt(session, frame)
        val plainText   = String(plainBytes, Charsets.UTF_8)

        val message = Message(
            id             = wire.messageId,
            conversationId = wire.conversationId,
            senderId       = wire.senderId,
            body           = plainText,
            type           = runCatching { MessageType.valueOf(wire.messageType) }.getOrDefault(MessageType.TEXT),
            deliveryStatus = DeliveryStatus.DELIVERED,
            timestamp      = wire.timestamp,
            isMine         = false,
        )
        messageDao.upsert(message.toEntity())
        conversationDao.updateLastMessage(wire.conversationId, plainText, wire.timestamp, delta = 1)

        // Send DELIVERED ACK back to sender.
        val ack = WireAck(wire.messageId, "DELIVERED", System.currentTimeMillis())
        socketClient.sendMessage(json.encodeToString(ack))
    }

    private suspend fun handleAck(ack: WireAck) {
        messageDao.updateDeliveryStatus(ack.messageId, ack.status)
    }

    private suspend fun handleReaction(wire: WireReaction) {
        val entity = messageDao.getById(wire.messageId) ?: return
        val reactions = json.decodeFromString<List<Reaction>>(entity.reactionsJson).toMutableList()
        val idx = reactions.indexOfFirst { it.senderId == wire.senderId && it.emoji == wire.emoji }
        if (idx >= 0) reactions.removeAt(idx) else reactions.add(Reaction(wire.emoji, wire.senderId))
        messageDao.updateReactions(wire.messageId, json.encodeToString(reactions))
    }

    // ── Soft Delete ────────────────────────────────────────────────────────

    suspend fun deleteMessage(messageId: String) {
        messageDao.softDelete(messageId)
    }

    // ── Session management (simplified in-memory) ──────────────────────────

    private fun getOrCreateSession(conversationId: String): SessionState {
        return sessions.getOrPut(conversationId) {
            // In production: load from encrypted SharedPreferences / DataStore.
            // Here we derive a deterministic test session from the conversationId.
            val seed = conversationId.toByteArray().copyOf(32)
            cipherManager.initSession(seed)
        }
    }

    // ── Extension ─────────────────────────────────────────────────────────

    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = this as? kotlinx.serialization.json.JsonObject
            ?: throw IllegalStateException("Expected JsonObject")
}
