package com.securechat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.securechat.core.model.DeliveryStatus
import com.securechat.core.model.Message
import com.securechat.core.model.MessageType
import com.securechat.core.model.Reaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room entity persisting a decrypted message.
 * Ciphertext NEVER touches this layer — only plaintext after successful decryption.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id")   val conversationId: String,
    @ColumnInfo(name = "sender_id")         val senderId: String,
    @ColumnInfo(name = "body")              val body: String = "",
    @ColumnInfo(name = "type")              val type: String = MessageType.TEXT.name,
    @ColumnInfo(name = "media_url")         val mediaUrl: String? = null,
    @ColumnInfo(name = "local_media_path")  val localMediaPath: String? = null,
    @ColumnInfo(name = "media_thumbnail")   val mediaThumbnail: String? = null,
    @ColumnInfo(name = "reply_to_id")       val replyToMessageId: String? = null,
    @ColumnInfo(name = "reply_to_body")     val replyToBody: String? = null,
    /** JSON-encoded list of Reaction objects. */
    @ColumnInfo(name = "reactions_json")    val reactionsJson: String = "[]",
    @ColumnInfo(name = "delivery_status")   val deliveryStatus: String = DeliveryStatus.PENDING.name,
    @ColumnInfo(name = "timestamp")         val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_mine")           val isMine: Boolean = false,
) {
    fun toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        body = body,
        type = MessageType.valueOf(type),
        mediaUrl = mediaUrl,
        localMediaPath = localMediaPath,
        mediaThumbnail = mediaThumbnail,
        replyToMessageId = replyToMessageId,
        replyToBody = replyToBody,
        reactions = Json.decodeFromString<List<Reaction>>(reactionsJson),
        deliveryStatus = DeliveryStatus.valueOf(deliveryStatus),
        timestamp = timestamp,
        isMine = isMine,
    )
}

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    type = type.name,
    mediaUrl = mediaUrl,
    localMediaPath = localMediaPath,
    mediaThumbnail = mediaThumbnail,
    replyToMessageId = replyToMessageId,
    replyToBody = replyToBody,
    reactionsJson = Json.encodeToString(reactions),
    deliveryStatus = deliveryStatus.name,
    timestamp = timestamp,
    isMine = isMine,
)
