package com.example.aichat.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing chat sessions
 */
class ChatSessionRepository(
    private val sessionDao: ChatSessionDao
) {
    /**
     * Get all sessions as a Flow
     */
    fun getAllSessions(): Flow<List<ChatSessionEntity>> {
        return sessionDao.getAllSessions()
    }
    
    /**
     * Get a session by ID
     */
    suspend fun getSessionById(sessionId: String): ChatSessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }
    
    /**
     * Create a new session
     */
    suspend fun createSession(
        id: String,
        title: String,
        summary: String? = null
    ): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            summary = summary,
            messageCount = 0,
            lastMessagePreview = null
        )
        sessionDao.insertSession(session)
        return session
    }
    
    /**
     * Update session
     */
    suspend fun updateSession(session: ChatSessionEntity) {
        sessionDao.updateSession(session)
    }
    
    /**
     * Update session's summary
     */
    suspend fun updateSummary(sessionId: String, summary: String?) {
        sessionDao.updateSummary(sessionId, summary)
    }
    
    /**
     * Update session's summary and last summarized timestamp
     */
    suspend fun updateSummaryAndTimestamp(sessionId: String, summary: String?, timestamp: Long) {
        sessionDao.updateSummaryAndTimestamp(sessionId, summary, timestamp)
    }
    
    /**
     * Update session's message count
     */
    suspend fun updateMessageCount(sessionId: String, count: Int) {
        sessionDao.updateMessageCount(sessionId, count)
    }
    
    /**
     * Update session's last message preview and timestamp
     */
    suspend fun updateLastMessage(sessionId: String, preview: String?) {
        sessionDao.updateLastMessagePreview(sessionId, preview, System.currentTimeMillis())
    }
    
    /**
     * Delete a session
     */
    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }
    
    /**
     * Delete all sessions
     */
    suspend fun deleteAllSessions() {
        sessionDao.deleteAllSessions()
    }
}

