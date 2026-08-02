package com.securechat.features.chat

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.securechat.core.database.dao.ConversationDao
import com.securechat.core.database.dao.MessageDao
import com.securechat.core.database.entity.toEntity
import com.securechat.core.model.DeliveryStatus
import com.securechat.core.model.Message
import com.securechat.core.model.MessageType
import com.securechat.core.model.Reaction
import com.securechat.core.network.ChatApiService
import com.securechat.core.network.MessageRow
import com.securechat.core.network.RealtimeMessageClient
import com.securechat.core.network.UploadProgress
import com.securechat.core.security.EncryptedMessage
import com.securechat.core.security.SessionCipherManager
import com.securechat.core.security.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository coordinating:
 * - Incoming message routing (via [RealtimeMessageClient], Supabase Realtime) and decryption.
 * - Outgoing message encryption and send (via [ChatApiService], Supabase Postgrest).
 * - Encrypted media upload/download (via [ChatApiService], Supabase Storage).
 * - Room DB persistence of decrypted message content.
 *
 * ## Media encryption design
 * Media attachments reuse the SAME Double Ratchet session as text messages —
 * each attachment consumes exactly one ratchet step, just like a text message
 * would, so the wire schema (one `message_index` per row) stays unchanged.
 * The plaintext payload for a media message is:
 * `[4-byte length][JSON metadata: fileName + mimeType][raw file bytes]`,
 * all encrypted as a single blob. The ciphertext is uploaded to Supabase
 * Storage; the message row itself only ever stores the IV and a pointer
 * (`media_url`) to the encrypted blob — never plaintext, never the key
 * (the key is derived from the ratchet and never transmitted).
 *
 * KNOWN v1 LIMITATION: files are read fully into memory for encryption and
 * decryption — fine for photos and short clips, but very large videos should
 * eventually be streamed/chunked instead.
 */
@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatApiService: ChatApiService,
    private val realtimeMessageClient: RealtimeMessageClient,
    private val cipherManager: SessionCipherManager,
    private val json: Json,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Active Double Ratchet sessions keyed by conversationId. */
    private val sessions = mutableMapOf<String, SessionState>()

    /** Conversations we've already subscribed to on the Realtime channel. */
    private val subscribedConversations = mutableSetOf<String>()

    private companion object {
        /** Fixed filename for every uploaded ciphertext blob — keeps the storage path deterministic. */
        const val ENCRYPTED_BLOB_NAME = "media.enc"
    }

    init {
        scope.launch { collectIncomingMessages() }
    }

    // ── Observing ──────────────────────────────────────────────────────────

    fun observeMessages(conversationId: String): Flow<List<Message>> {
        subscribeToConversation(conversationId)
        return messageDao.observeMessages(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Subscribes to the Supabase Realtime channel for [conversationId] so incoming
     * messages start flowing through [collectIncomingMessages]. Safe to call repeatedly —
     * only subscribes once per conversation.
     */
    fun subscribeToConversation(conversationId: String) {
        if (!subscribedConversations.add(conversationId)) return
        realtimeMessageClient.subscribeToConversation(conversationId)
    }

    fun unsubscribeFromConversation(conversationId: String) {
        subscribedConversations.remove(conversationId)
        realtimeMessageClient.unsubscribeFromConversation(conversationId)
    }

    // ── Sending: text ──────────────────────────────────────────────────────

    /**
     * Encrypts and sends a text message, persisting it locally as PENDING immediately
     * for instant UI feedback, then inserting the ciphertext into Supabase.
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
        val timestamp = System.currentTimeMillis()

        val encryptedFrame = cipherManager.encrypt(session, body.toByteArray(Charsets.UTF_8))

        val message = Message(
            id                = messageId,
            conversationId    = conversationId,
            senderId          = senderId,
            body              = body,
            deliveryStatus    = DeliveryStatus.PENDING,
            timestamp         = timestamp,
            isMine            = true,
            replyToMessageId  = replyToMessageId,
            replyToBody       = replyToBody,
        )
        messageDao.upsert(message.toEntity())
        conversationDao.updateLastMessage(conversationId, body, timestamp, delta = 0)

        val row = MessageRow(
            id             = messageId,
            conversationId = conversationId,
            senderId       = senderId,
            encryptedBody  = Base64.encodeToString(encryptedFrame.cipherText, Base64.NO_WRAP),
            iv             = Base64.encodeToString(encryptedFrame.iv, Base64.NO_WRAP),
            messageIndex   = encryptedFrame.messageIndex,
            messageType    = "TEXT",
        )

        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "sendTextMessage() inserting row: conversationId=$conversationId, messageId=$messageId, senderId=$senderId")
        // ── END DEBUG ──────────────────────────────────────────────────────

        chatApiService.insertMessage(row)
            .onSuccess {
                android.util.Log.d("SCDebug", "sendTextMessage() insertMessage SUCCESS for $messageId")
                messageDao.updateDeliveryStatus(messageId, DeliveryStatus.SENT.name)
            }
            .onFailure { error ->
                android.util.Log.e("SCDebug", "sendTextMessage() insertMessage FAILED for $messageId: ${error::class.qualifiedName}: ${error.message}", error)
                messageDao.updateDeliveryStatus(messageId, DeliveryStatus.FAILED.name)
            }

        return message
    }

    // ── Sending: media (image / video / audio / generic file) ──────────────

    /**
     * Encrypts and sends a media attachment picked via [uri]. See class doc for
     * the encryption/wire-format design.
     */
    suspend fun sendMediaMessage(
        conversationId: String,
        senderId: String,
        uri: Uri,
        mimeType: String?,
        fileName: String?,
    ): Message = withContext(Dispatchers.IO) {
        val messageId = UUID.randomUUID().toString()
        val session   = getOrCreateSession(conversationId)
        val timestamp = System.currentTimeMillis()
        val type      = determineMessageType(mimeType)
        val resolvedFileName = fileName ?: "file"

        val plainBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read selected file")

        // Cache a local plaintext copy immediately so the sender sees an instant
        // preview without needing to re-download + decrypt their own attachment.
        val localFile = File(mediaCacheDir(), "${messageId}_$resolvedFileName")
        localFile.writeBytes(plainBytes)

        val metadata = MediaMetadata(fileName = resolvedFileName, mimeType = mimeType ?: "application/octet-stream")
        val payload = packMediaPayload(metadata, plainBytes)
        val encryptedFrame = cipherManager.encrypt(session, payload)

        val message = Message(
            id             = messageId,
            conversationId = conversationId,
            senderId       = senderId,
            type           = type,
            localMediaPath = localFile.absolutePath,
            mediaThumbnail = resolvedFileName, // repurposed to hold the display filename
            deliveryStatus = DeliveryStatus.PENDING,
            timestamp      = timestamp,
            isMine         = true,
        )
        messageDao.upsert(message.toEntity())
        conversationDao.updateLastMessage(conversationId, previewFor(type, resolvedFileName), timestamp, delta = 0)

        val uploadDir = File(context.cacheDir, "upload/$messageId").apply { mkdirs() }
        val encryptedTempFile = File(uploadDir, ENCRYPTED_BLOB_NAME).apply {
            writeBytes(encryptedFrame.cipherText)
        }

        val publicUrl = runCatching {
            chatApiService.uploadEncryptedMedia(conversationId, messageId, encryptedTempFile).lastOrNull()
        }.onFailure { error ->
            android.util.Log.e("SCDebug", "sendMediaMessage() uploadEncryptedMedia THREW: ${error::class.qualifiedName}: ${error.message}", error)
        }.getOrNull().let { it as? UploadProgress.Done }?.publicUrl

        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "sendMediaMessage() upload result publicUrl=$publicUrl")
        // ── END DEBUG ──────────────────────────────────────────────────────

        encryptedTempFile.delete()
        uploadDir.delete()

        if (publicUrl == null) {
            messageDao.updateDeliveryStatus(messageId, DeliveryStatus.FAILED.name)
            return@withContext message
        }

        val row = MessageRow(
            id             = messageId,
            conversationId = conversationId,
            senderId       = senderId,
            // No plaintext body for media — encryptedBody duplicates the IV to satisfy
            // the NOT NULL column without a schema change.
            encryptedBody  = Base64.encodeToString(encryptedFrame.iv, Base64.NO_WRAP),
            iv             = Base64.encodeToString(encryptedFrame.iv, Base64.NO_WRAP),
            messageIndex   = encryptedFrame.messageIndex,
            messageType    = type.name,
            mediaUrl       = publicUrl,
        )

        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "sendMediaMessage() inserting row: conversationId=$conversationId, messageId=$messageId, type=${type.name}, mediaUrl=$publicUrl")
        // ── END DEBUG ──────────────────────────────────────────────────────

        chatApiService.insertMessage(row)
            .onSuccess {
                android.util.Log.d("SCDebug", "sendMediaMessage() insertMessage SUCCESS for $messageId")
                messageDao.updateDeliveryStatus(messageId, DeliveryStatus.SENT.name)
            }
            .onFailure { error ->
                android.util.Log.e("SCDebug", "sendMediaMessage() insertMessage FAILED for $messageId: ${error::class.qualifiedName}: ${error.message}", error)
                messageDao.updateDeliveryStatus(messageId, DeliveryStatus.FAILED.name)
            }

        message
    }

    private fun determineMessageType(mimeType: String?): MessageType = when {
        mimeType == null              -> MessageType.FILE
        mimeType.startsWith("image/") -> MessageType.IMAGE
        mimeType.startsWith("video/") -> MessageType.VIDEO
        mimeType.startsWith("audio/") -> MessageType.AUDIO
        else                          -> MessageType.FILE
    }

    private fun previewFor(type: MessageType, fileName: String?): String = when (type) {
        MessageType.IMAGE -> "\uD83D\uDCF7 Photo"
        MessageType.VIDEO -> "\uD83C\uDFA5 Video"
        MessageType.AUDIO -> "\uD83C\uDFB5 Audio"
        else               -> "\uD83D\uDCCE ${fileName ?: "File"}"
    }

    private fun mediaCacheDir(): File = File(context.cacheDir, "media").apply { mkdirs() }

    // ── Media payload packing (metadata header + raw bytes, one ratchet step) ──

    @Serializable
    private data class MediaMetadata(val fileName: String, val mimeType: String)

    private fun packMediaPayload(metadata: MediaMetadata, fileBytes: ByteArray): ByteArray {
        val metaBytes = json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
        val lengthPrefix = ByteBuffer.allocate(4).putInt(metaBytes.size).array()
        return lengthPrefix + metaBytes + fileBytes
    }

    private fun unpackMediaPayload(payload: ByteArray): Pair<MediaMetadata, ByteArray> {
        val length = ByteBuffer.wrap(payload, 0, 4).int
        val metaBytes = payload.copyOfRange(4, 4 + length)
        val fileBytes = payload.copyOfRange(4 + length, payload.size)
        val metadata = json.decodeFromString<MediaMetadata>(String(metaBytes, Charsets.UTF_8))
        return metadata to fileBytes
    }

    // ── Reactions ─────────────────────────────────────────────────────────
    // TODO: Reactions are currently local-only. Add a `message_reactions` Supabase table
    // + Realtime subscription to sync reactions to peers.

    suspend fun toggleReaction(messageId: String, conversationId: String, senderId: String, emoji: String) {
        val entity = messageDao.getById(messageId) ?: return
        val currentReactions = json.decodeFromString<List<Reaction>>(entity.reactionsJson).toMutableList()

        val existing = currentReactions.indexOfFirst { it.senderId == senderId && it.emoji == emoji }
        if (existing >= 0) {
            currentReactions.removeAt(existing)
        } else {
            currentReactions.add(Reaction(emoji = emoji, senderId = senderId))
        }

        messageDao.updateReactions(messageId, json.encodeToString(currentReactions))
    }

    // ── Read Receipts ──────────────────────────────────────────────────────
    // TODO: Read receipts are currently local-only (clears our own unread badge).
    // Add a mechanism (e.g. a `read_at` column + Realtime broadcast) to notify the peer.

    suspend fun markConversationRead(conversationId: String) {
        conversationDao.clearUnread(conversationId)
    }

    // ── Incoming Message Routing (Supabase Realtime) ────────────────────────

    private suspend fun collectIncomingMessages() {
        realtimeMessageClient.incomingMessages.collect { row ->
            runCatching { handleIncomingMessage(row) }
        }
    }

    private suspend fun handleIncomingMessage(row: MessageRow) {
        val session   = getOrCreateSession(row.conversationId)
        val type      = runCatching { MessageType.valueOf(row.messageType) }.getOrDefault(MessageType.TEXT)
        val timestamp = System.currentTimeMillis()

        if (type == MessageType.TEXT) {
            val cipherBytes = Base64.decode(row.encryptedBody, Base64.NO_WRAP)
            val ivBytes      = Base64.decode(row.iv, Base64.NO_WRAP)
            val frame        = EncryptedMessage(row.messageIndex, ivBytes, cipherBytes)
            val plainBytes   = cipherManager.decrypt(session, frame)
            val plainText    = String(plainBytes, Charsets.UTF_8)

            val message = Message(
                id             = row.id,
                conversationId = row.conversationId,
                senderId       = row.senderId,
                body           = plainText,
                type           = MessageType.TEXT,
                deliveryStatus = DeliveryStatus.DELIVERED,
                timestamp      = timestamp,
                isMine         = false,
            )
            messageDao.upsert(message.toEntity())
            conversationDao.updateLastMessage(row.conversationId, plainText, timestamp, delta = 1)
            return
        }

        // Media message: download the encrypted blob from Storage, then decrypt.
        val ivBytes      = Base64.decode(row.iv, Base64.NO_WRAP)
        val storagePath  = "${row.conversationId}/${row.id}/$ENCRYPTED_BLOB_NAME"

        val cipherBytes = chatApiService.downloadEncryptedMedia(storagePath).getOrNull()
        if (cipherBytes == null) {
            // Couldn't download right now (e.g. offline). Store what we know —
            // the UI can offer a manual retry later using mediaUrl.
            val message = Message(
                id             = row.id,
                conversationId = row.conversationId,
                senderId       = row.senderId,
                type           = type,
                mediaUrl       = row.mediaUrl,
                deliveryStatus = DeliveryStatus.DELIVERED,
                timestamp      = timestamp,
                isMine         = false,
            )
            messageDao.upsert(message.toEntity())
            conversationDao.updateLastMessage(row.conversationId, previewFor(type, null), timestamp, delta = 1)
            return
        }

        val frame = EncryptedMessage(row.messageIndex, ivBytes, cipherBytes)
        val (metadata, fileBytes) = unpackMediaPayload(cipherManager.decrypt(session, frame))

        val localFile = File(mediaCacheDir(), "${row.id}_${metadata.fileName}")
        localFile.writeBytes(fileBytes)

        val message = Message(
            id             = row.id,
            conversationId = row.conversationId,
            senderId       = row.senderId,
            type           = type,
            mediaUrl       = row.mediaUrl,
            localMediaPath = localFile.absolutePath,
            mediaThumbnail = metadata.fileName,
            deliveryStatus = DeliveryStatus.DELIVERED,
            timestamp      = timestamp,
            isMine         = false,
        )
        messageDao.upsert(message.toEntity())
        conversationDao.updateLastMessage(row.conversationId, previewFor(type, metadata.fileName), timestamp, delta = 1)
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
}
