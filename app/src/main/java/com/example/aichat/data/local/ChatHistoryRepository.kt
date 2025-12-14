package com.example.aichat.data.local

import com.example.aichat.ui.model.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing chat history in local database
 */
class ChatHistoryRepository(
    private val chatMessageDao: ChatMessageDao
) {
    
    companion object {
        const val MAX_MESSAGES = 100
    }
    
    /**
     * Get all messages as a Flow
     */
    fun getAllMessages(): Flow<List<UiMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { it.toUiMessage() }
        }
    }
    
    /**
     * Get the last N messages
     */
    suspend fun getLastMessages(limit: Int = MAX_MESSAGES): List<UiMessage> {
        return chatMessageDao.getLastMessages(limit).map { it.toUiMessage() }
    }
    
    /**
     * Get messages by session ID
     */
    suspend fun getMessagesBySessionId(sessionId: String): List<UiMessage> {
        return chatMessageDao.getMessagesBySessionId(sessionId).map { it.toUiMessage() }
    }
    
    /**
     * Get message count for a session
     */
    suspend fun getMessageCountBySessionId(sessionId: String): Int {
        return chatMessageDao.getMessageCountBySessionId(sessionId)
    }
    
    /**
     * Delete messages by session ID
     */
    suspend fun deleteMessagesBySessionId(sessionId: String) {
        chatMessageDao.deleteMessagesBySessionId(sessionId)
    }
    
    /**
     * Get non-summary messages by session ID
     */
    suspend fun getNonSummaryMessagesBySessionId(sessionId: String): List<UiMessage> {
        return chatMessageDao.getNonSummaryMessagesBySessionId(sessionId).map { it.toUiMessage() }
    }
    
    /**
     * Get non-summary messages by session ID that were created after a specific timestamp
     * Used to find messages that haven't been summarized yet
     */
    suspend fun getNonSummaryMessagesAfterTimestamp(sessionId: String, afterTimestamp: Long): List<UiMessage> {
        return chatMessageDao.getNonSummaryMessagesAfterTimestamp(sessionId, afterTimestamp).map { it.toUiMessage() }
    }
    
    /**
     * Save a message to the database
     */
    suspend fun saveMessage(message: UiMessage) {
        val entity = message.toEntity()
        chatMessageDao.insertMessage(entity)
        // Ensure we don't exceed MAX_MESSAGES
        val messageCount = chatMessageDao.getMessageCount()
        if (messageCount > MAX_MESSAGES) {
            chatMessageDao.deleteOldMessages(MAX_MESSAGES)
        }
    }
    
    /**
     * Save multiple messages to the database
     */
    suspend fun saveMessages(messages: List<UiMessage>) {
        if (messages.isEmpty()) return
        val entities = messages.map { it.toEntity() }
        chatMessageDao.insertMessages(entities)
        // Note: We don't limit messages when importing from JSON
        // The limit is only applied when saving individual messages
    }
    
    /**
     * Clear all chat history
     */
    suspend fun clearHistory() {
        chatMessageDao.deleteAllMessages()
    }
    
    /**
     * Get messages that are not summaries (original messages)
     */
    suspend fun getNonSummaryMessages(limit: Int = MAX_MESSAGES): List<UiMessage> {
        return chatMessageDao.getNonSummaryMessages(limit).map { it.toUiMessage() }
    }
    
    /**
     * Get messages by their IDs
     */
    suspend fun getMessagesByIds(ids: List<String>): List<UiMessage> {
        return chatMessageDao.getMessagesByIds(ids).map { it.toUiMessage() }
    }
    
    /**
     * Delete messages by their IDs
     */
    suspend fun deleteMessagesByIds(ids: List<String>) {
        chatMessageDao.deleteMessagesByIds(ids)
    }
    
    /**
     * Convert ChatMessageEntity to UiMessage
     */
    private fun ChatMessageEntity.toUiMessage(): UiMessage {
        return UiMessage(
            id = this.id,
            content = this.content,
            isUser = this.isUser,
            timestamp = this.timestamp,
            sessionId = this.sessionId,
            requestTokens = this.requestTokens,
            responseTokens = this.responseTokens,
            totalTokens = this.totalTokens,
            isSummary = this.isSummary,
            compressedMessageIds = this.compressedMessageIds
        )
    }
    
    /**
     * Convert UiMessage to ChatMessageEntity
     */
    private fun UiMessage.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = this.id,
            content = this.content,
            isUser = this.isUser,
            timestamp = this.timestamp,
            sessionId = this.sessionId,
            requestTokens = this.requestTokens,
            responseTokens = this.responseTokens,
            totalTokens = this.totalTokens,
            isSummary = this.isSummary,
            compressedMessageIds = this.compressedMessageIds
        )
    }
}
