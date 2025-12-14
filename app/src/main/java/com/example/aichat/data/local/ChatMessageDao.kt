package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat messages
 */
@Dao
interface ChatMessageDao {
    
    /**
     * Get all messages ordered by timestamp (oldest first)
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>
    
    /**
     * Get the last N messages ordered by timestamp (oldest first)
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getLastMessages(limit: Int): List<ChatMessageEntity>
    
    /**
     * Insert a new message
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)
    
    /**
     * Insert multiple messages
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
    
    /**
     * Delete all messages
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
    
    /**
     * Delete messages older than the last N messages
     * Keeps only the most recent N messages
     * This deletes messages where timestamp is less than the timestamp of the Nth newest message
     * If there are fewer than keepCount messages, nothing is deleted (subquery returns NULL)
     */
    @Query("""
        DELETE FROM chat_messages 
        WHERE timestamp < COALESCE((
            SELECT timestamp FROM chat_messages 
            ORDER BY timestamp DESC 
            LIMIT 1 OFFSET :keepCount - 1
        ), 0)
    """)
    suspend fun deleteOldMessages(keepCount: Int)
    
    /**
     * Get the count of messages
     */
    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int
    
    /**
     * Get messages that are not summaries (original messages only)
     */
    @Query("SELECT * FROM chat_messages WHERE isSummary = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getNonSummaryMessages(limit: Int): List<ChatMessageEntity>
    
    /**
     * Get messages by their IDs
     */
    @Query("SELECT * FROM chat_messages WHERE id IN (:ids) ORDER BY timestamp ASC")
    suspend fun getMessagesByIds(ids: List<String>): List<ChatMessageEntity>
    
    /**
     * Delete messages by their IDs
     */
    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<String>)
    
    /**
     * Get messages by session ID
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionId(sessionId: String): List<ChatMessageEntity>
    
    /**
     * Get message count for a session
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCountBySessionId(sessionId: String): Int
    
    /**
     * Delete all messages for a session
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: String)
    
    /**
     * Get non-summary messages by session ID (messages that are not summaries themselves)
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isSummary = 0 ORDER BY timestamp ASC")
    suspend fun getNonSummaryMessagesBySessionId(sessionId: String): List<ChatMessageEntity>
    
    /**
     * Get non-summary messages by session ID that were created after a specific timestamp
     * Used to find messages that haven't been summarized yet
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isSummary = 0 AND timestamp > :afterTimestamp ORDER BY timestamp ASC")
    suspend fun getNonSummaryMessagesAfterTimestamp(sessionId: String, afterTimestamp: Long): List<ChatMessageEntity>
}
