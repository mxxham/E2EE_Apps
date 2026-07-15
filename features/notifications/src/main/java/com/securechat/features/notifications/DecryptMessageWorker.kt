package com.securechat.features.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onesignal.notifications.INotificationClickEvent
import com.securechat.core.security.EncryptedMessage
import com.securechat.core.security.KeystoreManager
import com.securechat.core.security.SessionCipherManager
import com.securechat.core.security.SessionState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Base64

/**
 * WorkManager worker that decrypts an incoming push notification payload on a
 * background thread and then builds a rich notification via [NotificationBuilder].
 *
 * Why WorkManager instead of the OneSignal callback thread:
 * - Guaranteed execution even if the app is killed mid-decryption.
 * - Access to Keystore secrets (not blocked by Direct Boot until after unlock).
 * - Keeps the OneSignal callback fast and non-blocking.
 *
 * Input data keys (set by [OneSignalHandler.enqueueDecryption]):
 *   - "sender_name"       — plaintext display name of the sender
 *   - "sender_id"         — sender's user ID
 *   - "conversation_id"   — conversation this message belongs to
 *   - "message_id"        — unique message UUID
 *   - "encrypted_body"    — Base64-encoded AES-GCM ciphertext
 *   - "iv"                — Base64-encoded GCM IV
 *   - "message_index"     — Double Ratchet message index (int as string)
 */
@HiltWorker
class DecryptMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val keystoreManager: KeystoreManager,
    private val cipherManager: SessionCipherManager,
    private val notificationBuilder: NotificationBuilder,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val senderName     = inputData.getString("sender_name")     ?: return Result.failure()
        val senderId       = inputData.getString("sender_id")        ?: return Result.failure()
        val conversationId = inputData.getString("conversation_id")  ?: return Result.failure()
        val messageId      = inputData.getString("message_id")       ?: return Result.failure()
        val encBodyB64     = inputData.getString("encrypted_body")   ?: return Result.failure()
        val ivB64          = inputData.getString("iv")               ?: return Result.failure()
        val msgIndex       = inputData.getInt("message_index", 0)

        return runCatching {
            val cipherBytes  = Base64.decode(encBodyB64, Base64.NO_WRAP)
            val ivBytes      = Base64.decode(ivB64, Base64.NO_WRAP)
            val frame        = EncryptedMessage(msgIndex, ivBytes, cipherBytes)

            // Retrieve and unwrap the session key for this conversation.
            // In production: load the persisted SessionState from EncryptedDataStore.
            val sessionSeed  = conversationId.toByteArray().copyOf(32)
            val session      = cipherManager.initSession(sessionSeed)

            val plainBytes   = cipherManager.decrypt(session, frame)
            val plainText    = String(plainBytes, Charsets.UTF_8)

            notificationBuilder.showMessageNotification(
                senderName     = senderName,
                senderId       = senderId,
                conversationId = conversationId,
                messageId      = messageId,
                body           = plainText,
            )
            Result.success()
        }.getOrElse {
            // Security: on failure, show a generic non-revealing notification.
            notificationBuilder.showFallbackNotification(senderName)
            Result.failure()
        }
    }
}
