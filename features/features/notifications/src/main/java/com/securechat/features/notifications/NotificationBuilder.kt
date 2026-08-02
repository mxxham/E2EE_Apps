package com.securechat.features.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and displays Android notifications for decrypted messages.
 *
 * Uses [NotificationCompat.MessagingStyle] for a modern messaging UI, with:
 * - Inline reply action (user can reply without opening the app).
 * - Conversation grouping (one notification thread per conversation).
 * - Fallback "New encrypted message" for decryption failures.
 */
@Singleton
class NotificationBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_MESSAGES   = "channel_messages"
        const val CHANNEL_CALLS      = "channel_calls"
        const val KEY_REPLY_INPUT    = "key_reply_input"
        const val ACTION_REPLY       = "com.securechat.ACTION_REPLY"
        const val ACTION_MARK_READ   = "com.securechat.ACTION_MARK_READ"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description            = "Incoming encrypted messages"
                enableVibration(true)
                enableLights(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_MAX,
            ).apply {
                description = "Incoming secure voice calls"
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(messagesChannel, callsChannel))
        }
    }

    /**
     * Shows a rich messaging-style notification for a decrypted message.
     * Multiple messages from the same conversation are grouped in one thread.
     */
    fun showMessageNotification(
        senderName: String,
        senderId: String,
        conversationId: String,
        messageId: String,
        body: String,
    ) {
        val notificationId = conversationId.hashCode()

        val sender = Person.Builder()
            .setName(senderName)
            .setKey(senderId)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(body, System.currentTimeMillis(), sender)

        // ── Inline Reply Action ─────────────────────────────────────────────
        val remoteInput = RemoteInput.Builder(KEY_REPLY_INPUT)
            .setLabel("Reply")
            .build()

        val replyIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(ACTION_REPLY).apply {
                putExtra("conversation_id", conversationId)
                putExtra("sender_id", senderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // ── Mark As Read Action ─────────────────────────────────────────────
        val markReadIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            Intent(ACTION_MARK_READ).apply {
                putExtra("conversation_id", conversationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark Read",
            markReadIntent,
        ).build()

        // ── Open App Intent ─────────────────────────────────────────────────
        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { putExtra("conversation_id", conversationId) }

        val openPendingIntent = PendingIntent.getActivity(
            context, notificationId + 2, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(messagingStyle)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("securechat_messages")
            .setShortcutId(conversationId)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Shows a generic notification when decryption fails.
     * Never reveals that a decryption failure occurred — looks like a normal tap-to-view.
     */
    fun showFallbackNotification(senderName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText("New message — tap to view")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(senderName.hashCode(), notification)
    }
}
