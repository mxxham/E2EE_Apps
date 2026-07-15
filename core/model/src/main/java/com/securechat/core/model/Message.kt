package com.securechat.core.model

import kotlinx.serialization.Serializable

/**
 * Delivery stages for a message, mirroring the 3-stage protocol:
 * PENDING → SENT (server ack) → DELIVERED (device ack) → READ (user ack)
 */
enum class DeliveryStatus { PENDING, SENT, DELIVERED, READ }

/** Message type discriminator for the UI renderer. */
enum class MessageType { TEXT, IMAGE, VIDEO, AUDIO, FILE, DELETED }

/** A reaction (emoji + sender) attached to a message. */
@Serializable
data class Reaction(
    val emoji: String,
    val senderId: String,
)

/**
 * Pure domain model for a single chat message.
 * Never holds ciphertext — only decrypted content reaches this layer.
 */
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    /** Decrypted plaintext body. Empty for media messages. */
    val body: String = "",
    val type: MessageType = MessageType.TEXT,
    /** Remote URL of the ciphertext blob for media messages. */
    val mediaUrl: String? = null,
    /** Local path to the decrypted media file (ephemeral, cleared on restart). */
    val localMediaPath: String? = null,
    /** The thumbnail (base64 or local path) for image/video. */
    val mediaThumbnail: String? = null,
    /** If this message is a reply, the ID of the quoted message. */
    val replyToMessageId: String? = null,
    /** Snapshot of the quoted message body (so we don't re-fetch on delete). */
    val replyToBody: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    /** True if the current device is the sender. */
    val isMine: Boolean = false,
)
