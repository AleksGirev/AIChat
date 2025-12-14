package com.example.aichat.data.local

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository for managing external memory
 */
class ExternalMemoryRepository(
    private val memoryDao: ExternalMemoryDao
) {
    /**
     * Save memory entry
     */
    suspend fun saveMemory(
        key: String,
        content: String,
        category: String,
        sessionId: String? = null,
        relatedMessageIds: List<String> = emptyList()
    ) {
        val existing = memoryDao.getByKey(key)
        val memory = if (existing != null) {
            existing.copy(
                content = content,
                category = category,
                lastAccessed = System.currentTimeMillis(),
                sessionId = sessionId,
                relatedMessageIds = relatedMessageIds
            )
        } else {
            ExternalMemoryEntity(
                id = UUID.randomUUID().toString(),
                key = key,
                content = content,
                category = category,
                timestamp = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis(),
                accessCount = 1,
                sessionId = sessionId,
                relatedMessageIds = relatedMessageIds
            )
        }
        memoryDao.insertMemory(memory)
    }
    
    /**
     * Get memory by key
     */
    suspend fun getMemory(key: String): String? {
        val memory = memoryDao.getByKey(key)
        memory?.let {
            memoryDao.updateAccess(it.id, System.currentTimeMillis())
        }
        return memory?.content
    }
    
    /**
     * Get all memory entries by category
     */
    suspend fun getByCategory(category: String): List<String> {
        return memoryDao.getByCategory(category).map { it.content }
    }
    
    /**
     * Get all memory entries by session ID
     */
    suspend fun getBySessionId(sessionId: String): List<ExternalMemoryEntity> {
        return memoryDao.getBySessionId(sessionId)
    }
    
    /**
     * Get all memory as Flow
     */
    fun getAllMemory(): Flow<List<ExternalMemoryEntity>> {
        return memoryDao.getAllMemory()
    }
    
    /**
     * Delete memory by key
     */
    suspend fun deleteMemory(key: String) {
        val memory = memoryDao.getByKey(key)
        memory?.let {
            memoryDao.deleteMemory(it.id)
        }
    }
    
    /**
     * Delete memory by session ID
     */
    suspend fun deleteBySessionId(sessionId: String) {
        memoryDao.deleteBySessionId(sessionId)
    }
    
    /**
     * Delete all memory
     */
    suspend fun deleteAllMemory() {
        memoryDao.deleteAllMemory()
    }
}

