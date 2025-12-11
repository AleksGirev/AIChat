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
}
