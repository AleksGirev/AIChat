package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for external memory
 */
@Dao
interface ExternalMemoryDao {
    
    /**
     * Get all memory entries ordered by last access (most recent first)
     */
    @Query("SELECT * FROM external_memory ORDER BY lastAccessed DESC")
    fun getAllMemory(): Flow<List<ExternalMemoryEntity>>
    
    /**
     * Get memory entry by key
     */
    @Query("SELECT * FROM external_memory WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): ExternalMemoryEntity?
    
    /**
     * Get memory entries by category
     */
    @Query("SELECT * FROM external_memory WHERE category = :category ORDER BY lastAccessed DESC")
    suspend fun getByCategory(category: String): List<ExternalMemoryEntity>
    
    /**
     * Get memory entries by session ID
     */
    @Query("SELECT * FROM external_memory WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getBySessionId(sessionId: String): List<ExternalMemoryEntity>
    
    /**
     * Insert a memory entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: ExternalMemoryEntity)
    
    /**
     * Insert multiple memory entries
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<ExternalMemoryEntity>)
    
    /**
     * Update a memory entry
     */
    @Update
    suspend fun updateMemory(memory: ExternalMemoryEntity)
    
    /**
     * Delete a memory entry by ID
     */
    @Query("DELETE FROM external_memory WHERE id = :id")
    suspend fun deleteMemory(id: String)
    
    /**
     * Delete memory entries by session ID
     */
    @Query("DELETE FROM external_memory WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
    
    /**
     * Delete all memory entries
     */
    @Query("DELETE FROM external_memory")
    suspend fun deleteAllMemory()
    
    /**
     * Update access time and increment access count
     */
    @Query("UPDATE external_memory SET lastAccessed = :timestamp, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun updateAccess(id: String, timestamp: Long)
}

