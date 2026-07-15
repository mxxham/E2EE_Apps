package com.securechat.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a conversation (1-to-1 or group).
 */
@Serializable
data class Conversation(
    val id: String,
    /** Display name derived from the peer username or group name. */
    val title: String,
    /** URL of the peer/group avatar. */
    val avatarUrl: String? = null,
    /** Most recent decrypted message body shown in the list preview. */
    val lastMessageBody: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    /** Peer's presence status for 1-to-1 chats. */
    val presenceStatus: PresenceStatus = PresenceStatus.OFFLINE,
    val isGroup: Boolean = false,
    /** IDs of participants for group chats. */
    val participantIds: List<String> = emptyList(),
    /** True while the peer is composing a message. */
    val peerIsTyping: Boolean = false,
)

enum class PresenceStatus { ONLINE, AWAY, OFFLINE }
