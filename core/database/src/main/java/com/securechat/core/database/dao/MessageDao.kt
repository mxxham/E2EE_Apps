package com.securechat.core.database.dao

import androidx.room.*
import com.securechat.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns a live Flow of all messages in [conversationId],
     * ordered oldest → newest, so LazyColumn can scroll naturally.
     */
    @Query("""
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
        ORDER BY timestamp ASC
    """)
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    /**
     * Paged query: loads [pageSize] messages before [beforeTimestamp].
     * Used for infinite scroll / "load more" pagination.
     */
    @Query("""
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
          AND timestamp < :beforeTimestamp
        ORDER BY timestamp DESC
        LIMIT :pageSize
    """)
    suspend fun getMessagesBefore(
        conversationId: String,
        beforeTimestamp: Long,
        pageSize: Int = 30,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND is_mine = 0 AND delivery_status != 'READ'")
    fun observeUnreadCount(conversationId: String): Flow<Int>

    // ── Writes ────────────────────────────────────────────────────────────────

    /** Insert or fully replace a message (used when receiving from server). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    /** Update only the delivery status column — avoids re-writing large blobs. */
    @Query("UPDATE messages SET delivery_status = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    /** Soft-delete: mark a message as DELETED and clear its body/media. */
    @Query("""
        UPDATE messages
        SET type = 'DELETED', body = '', media_url = NULL, local_media_path = NULL
        WHERE id = :messageId
    """)
    suspend fun softDelete(messageId: String)

    /** Update the reactions JSON column for a message. */
    @Query("UPDATE messages SET reactions_json = :reactionsJson WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: String)

    /** Update the local media path after decryption is complete. */
    @Query("UPDATE messages SET local_media_path = :localPath WHERE id = :messageId")
    suspend fun updateLocalMediaPath(messageId: String, localPath: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)
}
