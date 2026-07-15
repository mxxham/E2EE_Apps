package com.securechat.features.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.INotificationLifecycleListener
import com.securechat.core.network.ChatApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneSignal push notification integration for SecureChat.
 *
 * OneSignal is initialized in the Application class by calling [initialize].
 *
 * ## Flow
 * 1. Server receives a new encrypted message (Supabase DB trigger or backend logic).
 * 2. Server calls the OneSignal REST API to send a **data-only** notification to the recipient's
 *    `onesignal_id` (stored in the `device_tokens` Supabase table).
 * 3. OneSignal delivers the push to the device.
 * 4. [OneSignalHandler.onNotificationWillDisplay] intercepts the notification BEFORE it appears.
 * 5. The handler suppresses the raw notification and enqueues [DecryptMessageWorker].
 * 6. The worker decrypts the payload and shows a rich local notification.
 *
 * ## Server-side OneSignal payload (send from your backend / Supabase Edge Function)
 * ```json
 * {
 *   "app_id": "<YOUR_ONESIGNAL_APP_ID>",
 *   "include_aliases": { "onesignal_id": ["<recipient_onesignal_id>"] },
 *   "target_channel": "push",
 *   "data": {
 *     "sender_name":       "Alice",
 *     "sender_id":         "<uuid>",
 *     "conversation_id":   "<uuid>",
 *     "message_id":        "<uuid>",
 *     "encrypted_body":    "<base64-ciphertext>",
 *     "iv":                "<base64-iv>",
 *     "message_index":     "42"
 *   },
 *   "content_available": true
 * }
 * ```
 *
 * **Important**: Do NOT include "headings" or "contents" in the server payload.
 * A content-available-only push prevents OneSignal from auto-displaying
 * the raw ciphertext to the user.
 */
@Singleton
class OneSignalHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseService: ChatApiService,
) : INotificationLifecycleListener, INotificationClickListener {

    companion object {
        // Replace with your OneSignal App ID from https://app.onesignal.com
        private const val ONESIGNAL_APP_ID = "<YOUR_ONESIGNAL_APP_ID>"

        /**
         * Call this once from [SecureChatApplication.onCreate].
         * Sets up OneSignal SDK with data-only notification interception.
         */
        fun initialize(context: Context) {
            // Enable verbose logs during development only.
            OneSignal.Debug.logLevel = LogLevel.VERBOSE

            OneSignal.initWithContext(context, ONESIGNAL_APP_ID)
        }
    }

    /**
     * Registers click/display listeners and uploads the OneSignal subscription ID
     * to Supabase so the server can target this device.
     *
     * Call after the user has logged in and [initialize] has been called.
     */
    suspend fun onUserLoggedIn(userId: String) {
        // Wait for OneSignal to assign a subscription ID.
        val oneSignalId = OneSignal.User.pushSubscription.id ?: return

        // Upload to Supabase `device_tokens` table.
        supabaseService.upsertOneSignalId(userId, oneSignalId)

        // Register notification intercept listeners.
        OneSignal.Notifications.addForegroundLifecycleListener(this)
        OneSignal.Notifications.addClickListener(this)
    }

    // ── INotificationWillDisplayListener ─────────────────────────────────────

    /**
     * Called by OneSignal BEFORE showing any notification.
     * We suppress auto-display and hand off to [DecryptMessageWorker].
     */
    override fun onWillDisplay(event: INotificationWillDisplayEvent) {
        // Suppress the raw notification — never show ciphertext to the user.
        event.preventDefault()

        val data = event.notification.additionalData ?: return

        val workInput = Data.Builder()
            .putString("sender_name",     data.optString("sender_name", "Unknown"))
            .putString("sender_id",       data.optString("sender_id"))
            .putString("conversation_id", data.optString("conversation_id"))
            .putString("message_id",      data.optString("message_id"))
            .putString("encrypted_body",  data.optString("encrypted_body"))
            .putString("iv",              data.optString("iv"))
            .putInt("message_index",      data.optString("message_index", "0").toIntOrNull() ?: 0)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DecryptMessageWorker>()
            .setInputData(workInput)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            // Unique name per message prevents duplicate decryption if the push is redelivered.
            "decrypt_${data.optString("message_id")}",
            ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    // ── INotificationClickListener ────────────────────────────────────────────

    /** Handles user tapping on a notification — deep-link into the conversation. */
    override fun onClick(event: INotificationClickEvent) {
        val data           = event.notification.additionalData ?: return
        val conversationId = data.optString("conversation_id") ?: return
        // TODO: Use NavController or DeepLink to navigate to ChatScreen(conversationId).
        // Example: deepLinkUri = "securechat://chat/$conversationId"
    }
}
