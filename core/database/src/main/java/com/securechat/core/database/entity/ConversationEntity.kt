package com.securechat.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.securechat.core.model.Conversation
import com.securechat.core.model.PresenceStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "title")                  val title: String,
    @ColumnInfo(name = "avatar_url")             val avatarUrl: String? = null,
    @ColumnInfo(name = "last_message_body")      val lastMessageBody: String = "",
    @ColumnInfo(name = "last_message_timestamp") val lastMessageTimestamp: Long = 0L,
    @ColumnInfo(name = "unread_count")           val unreadCount: Int = 0,
    @ColumnInfo(name = "presence_status")        val presenceStatus: String = PresenceStatus.OFFLINE.name,
    @ColumnInfo(name = "is_group")               val isGroup: Boolean = false,
    /** JSON-encoded list of participant IDs. */
    @ColumnInfo(name = "participant_ids_json")   val participantIdsJson: String = "[]",
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        avatarUrl = avatarUrl,
        lastMessageBody = lastMessageBody,
        lastMessageTimestamp = lastMessageTimestamp,
        unreadCount = unreadCount,
        presenceStatus = PresenceStatus.valueOf(presenceStatus),
        isGroup = isGroup,
        participantIds = Json.decodeFromString<List<String>>(participantIdsJson),
    )
}

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    title = title,
    avatarUrl = avatarUrl,
    lastMessageBody = lastMessageBody,
    lastMessageTimestamp = lastMessageTimestamp,
    unreadCount = unreadCount,
    presenceStatus = presenceStatus.name,
    isGroup = isGroup,
    participantIdsJson = Json.encodeToString(participantIds),
)
