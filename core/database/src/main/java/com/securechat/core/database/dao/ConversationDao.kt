package com.securechat.core.database.dao

import androidx.room.*
import com.securechat.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Live list of all conversations ordered by most recent message first. */
    @Query("SELECT * FROM conversations ORDER BY last_message_timestamp DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getById(conversationId: String): ConversationEntity?

    /** Total unread count across all conversations (for the app badge). */
    @Query("SELECT SUM(unread_count) FROM conversations")
    fun observeTotalUnread(): Flow<Int>

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    /**
     * Atomically updates the last message preview and resets the unread counter.
     * Called whenever a new message is inserted.
     */
    @Query("""
        UPDATE conversations
        SET last_message_body = :body,
            last_message_timestamp = :timestamp,
            unread_count = unread_count + :delta
        WHERE id = :conversationId
    """)
    suspend fun updateLastMessage(
        conversationId: String,
        body: String,
        timestamp: Long,
        delta: Int,
    )

    /** Mark all messages in a conversation as read (set unread_count = 0). */
    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun clearUnread(conversationId: String)

    /** Update presence status string for a peer conversation. */
    @Query("UPDATE conversations SET presence_status = :status WHERE id = :conversationId")
    suspend fun updatePresence(conversationId: String, status: String)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: String)
}
