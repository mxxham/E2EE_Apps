package com.securechat.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Wraps all Supabase backend operations for the SecureChat application.
 *
 * Supabase database schema (run in Supabase SQL Editor):
 * ```sql
 * -- Enable RLS on all tables (add policies per your auth requirements)
 *
 * create table public.users (
 *   id                 uuid primary key references auth.users(id),
 *   username           text unique not null,
 *   display_name       text not null,
 *   avatar_url         text,
 *   identity_public_key text not null,
 *   created_at         timestamptz default now()
 * );
 *
 * create table public.conversations (
 *   id          uuid primary key default gen_random_uuid(),
 *   title       text not null,
 *   is_group    boolean default false,
 *   created_at  timestamptz default now()
 * );
 *
 * create table public.conversation_participants (
 *   conversation_id uuid references public.conversations(id) on delete cascade,
 *   user_id         uuid references public.users(id) on delete cascade,
 *   primary key (conversation_id, user_id)
 * );
 *
 * create table public.messages (
 *   id              uuid primary key default gen_random_uuid(),
 *   conversation_id uuid references public.conversations(id) on delete cascade,
 *   sender_id       uuid references public.users(id),
 *   -- Ciphertext fields (server NEVER sees plaintext)
 *   encrypted_body  text not null,
 *   iv              text not null,
 *   message_index   int  not null default 0,
 *   message_type    text not null default 'TEXT',
 *   -- Media (stored encrypted in Supabase Storage)
 *   media_url       text,
 *   timestamp       timestamptz default now()
 * );
 *
 * create table public.prekeys (
 *   user_id     uuid references public.users(id) on delete cascade,
 *   key_id      int  not null,
 *   public_key  text not null,
 *   key_type    text not null, -- 'identity' | 'signed' | 'onetime'
 *   signature   text,
 *   consumed    boolean default false,
 *   primary key (user_id, key_id, key_type)
 * );
 *
 * create table public.device_tokens (
 *   user_id       uuid references public.users(id) on delete cascade,
 *   onesignal_id  text not null,
 *   updated_at    timestamptz default now(),
 *   primary key (user_id)
 * );
 * ```
 */
class ChatApiService(private val supabase: SupabaseClient) {

    companion object {
        /** Supabase Storage bucket for encrypted media attachments. */
        const val MEDIA_BUCKET = "encrypted-media"
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Signs up a new user with Supabase Auth (email/password). The `public.users`
     * profile row is created automatically server-side by a database trigger
     * (see `handle_new_user()` in the SQL schema) reading the metadata passed here.
     *
     * NOTE: `signUpWith()` returns null when email confirmation is pending (no
     * session yet) — that's expected SDK behavior, not a failure. Either way, the
     * trigger has already run synchronously as part of the same transaction by the
     * time this call returns, so we look the row up by username instead of relying
     * on the return value.
     */
    suspend fun register(
        email: String,
        password: String,
        username: String,
        displayName: String,
        identityPublicKey: String,
    ): Result<UserRow> = runCatching {
        supabase.auth.signUpWith(Email) {
            this.email    = email
            this.password = password
            data = buildJsonObject {
                put("username", username)
                put("display_name", displayName)
                put("identity_public_key", identityPublicKey)
            }
        }

        supabase.postgrest["users"]
            .select { filter { eq("username", username) } }
            .decodeSingle<UserRow>()
    }

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
        val id = supabase.auth.currentUserOrNull()?.id ?: error("Login succeeded but no user ID")
        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "login() succeeded, userId=$id, hasActiveSession=${supabase.auth.currentSessionOrNull() != null}, supabase client identity hash = ${System.identityHashCode(supabase)}")
        // ── END DEBUG ──────────────────────────────────────────────────────
        id
    }

    suspend fun logout(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    /**
     * True only if Supabase has issued a real session (JWT access token) for this
     * device — false right after registering if email confirmation is still pending,
     * even though the account and profile row already exist.
     */
    fun hasActiveSession(): Boolean = supabase.auth.currentSessionOrNull() != null

    // ── PreKeys ───────────────────────────────────────────────────────────────

    /** Batch-uploads the signed PreKey + one-time PreKeys after registration. */
    suspend fun uploadPreKeys(rows: List<PreKeyRow>): Result<Unit> = runCatching {
        supabase.postgrest["prekeys"].insert(rows)
    }

    /**
     * Fetches one unconsumed one-time PreKey + the identity and signed PreKey
     * for [peerId] so the caller can perform X3DH session establishment.
     */
    suspend fun fetchPreKeyBundle(peerId: String): Result<PeerPreKeyBundleRow> = runCatching {
        val identity = supabase.postgrest["prekeys"]
            .select { filter { eq("user_id", peerId); eq("key_type", "identity") } }
            .decodeSingle<PreKeyRow>()

        val signed = supabase.postgrest["prekeys"]
            .select { filter { eq("user_id", peerId); eq("key_type", "signed") } }
            .decodeSingle<PreKeyRow>()

        val onetime = supabase.postgrest["prekeys"]
            .select {
                filter { eq("user_id", peerId); eq("key_type", "onetime"); eq("consumed", false) }
                limit(1)
            }
            .decodeSingleOrNull<PreKeyRow>()

        // Mark the one-time key as consumed.
        onetime?.let {
            supabase.postgrest["prekeys"]
                .update({ set("consumed", true) }) {
                    filter { eq("user_id", peerId); eq("key_id", it.keyId); eq("key_type", "onetime") }
                }
        }

        PeerPreKeyBundleRow(
            peerId       = peerId,
            identityKey  = identity,
            signedPreKey = signed,
            oneTimePreKey = onetime,
        )
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    suspend fun searchUsers(query: String): Result<List<UserRow>> = runCatching {
        supabase.postgrest["users"]
            .select { filter { ilike("username", "%$query%") }; limit(20) }
            .decodeList<UserRow>()
    }

    suspend fun getProfile(userId: String): Result<UserRow> = runCatching {
        supabase.postgrest["users"]
            .select { filter { eq("id", userId) } }
            .decodeSingle<UserRow>()
    }

    // ── Conversations ─────────────────────────────────────────────────────────

    suspend fun fetchConversations(userId: String): Result<List<ConversationRow>> = runCatching {
        // Two flat queries instead of a Postgrest relational embed — avoids any risk
        // of an embedded JSON object breaking ConversationRow's decoder, and avoids
        // needing a custom Postgres RPC function.
        val conversationIds = supabase.postgrest["conversation_participants"]
            .select(columns = Columns.list("conversation_id")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<ParticipantRow>()
            .map { it.conversationId }

        if (conversationIds.isEmpty()) return@runCatching emptyList()

        supabase.postgrest["conversations"]
            .select { filter { isIn("id", conversationIds) } }
            .decodeList<ConversationRow>()
    }

    /**
     * Finds an existing 1:1 (non-group) conversation between [userIdA] and [userIdB],
     * if one already exists — used to avoid creating duplicate conversations when
     * starting a chat with someone you've already messaged.
     */
    suspend fun findExistingConversation(userIdA: String, userIdB: String): Result<ConversationRow?> = runCatching {
        val conversationIdsA = supabase.postgrest["conversation_participants"]
            .select(columns = Columns.list("conversation_id")) { filter { eq("user_id", userIdA) } }
            .decodeList<ParticipantRow>()
            .map { it.conversationId }
            .toSet()

        if (conversationIdsA.isEmpty()) return@runCatching null

        val conversationIdsB = supabase.postgrest["conversation_participants"]
            .select(columns = Columns.list("conversation_id")) { filter { eq("user_id", userIdB) } }
            .decodeList<ParticipantRow>()
            .map { it.conversationId }
            .toSet()

        val sharedIds = conversationIdsA.intersect(conversationIdsB)
        if (sharedIds.isEmpty()) return@runCatching null

        supabase.postgrest["conversations"]
            .select { filter { isIn("id", sharedIds.toList()); eq("is_group", false) } }
            .decodeList<ConversationRow>()
            .firstOrNull()
    }

    /**
     * For a 1:1 (non-group) conversation, returns the OTHER participant (not [myUserId]).
     * `conversations.title` is a single shared column set by whoever created the row — each
     * side should see the OTHER person's name, not a fixed shared string, so the display
     * title must be resolved per-viewer using this instead of reading `title` directly.
     */
    suspend fun getOtherParticipant(conversationId: String, myUserId: String): Result<UserRow?> = runCatching {
        val participantIds = supabase.postgrest["conversation_participants"]
            .select(columns = Columns.list("user_id")) {
                filter { eq("conversation_id", conversationId) }
            }
            .decodeList<ParticipantUserIdRow>()
            .map { it.userId }

        val otherId = participantIds.firstOrNull { it != myUserId } ?: return@runCatching null

        supabase.postgrest["users"]
            .select { filter { eq("id", otherId) } }
            .decodeSingle<UserRow>()
    }

    suspend fun createConversation(
        title: String,
        participantIds: List<String>,
        isGroup: Boolean = false,
    ): Result<ConversationRow> = runCatching {
        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.d("SCDebug", "createConversation() called")
        android.util.Log.d("SCDebug", "  currentUserId() = ${supabase.auth.currentUserOrNull()?.id}")
        android.util.Log.d("SCDebug", "  hasActiveSession = ${supabase.auth.currentSessionOrNull() != null}")
        android.util.Log.d("SCDebug", "  session.accessToken (first 20 chars) = ${supabase.auth.currentSessionOrNull()?.accessToken?.take(20)}")
        android.util.Log.d("SCDebug", "  session.expiresAt = ${supabase.auth.currentSessionOrNull()?.expiresAt}")
        android.util.Log.d("SCDebug", "  participantIds = $participantIds")
        android.util.Log.d("SCDebug", "  supabase client identity hash = ${System.identityHashCode(supabase)}")
        // ── END DEBUG ──────────────────────────────────────────────────────

        val conv = supabase.postgrest["conversations"]
            .insert(ConversationInsert(title = title, isGroup = isGroup)) { select() }
            .decodeSingle<ConversationRow>()

        // Insert participants.
        val participants = participantIds.map {
            mapOf("conversation_id" to conv.id, "user_id" to it)
        }
        supabase.postgrest["conversation_participants"].insert(participants)
        conv
    }.onFailure { error ->
        // ── DEBUG ──────────────────────────────────────────────────────────
        android.util.Log.e("SCDebug", "createConversation() FAILED: ${error::class.qualifiedName}", error)
        // ── END DEBUG ──────────────────────────────────────────────────────
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    /**
     * Inserts an encrypted message row. The server stores ONLY ciphertext.
     * Supabase Realtime will broadcast this insert to all subscribers of the conversation channel.
     */
    suspend fun insertMessage(row: MessageRow): Result<MessageRow> = runCatching {
        supabase.postgrest["messages"]
            .insert(row) { select() }
            .decodeSingle<MessageRow>()
    }

    /** Fetches the last [limit] messages for pagination / initial load. */
    suspend fun fetchMessages(
        conversationId: String,
        limit: Int = 30,
        beforeTimestamp: String? = null,
    ): Result<List<MessageRow>> = runCatching {
        supabase.postgrest["messages"].select {
            filter {
                eq("conversation_id", conversationId)
                beforeTimestamp?.let { lt("timestamp", it) }
            }
            order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<MessageRow>()
    }

    // ── OneSignal Token ───────────────────────────────────────────────────────

    /**
     * Upserts the user's OneSignal Player/Subscription ID so the server can
     * target them with data-only push notifications when a new message arrives.
     */
    suspend fun upsertOneSignalId(userId: String, oneSignalId: String): Result<Unit> = runCatching {
        supabase.postgrest["device_tokens"].upsert(
            mapOf("user_id" to userId, "onesignal_id" to oneSignalId),
        )
    }

    // ── Media (Encrypted Upload / Download) ───────────────────────────────────

    /**
     * Uploads an encrypted media file to Supabase Storage.
     * Returns a [Flow] of [UploadProgress] for real-time progress UI.
     *
     * The [encryptedFile] contains ciphertext — the storage bucket never
     * holds decryptable content.
     */
    fun uploadEncryptedMedia(
        conversationId: String,
        messageId: String,
        encryptedFile: File,
    ): Flow<UploadProgress> = flow {
        emit(UploadProgress.Started)
        val path = "$conversationId/$messageId/${encryptedFile.name}"
        val bucket = supabase.storage[MEDIA_BUCKET]

        bucket.upload(path, encryptedFile.readBytes(), upsert = false)
        val publicUrl = bucket.publicUrl(path)
        emit(UploadProgress.Done(publicUrl))
    }

    /** Downloads the encrypted blob from Supabase Storage. */
    suspend fun downloadEncryptedMedia(storagePath: String): Result<ByteArray> = runCatching {
        supabase.storage[MEDIA_BUCKET].downloadPublic(storagePath)
    }
}

// ── Row / DTO types ───────────────────────────────────────────────────────────

@Serializable
data class UserRow(
    val id: String = "",
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("identity_public_key") val identityPublicKey: String = "",
)

@Serializable
data class PreKeyRow(
    @SerialName("user_id") val userId: String = "",
    @SerialName("key_id") val keyId: Int,
    @SerialName("public_key") val publicKey: String,
    @SerialName("key_type") val keyType: String,           // "identity" | "signed" | "onetime"
    val signature: String? = null,
    val consumed: Boolean = false,
)

@Serializable
data class PeerPreKeyBundleRow(
    val peerId: String,
    val identityKey: PreKeyRow,
    val signedPreKey: PreKeyRow,
    val oneTimePreKey: PreKeyRow?,
)

@Serializable
data class ConversationRow(
    val id: String = "",
    val title: String,
    @SerialName("is_group") val isGroup: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("created_by") val createdBy: String? = null,
)

@Serializable
data class ParticipantRow(@SerialName("conversation_id") val conversationId: String)

@Serializable
data class ParticipantUserIdRow(@SerialName("user_id") val userId: String)

@Serializable
data class ConversationInsert(
    val title: String,
    @SerialName("is_group") val isGroup: Boolean = false,
)

@Serializable
data class MessageRow(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    /** Base64-encoded AES-GCM ciphertext. */
    @SerialName("encrypted_body") val encryptedBody: String,
    /** Base64-encoded GCM IV. */
    val iv: String,
    @SerialName("message_index") val messageIndex: Int = 0,
    @SerialName("message_type") val messageType: String = "TEXT",
    @SerialName("media_url") val mediaUrl: String? = null,
    val timestamp: String = "",
)

sealed interface UploadProgress {
    data object Started                    : UploadProgress
    data class Done(val publicUrl: String) : UploadProgress
}