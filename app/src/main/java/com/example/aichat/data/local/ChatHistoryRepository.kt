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
        val entities = messages.map { it.toEntity() }
        chatMessageDao.insertMessages(entities)
        // Ensure we don't exceed MAX_MESSAGES
        val messageCount = chatMessageDao.getMessageCount()
        if (messageCount > MAX_MESSAGES) {
            chatMessageDao.deleteOldMessages(MAX_MESSAGES)
        }
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
            requestTokens = this.requestTokens,
            responseTokens = this.responseTokens,
            totalTokens = this.totalTokens,
            isSummary = this.isSummary,
            compressedMessageIds = this.compressedMessageIds
        )
    }
}
