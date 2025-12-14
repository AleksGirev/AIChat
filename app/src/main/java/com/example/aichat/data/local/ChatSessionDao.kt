package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat sessions
 */
@Dao
interface ChatSessionDao {
    
    /**
     * Get all sessions ordered by last update (newest first)
     */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>
    
    /**
     * Get a session by ID
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?
    
    /**
     * Insert a new session
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)
    
    /**
     * Update a session
     */
    @Update
    suspend fun updateSession(session: ChatSessionEntity)
    
    /**
     * Delete a session
     */
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    /**
     * Delete all sessions
     */
    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()
    
    /**
     * Update session's updatedAt timestamp
     */
    @Query("UPDATE chat_sessions SET updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateTimestamp(sessionId: String, timestamp: Long)
    
    /**
     * Update session's summary
     */
    @Query("UPDATE chat_sessions SET summary = :summary WHERE id = :sessionId")
    suspend fun updateSummary(sessionId: String, summary: String?)
    
    /**
     * Update session's message count
     */
    @Query("UPDATE chat_sessions SET messageCount = :count WHERE id = :sessionId")
    suspend fun updateMessageCount(sessionId: String, count: Int)
    
    /**
     * Update session's last message preview
     */
    @Query("UPDATE chat_sessions SET lastMessagePreview = :preview, updatedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateLastMessagePreview(sessionId: String, preview: String?, timestamp: Long)
    
    /**
     * Update session's summary and last summarized timestamp
     */
    @Query("UPDATE chat_sessions SET summary = :summary, lastSummarizedTimestamp = :timestamp WHERE id = :sessionId")
    suspend fun updateSummaryAndTimestamp(sessionId: String, summary: String?, timestamp: Long)
}

